package com.xnexusacs.visioncore.common.player.vlc;

import com.xnexusacs.visioncore.common.audio.AudioBuffer;
import com.xnexusacs.visioncore.common.audio.AudioBufferPool;
import com.xnexusacs.visioncore.common.audio.AudioSampleSink;
import com.xnexusacs.visioncore.common.audio.AudioFormat;
import com.xnexusacs.visioncore.common.concurrency.SequencedDispatcher;
import com.xnexusacs.visioncore.common.event.EventBus;
import com.xnexusacs.visioncore.common.event.events.BufferingEvent;
import com.xnexusacs.visioncore.common.event.events.PlaybackEndedEvent;
import com.xnexusacs.visioncore.common.event.events.PlaybackErrorEvent;
import com.xnexusacs.visioncore.common.event.events.PlaybackStateChangedEvent;
import com.xnexusacs.visioncore.common.event.events.PlaybackStats;
import com.xnexusacs.visioncore.common.event.events.PlaybackStatsEvent;
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
import com.sun.jna.Pointer;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.media.MediaStatistics;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.base.callback.AudioCallbackAdapter;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallbackAdapter;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

final class VlcMediaPlayerHandle implements MediaPlayerHandle {

    private static final String AUDIO_FORMAT_STRING = "S16N";
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(48_000, 2, 16);

    private static final String[] DISABLE_LIBVLC_SUBTITLES_OPTIONS = {":no-sub-autodetect-file", ":sub-track=-1"};

    private final String id;
    private final EmbeddedMediaPlayer mediaPlayer;
    private final FrameBufferPool frameBufferPool;
    private final AudioBufferPool audioBufferPool;
    private final MediaLogger logger;
    private final SequencedDispatcher<String> dispatcher;
    private final Set<PlaybackListener> listeners = new CopyOnWriteArraySet<>();
    private final EventBus events;
    private final ScheduledExecutorService statsScheduler;
    private final Duration statsSampleInterval;

    private final AtomicReference<PlaybackState> state = new AtomicReference<>(PlaybackState.IDLE);
    private final AtomicLong frameToken = new AtomicLong();
    private final AtomicBoolean audioCallbackRegistered = new AtomicBoolean(false);
    private final AtomicLong deliveredVideoFrames = new AtomicLong();
    private final AtomicLong deliveredAudioBuffers = new AtomicLong();
    private volatile FrameSink currentVideoSink;
    private volatile AudioSampleSink currentAudioSink;
    private volatile ScheduledFuture<?> statsTask;

