package com.xnexusacs.visioncore.common.event.events;

public abstract class MediaEvent {

    private final String playerId;
    private final long timestampMillis;

    protected MediaEvent(String playerId) {
        this.playerId = playerId;
        this.timestampMillis = System.currentTimeMillis();
    }

    public String playerId() {
        return playerId;
    }

    public long timestampMillis() {
        return timestampMillis;
    }
}
