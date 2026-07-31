package com.xnexusacs.visioncore.common.subtitles;

public interface SubtitleSink {

    void onCue(SubtitleCue cue);

    default void onCueCleared() { }
}
