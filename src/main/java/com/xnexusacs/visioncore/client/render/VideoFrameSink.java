package com.xnexusacs.visioncore.client.render;

import com.xnexusacs.visioncore.common.frame.FrameBuffer;
import com.xnexusacs.visioncore.common.frame.FrameSink;
import java.nio.ByteBuffer;

public final class VideoFrameSink implements FrameSink {

    private final Object lock = new Object();
    private ByteBuffer pending;
    private int pendingWidth;
    private int pendingHeight;
    private boolean hasPending;

    @Override
    public void onFrame(FrameBuffer frame) {
        try (frame) {
            int width = frame.width();
            int height = frame.height();
            int required = width * height * frame.format().bytesPerPixel();

            synchronized (lock) {
                if (pending == null || pending.capacity() < required) {
                    pending = ByteBuffer.allocateDirect(required);
                }

                pending.clear();
                ByteBuffer source = frame.data().duplicate();
                source.rewind();
                source.limit(required);
                pending.put(source);
                pending.flip();
                pendingWidth = width;
                pendingHeight = height;
                hasPending = true;
            }
        }
    }

    public boolean consumePending(FrameConsumer consumer) {
        synchronized (lock) {
            if (!hasPending) {
                return false;
            }

            consumer.accept(pending, pendingWidth, pendingHeight);
            hasPending = false;
            return true;
        }
    }

    @FunctionalInterface
    public interface FrameConsumer {
        void accept(ByteBuffer data, int width, int height);
    }
}
