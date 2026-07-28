package com.xnexusacs.visioncore.common.event.events;

import com.xnexusacs.visioncore.common.player.PlaybackState;

public final class PlaybackStateChangedEvent extends MediaEvent {

    private final PlaybackState previous;
    private final PlaybackState current;

    public PlaybackStateChangedEvent(String playerId, PlaybackState previous, PlaybackState current) {
        super(playerId);
        this.previous = previous;
        this.current = current;
    }

    public PlaybackState previous() {
        return previous;
    }

    public PlaybackState current() {
        return current;
    }
}
