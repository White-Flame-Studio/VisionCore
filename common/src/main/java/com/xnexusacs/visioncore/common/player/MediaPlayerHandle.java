package com.xnexusacs.visioncore.common.player;

import com.xnexusacs.visioncore.common.audio.AudioSampleSink;
import com.xnexusacs.visioncore.common.frame.FrameSink;
import com.xnexusacs.visioncore.common.source.ResolvedMedia;

public interface MediaPlayerHandle {

    String id();

    void play(ResolvedMedia media, FrameSink sink);

    void play(ResolvedMedia media, AudioSampleSink audioSink);

    void pause();

    void resume();

    void stop();

    void seek(long millis);

    PlaybackState state();

    void addListener(PlaybackListener listener);

    void removeListener(PlaybackListener listener);

    void release();
}
