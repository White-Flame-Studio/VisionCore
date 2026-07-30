package com.xnexusacs.visioncore.common.event.events;

public record PlaybackStats(float inputBitrate, long inputBytesRead, float demuxBitrate, long demuxBytesRead, int demuxCorrupted, int demuxDiscontinuity, int decodedVideoFrames, int decodedAudioBlocks, int picturesDisplayed, int picturesLost, int audioBuffersPlayed, int audioBuffersLost, long deliveredVideoFrames, long deliveredAudioBuffers) {

    public double videoDropRatio() {
        int total = picturesDisplayed + picturesLost;
        return total == 0 ? 0.0 : (double) picturesLost / total;
    }

    public double audioDropRatio() {
        int total = audioBuffersPlayed + audioBuffersLost;
        return total == 0 ? 0.0 : (double) audioBuffersLost / total;
    }
}
