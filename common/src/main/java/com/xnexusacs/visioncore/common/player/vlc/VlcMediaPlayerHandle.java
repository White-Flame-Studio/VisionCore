package com.xnexusacs.visioncore.common.player.vlc;

import com.xnexusacs.visioncore.common.concurrency.SequencedDispatcher;
import com.xnexusacs.visioncore.common.exception.MediaException;
import com.xnexusacs.visioncore.common.frame.BufferFormat;
import com.xnexusacs.visioncore.common.frame.FrameBuffer;
import com.xnexusacs.visioncore.common.frame.FrameBufferPool;
import com.xnexusacs.visioncore.common.frame.FrameSink;
import com.xnexusacs.visioncore.common.log.MediaLogger;
import com.xnexusacs.visioncore.common.player.MediaPlayerHandle;
import com.xnexusacs.visioncore.common.player.PlaybackListener;
import com.xnexusacs.visioncore.common.player.PlaybackState;
import com.xnexusacs.visioncore.common.source.ResolvedMedia;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallbackAdapter;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

final class VlcMediaPlayerHandle implements MediaPlayerHandle {

    private final String id;
    private final EmbeddedMediaPlayer mediaPlayer;
    private final FrameBufferPool frameBufferPool;
    private final MediaLogger logger;
    private final SequencedDispatcher<String> dispatcher;
    private final Set<PlaybackListener> listeners = new CopyOnWriteArraySet<>();

    private final AtomicReference<PlaybackState> state = new AtomicReference<>(PlaybackState.IDLE);
    private final AtomicLong frameToken = new AtomicLong();
    private volatile FrameSink currentSink;

    VlcMediaPlayerHandle(String id, MediaPlayerFactory factory, FrameBufferPool frameBufferPool, MediaLogger logger, Executor dispatchExecutor) {
        this.id = id;
        this.frameBufferPool = frameBufferPool;
        this.logger = logger;
        this.dispatcher = new SequencedDispatcher<>(dispatchExecutor);
        this.mediaPlayer = factory.mediaPlayers().newEmbeddedMediaPlayer();
        this.mediaPlayer.videoSurface().set(factory.videoSurfaces().newVideoSurface(new FormatCallback(), new FrameRenderCallback(), true));
        this.mediaPlayer.events().addMediaPlayerEventListener(new StateListener());
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public void play(ResolvedMedia media, FrameSink sink) {
        this.currentSink = sink;
        transitionTo(PlaybackState.LOADING);
        boolean started = mediaPlayer.media().play(media.playableUri().toString());

        if (!started) {
            transitionTo(PlaybackState.ERROR);
            dispatchEvent(() -> notifyError(new MediaException("VLC rejected the play action for: " + media.playableUri())));
        }
    }

    @Override
    public void pause() {
        mediaPlayer.controls().setPause(true);
    }

    @Override
    public void resume() {
        mediaPlayer.controls().setPause(false);
    }

    @Override
    public void stop() {
        currentSink = null;
        mediaPlayer.controls().stop();
    }

    @Override
    public void seek(long millis) {
        mediaPlayer.controls().setTime(millis);
    }

    @Override
    public PlaybackState state() {
        return state.get();
    }

    @Override
    public void addListener(PlaybackListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(PlaybackListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void release() {
        dispatcher.shutdown();
        mediaPlayer.release();
    }

    private void transitionTo(PlaybackState newState) {
        PlaybackState previous = state.getAndSet(newState);

        if (previous != newState) {
            dispatchEvent(() -> notifyStateChanged(previous, newState));
        }
    }

    private void dispatchEvent(Runnable task) {
        dispatcher.submit("event", task);
    }

    private void notifyStateChanged(PlaybackState previous, PlaybackState current) {
        for (PlaybackListener listener : listeners) {
            try {
                listener.onStateChanged(previous, current);
            } catch (RuntimeException e) {
                logger.error("A PlaybackListener from '" + id + "' threw an exception on onStateChanged", e);
            }
        }
    }

    private void notifyError(MediaException error) {
        for (PlaybackListener listener : listeners) {
            try {
                listener.onError(error);
            } catch (RuntimeException e) {
                logger.error("A PlaybackListener from '" + id + "' threw an exception on onError", e);
            }
        }
    }

    private void notifyEndReached() {
        for (PlaybackListener listener : listeners) {
            try {
                listener.onEndReached();
            } catch (RuntimeException e) {
                logger.error("A PlaybackListener from '" + id + "' threw an exception on onEndReached", e);
            }
        }
    }

    private final class FormatCallback extends BufferFormatCallbackAdapter {
        @Override
        public uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat getBufferFormat(int sourceWidth, int sourceHeight) {
            return new RV32BufferFormat(sourceWidth, sourceHeight);
        }
    }

    private final class FrameRenderCallback implements RenderCallback {
        @Override
        public void lock(MediaPlayer mediaPlayer) {
            // Ignore.
        }

        @Override
        public void display(MediaPlayer mediaPlayer, ByteBuffer[] nativeBuffers, uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat bufferFormat, int displayWidth, int displayHeight) {
            FrameSink sink = currentSink;

            if (sink == null) {
                return;
            }

            ByteBuffer source = nativeBuffers[0];
            int width = bufferFormat.getWidth();
            int height = bufferFormat.getHeight();
            long token = frameToken.incrementAndGet();

            FrameBuffer frame = frameBufferPool.acquire(width, height, BufferFormat.BGRA, System.nanoTime() / 1000);
            ByteBuffer dest = frame.data();
            ByteBuffer sourceView = source.duplicate();
            sourceView.limit(Math.min(sourceView.capacity(), dest.capacity()));
            dest.put(sourceView);
            dest.flip();

            dispatcher.submitLatest("frame", token, () -> {
                try {
                    sink.onFrame(frame);
                } finally {
                    frame.close();
                }
            });
        }

        @Override
        public void unlock(MediaPlayer mediaPlayer) {
            // Ignore.
        }
    }

    private final class StateListener extends MediaPlayerEventAdapter {
        @Override
        public void playing(MediaPlayer mediaPlayer) {
            transitionTo(PlaybackState.PLAYING);
        }

        @Override
        public void paused(MediaPlayer mediaPlayer) {
            transitionTo(PlaybackState.PAUSED);
        }

        @Override
        public void stopped(MediaPlayer mediaPlayer) {
            transitionTo(PlaybackState.STOPPED);
        }

        @Override
        public void finished(MediaPlayer mediaPlayer) {
            transitionTo(PlaybackState.STOPPED);
            dispatchEvent(VlcMediaPlayerHandle.this::notifyEndReached);
        }

        @Override
        public void error(MediaPlayer mediaPlayer) {
            transitionTo(PlaybackState.ERROR);
            dispatchEvent(() -> notifyError(new MediaException("VLC reported an exception for: " + id)));
        }

        @Override
        public void buffering(MediaPlayer mediaPlayer, float newCache) {
            if (state.get() != PlaybackState.PLAYING) {
                transitionTo(PlaybackState.BUFFERING);
            }
        }
    }
}