    VlcMediaPlayerHandle(String id, MediaPlayerFactory factory, FrameBufferPool frameBufferPool, AudioBufferPool audioBufferPool, MediaLogger logger, Executor dispatchExecutor, EventBus events, ScheduledExecutorService statsScheduler, Duration statsSampleInterval) {
        this.id = id;
        this.frameBufferPool = frameBufferPool;
        this.audioBufferPool = audioBufferPool;
        this.logger = logger;
        this.dispatcher = new SequencedDispatcher<>(dispatchExecutor);
        this.events = events;
        this.statsScheduler = statsScheduler;
        this.statsSampleInterval = statsSampleInterval;
        this.mediaPlayer = factory.mediaPlayers().newEmbeddedMediaPlayer();
        this.mediaPlayer.videoSurface().set(factory.videoSurfaces().newVideoSurface(new FormatCallback(), new FrameRenderCallback(), true));
        this.mediaPlayer.events().addMediaPlayerEventListener(new StateListener());
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public void play(ResolvedMedia media, FrameSink videoSink) {
        this.currentVideoSink = videoSink;
        this.currentAudioSink = null;
        startPlayback(media);
    }

    @Override
    public void play(ResolvedMedia media, AudioSampleSink audioSink) {
        this.currentVideoSink = null;
        this.currentAudioSink = audioSink;
        ensureAudioCallbackRegistered();
        startPlayback(media);
    }

    @Override
    public void play(ResolvedMedia media, FrameSink videoSink, AudioSampleSink audioSink) {
        this.currentVideoSink = videoSink;
        this.currentAudioSink = audioSink;
        ensureAudioCallbackRegistered();
        startPlayback(media);
    }

    private void ensureAudioCallbackRegistered() {
        if (audioCallbackRegistered.compareAndSet(false, true)) {
            mediaPlayer.audio().callback(AUDIO_FORMAT_STRING, AUDIO_FORMAT.sampleRate(), AUDIO_FORMAT.channels(), new AudioSampleCallback());
        }
    }

    private void startPlayback(ResolvedMedia media) {
        transitionTo(PlaybackState.LOADING);
        boolean started = mediaPlayer.media().play(media.playableUri().toString(), DISABLE_LIBVLC_SUBTITLES_OPTIONS);

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
        currentVideoSink = null;
        currentAudioSink = null;
        cancelStatsTask();
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
    public long timeMillis() {
        return mediaPlayer.status().time();
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
        cancelStatsTask();
        dispatcher.shutdown();
        mediaPlayer.release();
    }

    private void transitionTo(PlaybackState newState) {
        PlaybackState previous = state.getAndSet(newState);

        if (previous != newState) {
            logger.info("[{}] state: {} -> {}", id, previous, newState);
            dispatchEvent(() -> notifyStateChanged(previous, newState));

            if (newState == PlaybackState.PLAYING) {
                ensureStatsTaskRunning();
            } else if (newState == PlaybackState.STOPPED || newState == PlaybackState.ERROR) {
                cancelStatsTask();
            }
        }
    }

    private void dispatchEvent(Runnable task) {
        dispatcher.submit("event", task);
    }

    private void notifyStateChanged(PlaybackState previous, PlaybackState current) {
        events.post(new PlaybackStateChangedEvent(id, previous, current));

        for (PlaybackListener listener : listeners) {
            try {
                listener.onStateChanged(previous, current);
            } catch (RuntimeException e) {
                logger.error("A PlaybackListener from '" + id + "' threw an exception on onStateChanged", e);
            }
        }
    }

    private void notifyError(MediaException error) {
        events.post(new PlaybackErrorEvent(id, error));

        for (PlaybackListener listener : listeners) {
            try {
                listener.onError(error);
            } catch (RuntimeException e) {
                logger.error("A PlaybackListener from '" + id + "' threw an exception on onError", e);
            }
        }
    }

    private void notifyEndReached() {
        events.post(new PlaybackEndedEvent(id));

        for (PlaybackListener listener : listeners) {
            try {
                listener.onEndReached();
            } catch (RuntimeException e) {
                logger.error("A PlaybackListener from '" + id + "' threw an exception on onEndReached", e);
            }
        }
    }

    private void ensureStatsTaskRunning() {
        if (statsScheduler == null) {
            return;
        }

        ScheduledFuture<?> existing = statsTask;

        if (existing != null && !existing.isDone()) {
            return;
        }

        long intervalMillis = statsSampleInterval.toMillis();
        statsTask = statsScheduler.scheduleAtFixedRate(this::sampleAndPublishStats, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    private void cancelStatsTask() {
        ScheduledFuture<?> task = statsTask;

        if (task != null) {
            task.cancel(false);
            statsTask = null;
        }
    }

    private void sampleAndPublishStats() {
        try {
            MediaStatistics stats = mediaPlayer.media().info().statistics();

            if (stats == null) {
                return;
            }

            PlaybackStats snapshot = new PlaybackStats(stats.inputBitrate(), stats.inputBytesRead(), stats.demuxBitrate(), stats.demuxBytesRead(), stats.demuxCorrupted(), stats.demuxDiscontinuity(), stats.decodedVideo(), stats.decodedAudio(), stats.picturesDisplayed(), stats.picturesLost(), stats.audioBuffersPlayed(), stats.audioBuffersLost(), deliveredVideoFrames.get(), deliveredAudioBuffers.get());

            events.post(new PlaybackStatsEvent(id, snapshot));
        } catch (RuntimeException e) {
            logger.warn("Couldn't sample playback statistics for '" + id + "'", e);
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
            FrameSink sink = currentVideoSink;

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
                    deliveredVideoFrames.incrementAndGet();
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

    private final class AudioSampleCallback extends AudioCallbackAdapter {
        @Override
        public void play(MediaPlayer mediaPlayer, Pointer samples, int sampleCount, long pts) {
            AudioSampleSink sink = currentAudioSink;

            if (sink == null) {
                return;
            }

            int byteLength = sampleCount * AUDIO_FORMAT.frameSizeBytes();
            AudioBuffer buffer = audioBufferPool.acquire(byteLength, sampleCount, AUDIO_FORMAT, pts);
            ByteBuffer dest = buffer.data();
            ByteBuffer source = samples.getByteBuffer(0, byteLength);
            dest.put(source);
            dest.flip();

            try {
                sink.onSamples(buffer);
                deliveredAudioBuffers.incrementAndGet();
            } finally {
                buffer.close();
            }
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
            dispatchEvent(() -> events.post(new BufferingEvent(id, Math.round(newCache))));

            if (state.get() != PlaybackState.PLAYING) {
                transitionTo(PlaybackState.BUFFERING);
            }
        }
    }
}
