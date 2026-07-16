package com.xnexusacs.visioncore.common.concurrency;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public final class SequencedDispatcher<K> {

    private final Executor executor;
    private final Map<K, Lane> lanes = new ConcurrentHashMap<>();

    public SequencedDispatcher(Executor executor) {
        this.executor = executor;
    }

    public void submit(K key, Runnable task) {
        Lane lane = lanes.computeIfAbsent(key, k -> new Lane());
        boolean shouldSchedule;

        synchronized (lane) {
            lane.queue.offer(task);
            shouldSchedule = !lane.draining;
            lane.draining = true;
        }

        if (shouldSchedule) {
            executor.execute(() -> drain(lane));
        }
    }

    public void submitLatest(K key, long token, Runnable task) {
        Lane lane = lanes.computeIfAbsent(key, k -> new Lane());
        boolean shouldSchedule = false;

        synchronized (lane) {
            if (token <= lane.lastAcceptedToken) {
                return;
            }
            lane.lastAcceptedToken = token;
            lane.queue.clear();
            lane.queue.offer(task);
            if (!lane.draining) {
                lane.draining = true;
                shouldSchedule = true;
            }
        }

        if (shouldSchedule) {
            executor.execute(() -> drain(lane));
        }
    }

    public void forget(K key) {
        lanes.remove(key);
    }

    public void shutdown() {
        lanes.clear();
    }

    private void drain(Lane lane) {
        while (true) {
            Runnable task;
            synchronized (lane) {
                task = lane.queue.poll();
                if (task == null) {
                    lane.draining = false;
                    return;
                }
            }
            try {
                task.run();
            } catch (RuntimeException e) {
                // Ignore
            }
        }
    }

    private static final class Lane {
        final Deque<Runnable> queue = new ArrayDeque<>();
        boolean draining = false;
        long lastAcceptedToken = Long.MIN_VALUE;
    }
}
