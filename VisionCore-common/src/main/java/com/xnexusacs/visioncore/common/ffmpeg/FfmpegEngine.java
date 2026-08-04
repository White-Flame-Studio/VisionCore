package com.xnexusacs.visioncore.common.ffmpeg;

import com.xnexusacs.visioncore.common.audio.AudioBufferPool;
import com.xnexusacs.visioncore.common.audio.AudioSampleSink;
import com.xnexusacs.visioncore.common.config.FfmpegOptions;
import com.xnexusacs.visioncore.common.ffmpeg.effects.AudioEffect;
import com.xnexusacs.visioncore.common.ffmpeg.enhancer.FfmpegAudioEnhancer;
import com.xnexusacs.visioncore.common.ffmpeg.enhancer.FfmpegVideoEnhancer;
import com.xnexusacs.visioncore.common.frame.FrameBufferPool;
import com.xnexusacs.visioncore.common.frame.FrameSink;
import com.xnexusacs.visioncore.common.log.MediaLogger;

public final class FfmpegEngine {

    private final FfmpegOptions options;
    private final FrameBufferPool frameBufferPool;
    private final AudioBufferPool audioBufferPool;
    private final MediaLogger logger;
    private final FfmpegDiscovery discovery;

    public FfmpegEngine(FfmpegOptions options, FrameBufferPool frameBufferPool, AudioBufferPool audioBufferPool, MediaLogger logger) {
        this.options = options;
        this.frameBufferPool = frameBufferPool;
        this.audioBufferPool = audioBufferPool;
        this.logger = logger;
        this.discovery = new FfmpegDiscovery(options.executable(), options.probeTimeout(), logger);
    }

    public boolean isAvailable() {
        return options.enabled() && discovery.isAvailable();
    }

    public FrameSink wrapVideoSink(FrameSink delegate) {
        return wrapVideoSink(delegate, options.videoFilterGraph());
    }

    public FrameSink wrapVideoSink(FrameSink delegate, String filterGraph) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate can't be null");
        }

        if (filterGraph == null || filterGraph.isBlank() || !isAvailable()) {
            return delegate;
        }

        return new FfmpegVideoEnhancer(delegate, frameBufferPool, options.executable(), filterGraph, logger);
    }

    public AudioSampleSink wrapAudioSink(AudioSampleSink delegate) {
        return wrapAudioSink(delegate, options.audioFilterGraph());
    }

    public AudioSampleSink wrapAudioSink(AudioSampleSink delegate, String filterGraph) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate can't be null");
        }

        if (filterGraph == null || filterGraph.isBlank() || !isAvailable()) {
            return delegate;
        }

        return new FfmpegAudioEnhancer(delegate, audioBufferPool, options.executable(), filterGraph, logger);
    }

    public AudioSampleSink wrapAudioSink(AudioSampleSink delegate, AudioEffect effect) {
        if (effect == null) {
            throw new IllegalArgumentException("effect can't be null");
        }

        return wrapAudioSink(delegate, effect.filterGraph());
    }
}
