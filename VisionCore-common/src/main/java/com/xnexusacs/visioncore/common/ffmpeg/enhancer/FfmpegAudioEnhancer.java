package com.xnexusacs.visioncore.common.ffmpeg.enhancer;

import com.xnexusacs.visioncore.common.audio.AudioBuffer;
import com.xnexusacs.visioncore.common.audio.AudioBufferPool;
import com.xnexusacs.visioncore.common.audio.AudioFormat;
import com.xnexusacs.visioncore.common.audio.AudioSampleSink;
import com.xnexusacs.visioncore.common.ffmpeg.FfmpegPipeline;
import com.xnexusacs.visioncore.common.log.MediaLogger;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FfmpegAudioEnhancer implements AudioSampleSink, AutoCloseable {

    private static final int QUEUE_CAPACITY = 8;
    private static final int READ_BUFFER_BYTES = 8192;
    private static final byte[] EMPTY = new byte[0];
    private static final PendingChunk POISON = new PendingChunk(EMPTY);

    private final AudioSampleSink delegate;
    private final AudioBufferPool audioBufferPool;
    private final String executable;
    private final String filterGraph;
    private final MediaLogger logger;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean degraded = new AtomicBoolean(false);
    private volatile Session session;

    public FfmpegAudioEnhancer(AudioSampleSink delegate, AudioBufferPool audioBufferPool, String executable, String filterGraph, MediaLogger logger) {
        this.delegate = delegate;
        this.audioBufferPool = audioBufferPool;
        this.executable = executable;
        this.filterGraph = filterGraph;
        this.logger = logger;
    }

    @Override
    public void onSamples(AudioBuffer buffer) {
        if (closed.get() || degraded.get()) {
            delegate.onSamples(buffer);
            return;
        }

        if (buffer.format().bitsPerSample() != 16) {
            logger.warn("Ffmpeg audio enhancement only supports 16-bit PCM, got {} bits/sample. Disabling enhancement.", buffer.format().bitsPerSample());
            degraded.set(true);
            delegate.onSamples(buffer);
            return;
        }

        Session current = session;

        if (current == null || !current.format.equals(buffer.format())) {
            current = restartSession(buffer.format());
        }

        if (current == null) {
            delegate.onSamples(buffer);
            return;
        }

        byte[] copy = new byte[buffer.data().remaining()];
        buffer.data().duplicate().get(copy);
        current.enqueue(new PendingChunk(copy));
    }

    private synchronized Session restartSession(AudioFormat format) {
        if (session != null) {
            session.stop();
            session = null;
        }

        if (closed.get() || degraded.get()) {
            return null;
        }

        try {
            List<String> args = buildArgs(format);
            FfmpegPipeline pipeline = FfmpegPipeline.start(executable, args, "audio", logger);
            Session started = new Session(format, pipeline);
            started.start();
            session = started;
            return started;
        } catch (IOException e) {
            logger.warn("Couldn't start Ffmpeg for audio enhancement, falling back to unmodified samples", e);
            degraded.set(true);
            return null;
        }
    }

    private void onSessionFailure(Session failed, String action, Exception cause) {
        if (session != failed) {
            return;
        }

        logger.warn("Ffmpeg audio enhancement failed while " + action + ", falling back to unmodified samples", cause);
        degraded.set(true);

        synchronized (this) {
            if (session == failed) {
                session = null;
            }
        }

        failed.stop();
    }

    private List<String> buildArgs(AudioFormat format) {
        List<String> args = new ArrayList<>();
        args.add("-hide_banner");
        args.add("-loglevel");
        args.add("error");
        args.add("-nostats");
        args.add("-f");
        args.add("s16le");
        args.add("-ar");
        args.add(String.valueOf(format.sampleRate()));
        args.add("-ac");
        args.add(String.valueOf(format.channels()));
        args.add("-i");
        args.add("-");
        args.add("-af");
        args.add(filterGraph);
        args.add("-f");
        args.add("s16le");
        args.add("-ar");
        args.add(String.valueOf(format.sampleRate()));
        args.add("-ac");
        args.add(String.valueOf(format.channels()));
        args.add("-");
        return args;
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

    private record PendingChunk(byte[] data) { }

    private final class Session {
        final AudioFormat format;
        final FfmpegPipeline pipeline;
        final BlockingQueue<PendingChunk> pending = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        final Thread writer;
        final Thread reader;
        volatile boolean stopped = false;

        Session(AudioFormat format, FfmpegPipeline pipeline) {
            this.format = format;
            this.pipeline = pipeline;
            this.writer = new Thread(this::writeLoop, "ffmpeg-audio-writer");
            this.reader = new Thread(this::readLoop, "ffmpeg-audio-reader");
            this.writer.setDaemon(true);
            this.reader.setDaemon(true);
        }

        void start() {
            writer.start();
            reader.start();
        }

        void enqueue(PendingChunk chunk) {
            if (!pending.offer(chunk)) {
                pending.poll();
                pending.offer(chunk);
            }
        }

        void writeLoop() {
            try {
                while (!stopped) {
                    PendingChunk next = pending.take();

                    if (next == POISON) {
                        return;
                    }

                    pipeline.stdin().write(next.data());
                    pipeline.stdin().flush();
                }
            } catch (IOException e) {
                if (!stopped) {
                    onSessionFailure(this, "writing samples to Ffmpeg", e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        void readLoop() {
            byte[] buf = new byte[READ_BUFFER_BYTES];
            byte[] carry = EMPTY;
            InputStream in = pipeline.stdout();
            int frameSizeBytes = format.frameSizeBytes();

            try {
                int read;
                while (!stopped && (read = in.read(buf)) != -1) {
                    byte[] combined;

                    if (carry.length > 0) {
                        combined = new byte[carry.length + read];
                        System.arraycopy(carry, 0, combined, 0, carry.length);
                        System.arraycopy(buf, 0, combined, carry.length, read);
                    } else {
                        combined = Arrays.copyOf(buf, read);
                    }

                    int usableBytes = (combined.length / frameSizeBytes) * frameSizeBytes;
                    carry = usableBytes < combined.length ? Arrays.copyOfRange(combined, usableBytes, combined.length) : EMPTY;

                    if (usableBytes <= 0) {
                        continue;
                    }

                    int sampleCount = usableBytes / frameSizeBytes;
                    AudioBuffer enhanced = audioBufferPool.acquire(usableBytes, sampleCount, format, System.nanoTime() / 1000);
                    try {
                        enhanced.data().put(combined, 0, usableBytes);
                        enhanced.data().flip();
                        delegate.onSamples(enhanced);
                    } finally {
                        enhanced.close();
                    }
                }
            } catch (IOException e) {
                if (!stopped) {
                    onSessionFailure(this, "reading samples from Ffmpeg", e);
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
