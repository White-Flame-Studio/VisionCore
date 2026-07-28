package com.xnexusacs.visioncore.common.event.events;

import com.xnexusacs.visioncore.common.exception.MediaException;

public final class PlaybackErrorEvent extends MediaEvent {

    private final MediaException error;

    public PlaybackErrorEvent(String playerId, MediaException error) {
        super(playerId);
        this.error = error;
    }

    public MediaException error() {
        return error;
    }
}
