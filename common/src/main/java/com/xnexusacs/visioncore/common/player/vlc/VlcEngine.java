package com.xnexusacs.visioncore.common.player.vlc;

import com.xnexusacs.visioncore.common.exception.NativeLibraryException;
import com.xnexusacs.visioncore.common.frame.FrameBufferPool;
import com.xnexusacs.visioncore.common.log.MediaLogger;
import com.xnexusacs.visioncore.common.player.MediaPlayerHandle;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class VlcEngine implements AutoCloseable {

    private final MediaLogger logger;
    private final FrameBufferPool frameBufferPool;
    private final Executor dispatchExecutor;
    private final MediaPlayerFactory factory;
    private final AtomicInteger handleIdCounter = new AtomicInteger(1);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public VlcEngine(MediaLogger logger, FrameBufferPool frameBufferPool, Executor dispatchExecutor, String... libVlcArgs) {
        this.logger = logger;
        this.frameBufferPool = frameBufferPool;
        this.dispatchExecutor = dispatchExecutor;
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
        return new VlcMediaPlayerHandle(id, factory, frameBufferPool, logger, dispatchExecutor);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        factory.release();
        logger.info("VlcEngine closed");
    }
}
