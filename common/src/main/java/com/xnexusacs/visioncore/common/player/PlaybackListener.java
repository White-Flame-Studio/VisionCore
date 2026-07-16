package com.xnexusacs.visioncore.common.player;

import com.xnexusacs.visioncore.common.exception.MediaException;

public interface PlaybackListener {

    default void onStateChanged(PlaybackState previous, PlaybackState current) { }

    default void onError(MediaException error) { }

    default void onEndReached() { }
}
