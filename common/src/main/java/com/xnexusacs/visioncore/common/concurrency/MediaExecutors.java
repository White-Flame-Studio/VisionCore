package com.xnexusacs.visioncore.common.concurrency;

import com.xnexusacs.visioncore.common.config.MediaCoreConfig;
import com.xnexusacs.visioncore.common.log.MediaLogger;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class MediaExecutors {

    private final ExecutorService io;
    private final ExecutorService dispatch;
    private final ScheduledExecutorService scheduled;
    private final MediaLogger logger;

    public MediaExecutors(MediaCoreConfig config, MediaLogger logger) {
        this.logger = logger;
        this.io = Executors.newFixedThreadPool(config.ioThreadPoolSize(), namedFactory("visioncore-io"));
        this.dispatch = Executors.newFixedThreadPool(config.dispatchThreadPoolSize(), namedFactory("visioncore-dispatch"));
        this.scheduled = Executors.newSingleThreadScheduledExecutor(namedFactory("visioncore-scheduled"));
    }

    public ExecutorService io() {
        return io;
    }

    public ExecutorService dispatch() {
        return dispatch;
    }

    public ScheduledExecutorService scheduled() {
        return scheduled;
    }

    public void shutdown() {
        shutdownOne(io, "io");
        shutdownOne(dispatch, "dispatch");
        shutdownOne(scheduled, "scheduled");
    }

    private void shutdownOne(ExecutorService executor, String name) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("The executor '{}' didn't end in 5 seconds, forcing shutdownNow()", name);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private static ThreadFactory namedFactory(String prefix) {
        return new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, prefix + "-" + counter.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        };
    }
}
