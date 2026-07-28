package com.xnexusacs.visioncore.common.player;

import com.xnexusacs.visioncore.common.log.MediaLogger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class PlayerPool {

    private final Supplier<MediaPlayerHandle> factory;
    private final int maxSize;
    private final MediaLogger logger;

    private final Deque<MediaPlayerHandle> idle = new ArrayDeque<>();
    private final Set<MediaPlayerHandle> borrowed = ConcurrentHashMap.newKeySet();
    private int created = 0;
    private volatile boolean shuttingDown = false;

    public PlayerPool(Supplier<MediaPlayerHandle> factory, int maxSize, MediaLogger logger) {
        this.factory = factory;
        this.maxSize = maxSize;
        this.logger = logger;
    }

    public synchronized MediaPlayerHandle borrow() {
        if (shuttingDown) {
            throw new IllegalStateException("PlayerPool is shutting down, can't borrow more players");
        }

        MediaPlayerHandle handle = idle.poll();

        if (handle == null) {
            if (created >= maxSize) {
                throw new IllegalStateException("PlayerPool is full: " + maxSize + " players at the same time");
            }
            handle = factory.get();
            created++;
        }

        borrowed.add(handle);
        return handle;
    }

    public synchronized void release(MediaPlayerHandle handle) {
        if (!borrowed.remove(handle)) {
            logger.warn("Invalid MediaPlayerHandle submitted (id={})", handle.id());
            return;
        }

        if (shuttingDown) {
            handle.release();
            created--;
            return;
        }

        handle.stop();
        idle.offer(handle);
    }

    public synchronized void shutdownAll() {
        shuttingDown = true;

        if (!borrowed.isEmpty()) {
            logger.warn("Shutting down PlayerPool with {} players not returned", borrowed.size());
        }

        MediaPlayerHandle handle;

        while ((handle = idle.poll()) != null) {
            handle.release();
        }

        created = 0;
    }
}
