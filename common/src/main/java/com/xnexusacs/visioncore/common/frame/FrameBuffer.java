package com.xnexusacs.visioncore.common.frame;

import java.nio.ByteBuffer;

public final class FrameBuffer implements AutoCloseable {

    private final ByteBuffer data;
    private final int width;
    private final int height;
    private final BufferFormat format;
    private final long timestampMicros;
    private final FrameBufferPool owner;
    private volatile boolean closed = false;

    FrameBuffer(ByteBuffer data, int width, int height, BufferFormat format, long timestampMicros, FrameBufferPool owner) {
        this.data = data;
        this.width = width;
        this.height = height;
        this.format = format;
        this.timestampMicros = timestampMicros;
        this.owner = owner;
    }

    public ByteBuffer data() {
        return data;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public BufferFormat format() {
        return format;
    }

    public long timestampMicros() {
        return timestampMicros;
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
