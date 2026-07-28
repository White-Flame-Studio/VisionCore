package com.xnexusacs.visioncore.common.audio;

import java.nio.ByteBuffer;

public final class AudioBuffer implements AutoCloseable {

    private final ByteBuffer data;
    private final int sampleCount;
    private final AudioFormat format;
    private final long ptsMicros;
    private final AudioBufferPool owner;
    private volatile boolean closed = false;

    AudioBuffer(ByteBuffer data, int sampleCount, AudioFormat format, long ptsMicros, AudioBufferPool owner) {
        this.data = data;
        this.sampleCount = sampleCount;
        this.format = format;
        this.ptsMicros = ptsMicros;
        this.owner = owner;
    }

    public ByteBuffer data() {
        return data;
    }

    public int sampleCount() {
        return sampleCount;
    }

    public AudioFormat format() {
        return format;
    }

    public long ptsMicros() {
        return ptsMicros;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (owner != null) {
            owner.release(this);
        }
    }
}
