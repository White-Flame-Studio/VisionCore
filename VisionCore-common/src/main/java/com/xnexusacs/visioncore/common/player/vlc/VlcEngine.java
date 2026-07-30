package com.xnexusacs.visioncore.common.player.vlc;

import com.xnexusacs.visioncore.common.audio.AudioBufferPool;
import com.xnexusacs.visioncore.common.event.EventBus;
import com.xnexusacs.visioncore.common.exception.NativeLibraryException;
import com.xnexusacs.visioncore.common.frame.FrameBufferPool;
import com.xnexusacs.visioncore.common.log.MediaLogger;
import com.xnexusacs.visioncore.common.player.MediaPlayerHandle;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class VlcEngine implements AutoCloseable {

    private final MediaLogger logger;
    private final FrameBufferPool frameBufferPool;
    private final AudioBufferPool audioBufferPool;
    private final EventBus events;
    private final Executor dispatchExecutor;
    private final Duration statsSampleInterval;
    private final ScheduledExecutorService statsScheduler;
    private final MediaPlayerFactory factory;
    private final AtomicInteger handleIdCounter = new AtomicInteger(1);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public VlcEngine(MediaLogger logger, FrameBufferPool frameBufferPool, AudioBufferPool audioBufferPool, EventBus events, Executor dispatchExecutor, Duration statsSampleInterval, String... libVlcArgs) {
        this.logger = logger;
        this.frameBufferPool = frameBufferPool;
        this.audioBufferPool = audioBufferPool;
        this.events = events;
        this.dispatchExecutor = dispatchExecutor;
        this.statsSampleInterval = statsSampleInterval;
        this.statsScheduler = statsSampleInterval != null && !statsSampleInterval.isNegative() && !statsSampleInterval.isZero() ? Executors.newSingleThreadScheduledExecutor(namedFactory()) : null;
        new VlcNativeDiscovery(logger).ensureAvailable();

        try {
            this.factory = new MediaPlayerFactory(libVlcArgs);
        } catch (RuntimeException e) {
            throw new NativeLibraryException("Couldn't start MediaPlayerFactory from VLC", e);
        }

        logger.info("VlcEngine initialized");
    }

    public MediaPlayerHandle newHandle() {
        if (closed.get()) {
            throw new IllegalStateException("This VlcEngine is already closed, can't create more players");
        }

        String id = "vlc-player-" + handleIdCounter.getAndIncrement();
        return new VlcMediaPlayerHandle(id, factory, frameBufferPool, audioBufferPool, logger, dispatchExecutor, events, statsScheduler, statsSampleInterval);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        if (statsScheduler != null) {
            statsScheduler.shutdownNow();
            try {
                statsScheduler.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        factory.release();
        logger.info("VlcEngine closed");
    }

    private static ThreadFactory namedFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "visioncore-vlc-stats");
            thread.setDaemon(true);
            return thread;
        };
    }
}
