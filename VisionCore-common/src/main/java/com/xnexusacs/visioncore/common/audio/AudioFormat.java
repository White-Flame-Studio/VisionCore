package com.xnexusacs.visioncore.common.audio;

public record AudioFormat(int sampleRate, int channels, int bitsPerSample) {

    public int bytesPerSample() {
        return bitsPerSample / 8;
    }

    public int frameSizeBytes() {
        return bytesPerSample() * channels;
    }
}
