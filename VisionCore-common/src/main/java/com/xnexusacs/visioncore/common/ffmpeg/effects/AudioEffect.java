package com.xnexusacs.visioncore.common.ffmpeg.effects;

public record AudioEffect(String name, String filterGraph) {

    public static AudioEffect of(String name, String filterGraph) {
        return new AudioEffect(name, filterGraph);
    }
}
