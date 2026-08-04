package com.xnexusacs.visioncore.common.ffmpeg.enhancer;

import com.xnexusacs.visioncore.common.ffmpeg.FfmpegPipeline;
import com.xnexusacs.visioncore.common.frame.BufferFormat;
import com.xnexusacs.visioncore.common.frame.FrameBuffer;
import com.xnexusacs.visioncore.common.frame.FrameBufferPool;
import com.xnexusacs.visioncore.common.frame.FrameSink;
import com.xnexusacs.visioncore.common.log.MediaLogger;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FfmpegVideoEnhancer implements FrameSink, AutoCloseable {

    private static final int QUEUE_CAPACITY = 2;
    private static final int NOMINAL_FRAMERATE = 30;
    private static final PendingFrame POISON = new PendingFrame(new byte[0]);

    private final FrameSink delegate;
    private final FrameBufferPool frameBufferPool;
    private final String executable;
    private final String filterGraph;
    private final MediaLogger logger;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean degraded = new AtomicBoolean(false);
    private volatile Session session;

    public FfmpegVideoEnhancer(FrameSink delegate, FrameBufferPool frameBufferPool, String executable, String filterGraph, MediaLogger logger) {
        this.delegate = delegate;
        this.frameBufferPool = frameBufferPool;
        this.executable = executable;
        this.filterGraph = filterGraph;
        this.logger = logger;
    }

    @Override
    public void onFrame(FrameBuffer frame) {
        if (closed.get() || degraded.get()) {
            delegate.onFrame(frame);
            return;
        }

        Session current = session;

        if (current == null || current.width != frame.width() || current.height != frame.height() || current.format != frame.format()) {
            current = restartSession(frame.width(), frame.height(), frame.format());
        }

        if (current == null) {
            delegate.onFrame(frame);
            return;
        }

        byte[] copy = new byte[frame.data().remaining()];
        frame.data().duplicate().get(copy);
        current.enqueue(new PendingFrame(copy));
    }

    private synchronized Session restartSession(int width, int height, BufferFormat format) {
        if (session != null) {
            session.stop();
            session = null;
        }

        if (closed.get() || degraded.get()) {
            return null;
        }

        try {
            List<String> args = buildArgs(width, height, format);
            FfmpegPipeline pipeline = FfmpegPipeline.start(executable, args, "video", logger);
            Session started = new Session(width, height, format, pipeline);
            started.start();
            session = started;
            return started;
        } catch (IOException e) {
            logger.warn("Couldn't start Ffmpeg for video enhancement, falling back to unmodified frames", e);
            degraded.set(true);
            return null;
        }
    }

    private void onSessionFailure(Session failed, String action, Exception cause) {
        if (session != failed) {
            return;
        }

        logger.warn("Ffmpeg video enhancement failed while " + action + ", falling back to unmodified frames", cause);
        degraded.set(true);

        synchronized (this) {
            if (session == failed) {
                session = null;
            }
        }

        failed.stop();
    }

    private List<String> buildArgs(int width, int height, BufferFormat format) {
        String pixFmt = pixelFormatOf(format);
        List<String> args = new ArrayList<>();
        args.add("-hide_banner");
        args.add("-loglevel");
        args.add("error");
        args.add("-nostats");
        args.add("-f");
        args.add("rawvideo");
        args.add("-pixel_format");
        args.add(pixFmt);
        args.add("-video_size");
        args.add(width + "x" + height);
        args.add("-framerate");
        args.add(String.valueOf(NOMINAL_FRAMERATE));
        args.add("-i");
        args.add("-");
        args.add("-vf");
        args.add(filterGraph);
        args.add("-f");
        args.add("rawvideo");
        args.add("-pixel_format");
        args.add(pixFmt);
        args.add("-");
        return args;
    }

    private static String pixelFormatOf(BufferFormat format) {
        return switch (format) {
            case RGB -> "rgb24";
            case RGBA -> "rgba";
            case BGR -> "bgr24";
            case BGRA -> "bgra";
        };
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        synchronized (this) {
            if (session != null) {
                session.stop();
                session = null;
            }
        }
    }

    private static void readFully(InputStream in, byte[] buf) throws IOException {
        int offset = 0;

        while (offset < buf.length) {
            int read = in.read(buf, offset, buf.length - offset);

            if (read == -1) {
                throw new EOFException("Ffmpeg stdout closed unexpectedly");
            }

            offset += read;
        }
    }

    private record PendingFrame(byte[] data) { }

    private final class Session {
        final int width;
        final int height;
        final BufferFormat format;
        final FfmpegPipeline pipeline;
        final BlockingQueue<PendingFrame> pending = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        final Thread writer;
        final Thread reader;
        volatile boolean stopped = false;

        Session(int width, int height, BufferFormat format, FfmpegPipeline pipeline) {
            this.width = width;
            this.height = height;
            this.format = format;
            this.pipeline = pipeline;
            this.writer = new Thread(this::writeLoop, "ffmpeg-video-writer");
            this.reader = new Thread(this::readLoop, "ffmpeg-video-reader");
            this.writer.setDaemon(true);
            this.reader.setDaemon(true);
        }

        void start() {
            writer.start();
            reader.start();
        }

        void enqueue(PendingFrame frame) {
            if (!pending.offer(frame)) {
                pending.poll();
                pending.offer(frame);
            }
        }

        void writeLoop() {
            try {
                while (!stopped) {
                    PendingFrame next = pending.take();

                    if (next == POISON) {
                        return;
                    }

                    pipeline.stdin().write(next.data());
                    pipeline.stdin().flush();
                }
            } catch (IOException e) {
                if (!stopped) {
                    onSessionFailure(this, "writing frames to Ffmpeg", e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        void readLoop() {
            int frameBytes = width * height * format.bytesPerPixel();
            byte[] buf = new byte[frameBytes];

            try {
                while (!stopped) {
                    readFully(pipeline.stdout(), buf);

                    FrameBuffer enhanced = frameBufferPool.acquire(width, height, format, System.nanoTime() / 1000);
                    try {
                        enhanced.data().put(buf);
                        enhanced.data().flip();
                        delegate.onFrame(enhanced);
                    } finally {
                        enhanced.close();
                    }
                }
            } catch (IOException e) {
                if (!stopped) {
                    onSessionFailure(this, "reading frames from Ffmpeg", e);
                }
            }
        }

        void stop() {
            if (stopped) {
                return;
            }

            stopped = true;
            pending.clear();
            pending.offer(POISON);
            pipeline.close();
            writer.interrupt();
            reader.interrupt();
        }
    }
}
