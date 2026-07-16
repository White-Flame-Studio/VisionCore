package com.xnexusacs.visioncore.common.event.events;

public final class BufferingEvent extends MediaEvent {

    private final int percent;

    public BufferingEvent(String playerId, int percent) {
        super(playerId);
        this.percent = percent;
    }

    public int percent() {
        return percent;
    }
}
