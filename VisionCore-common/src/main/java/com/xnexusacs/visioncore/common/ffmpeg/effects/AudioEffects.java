package com.xnexusacs.visioncore.common.ffmpeg.effects;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public final class AudioEffects {

    private AudioEffects() { }

    public static AudioEffect reverb() {
        return reverb(0.8, 0.9, 1000, 0.3);
    }

    public static AudioEffect reverb(double inGain, double outGain, int delayMillis, double decay) {
        return new AudioEffect("reverb", String.format(Locale.ROOT, "aecho=%.2f:%.2f:%d:%.2f", inGain, outGain, delayMillis, decay));
    }

    public static AudioEffect echo() {
        return echo(500, 0.5);
    }

    public static AudioEffect echo(int delayMillis, double decay) {
        return new AudioEffect("echo", String.format(Locale.ROOT, "aecho=0.8:0.9:%d:%.2f", delayMillis, decay));
    }

    public static AudioEffect slowedReverb() {
        return slowedReverb(0.85);
    }

    public static AudioEffect slowedReverb(double speed) {
        String filterGraph = tempoFilter(speed) + ",aecho=0.8:0.9:1000:0.3,aecho=0.8:0.88:1800:0.25";
        return new AudioEffect("slowed-reverb", filterGraph);
    }

    public static AudioEffect nightcore() {
        return nightcore(1.25);
    }

    public static AudioEffect nightcore(double speed) {
        return new AudioEffect("nightcore", tempoFilter(speed));
    }

    public static AudioEffect bassBoost() {
        return bassBoost(10);
    }

    public static AudioEffect bassBoost(double gainDb) {
        return new AudioEffect("bass-boost", String.format(Locale.ROOT, "bass=g=%.1f", gainDb));
    }

    public static AudioEffect chain(AudioEffect... effects) {
        if (effects.length == 0) {
            throw new IllegalArgumentException("Need at least one effect to chain");
        }

        String name = Arrays.stream(effects).map(AudioEffect::name).collect(Collectors.joining("+"));
        String filterGraph = Arrays.stream(effects).map(AudioEffect::filterGraph).collect(Collectors.joining(","));
        return new AudioEffect(name, filterGraph);
    }

    private static String tempoFilter(double speed) {
        if (speed < 0.5 || speed > 2.0) {
            throw new IllegalArgumentException("speed must be in [0.5, 2.0], got: " + speed);
        }

        return String.format(Locale.ROOT, "atempo=%.3f", speed);
    }
}
