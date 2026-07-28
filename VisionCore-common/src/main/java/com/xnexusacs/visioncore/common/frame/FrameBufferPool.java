package com.xnexusacs.visioncore.common.frame;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class FrameBufferPool {

    private final int maxPooledPerBucket;
    private final Map<Integer, Queue<ByteBuffer>> buckets = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicInteger> bucketSizes = new ConcurrentHashMap<>();

    public FrameBufferPool(int maxPooledPerBucket) {
        this.maxPooledPerBucket = maxPooledPerBucket;
    }

    public FrameBuffer acquire(int width, int height, BufferFormat format, long timestampMicros) {
        int capacity = width * height * format.bytesPerPixel();
        Queue<ByteBuffer> bucket = buckets.computeIfAbsent(capacity, c -> new ConcurrentLinkedQueue<>());
        ByteBuffer buffer = bucket.poll();

        if (buffer != null) {
            bucketSizes.get(capacity).decrementAndGet();
            buffer.clear();
        } else {
            buffer = ByteBuffer.allocateDirect(capacity);
        }

        return new FrameBuffer(buffer, width, height, format, timestampMicros, this);
    }

    void release(FrameBuffer frame) {
        int capacity = frame.width() * frame.height() * frame.format().bytesPerPixel();
        AtomicInteger size = bucketSizes.computeIfAbsent(capacity, c -> new AtomicInteger());

        if (size.get() >= maxPooledPerBucket) {
            return;
        }

        size.incrementAndGet();
        buckets.computeIfAbsent(capacity, c -> new ConcurrentLinkedQueue<>()).offer(frame.data());
    }
}
