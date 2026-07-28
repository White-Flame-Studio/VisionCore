package com.xnexusacs.visioncore.common.frame;

public enum BufferFormat {

    RGB(3),

    RGBA(4),

    BGR(3),

    BGRA(4);

    private final int bytesPerPixel;

    BufferFormat(int bytesPerPixel) {
        this.bytesPerPixel = bytesPerPixel;
    }

    public int bytesPerPixel() {
        return bytesPerPixel;
    }
}
