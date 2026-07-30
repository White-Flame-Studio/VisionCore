package com.xnexusacs.visioncore.common.event.events;

public final class PlaybackStatsEvent extends MediaEvent {

    private final PlaybackStats stats;

    public PlaybackStatsEvent(String playerId, PlaybackStats stats) {
        super(playerId);
        this.stats = stats;
    }

    public PlaybackStats stats() {
        return stats;
    }
}
