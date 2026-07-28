package com.xnexusacs.visioncore.common.audio;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class AudioBufferPool {

    private final int maxPooledPerBucket;
    private final Map<Integer, Queue<ByteBuffer>> buckets = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicInteger> bucketSizes = new ConcurrentHashMap<>();

    public AudioBufferPool(int maxPooledPerBucket) {
        this.maxPooledPerBucket = maxPooledPerBucket;
    }

    public AudioBuffer acquire(int byteCapacity, int sampleCount, AudioFormat format, long ptsMicros) {
        Queue<ByteBuffer> bucket = buckets.computeIfAbsent(byteCapacity, c -> new ConcurrentLinkedQueue<>());
        ByteBuffer buffer = bucket.poll();

        if (buffer != null) {
            bucketSizes.get(byteCapacity).decrementAndGet();
            buffer.clear();
        } else {
            buffer = ByteBuffer.allocateDirect(byteCapacity);
        }

        return new AudioBuffer(buffer, sampleCount, format, ptsMicros, this);
    }

    void release(AudioBuffer buffer) {
        int capacity = buffer.data().capacity();
        AtomicInteger size = bucketSizes.computeIfAbsent(capacity, c -> new AtomicInteger());

        if (size.get() >= maxPooledPerBucket) {
            return;
        }

        size.incrementAndGet();
        buckets.computeIfAbsent(capacity, c -> new ConcurrentLinkedQueue<>()).offer(buffer.data());
    }
}
