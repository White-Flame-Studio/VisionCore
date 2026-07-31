package com.xnexusacs.visioncore.common.subtitles;

import com.xnexusacs.visioncore.common.log.MediaLogger;
import com.xnexusacs.visioncore.common.player.MediaPlayerHandle;
import com.xnexusacs.visioncore.common.player.PlaybackState;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SubtitleTrackController implements AutoCloseable {

    private final MediaPlayerHandle handle;
    private final SubtitleTrack track;
    private final SubtitleSink sink;
    private final Duration pollInterval;
    private final MediaLogger logger;
    private final ScheduledExecutorService scheduler;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> task;
    private volatile SubtitleCue activeCue;

    public SubtitleTrackController(MediaPlayerHandle handle, SubtitleTrack track, SubtitleSink sink, Duration pollInterval, MediaLogger logger) {
        this.handle = handle;
        this.track = track;
        this.sink = sink;
        this.pollInterval = pollInterval;
        this.logger = logger;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "visioncore-subtitle-" + handle.id());
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        if (closed.get() || task != null) {
            return;
        }

        long intervalMillis = Math.max(1, pollInterval.toMillis());
        task = scheduler.scheduleAtFixedRate(this::tick, 0, intervalMillis, TimeUnit.MILLISECONDS);
    }

    private void tick() {
        try {
            PlaybackState state = handle.state();

            if (state != PlaybackState.PLAYING && state != PlaybackState.PAUSED) {
                return;
            }

            SubtitleCue cue = track.cueAt(handle.timeMillis());

            if (cue == activeCue) {
                return;
            }

            activeCue = cue;

            if (cue == null) {
                sink.onCueCleared();
            } else {
                sink.onCue(cue);
            }
        } catch (RuntimeException e) {
            logger.warn("Subtitle controller for '" + handle.id() + "' failed while ticking", e);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        scheduler.shutdownNow();
    }
}
