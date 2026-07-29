package com.xnexusacs.visioncore.common.config;

import java.time.Duration;

public final class FfmpegOptions {

    public static final String FILTER_SHARPEN = "unsharp=5:5:0.8:5:5:0.4";
    public static final String FILTER_DENOISE = "hqdn3d=4:3:6:4";
    public static final String FILTER_UPSCALE_LANCZOS_2X = "scale=iw*2:ih*2:flags=lanczos";
    public static final String FILTER_COLOR_BALANCE = "eq=contrast=1.05:brightness=0.01:saturation=1.1";
    public static final String FILTER_LOUDNESS_NORMALIZE = "loudnorm=I=-16:TP=-1.5:LRA=11";
    public static final String FILTER_DENOISE_AUDIO = "highpass=f=80,afftdn=nf=-25";

    private final boolean enabled;
    private final String executable;
    private final String videoFilterGraph;
    private final String audioFilterGraph;
    private final Duration probeTimeout;

    private FfmpegOptions(Builder builder) {
        this.enabled = builder.enabled;
        this.executable = builder.executable;
        this.videoFilterGraph = builder.videoFilterGraph;
        this.audioFilterGraph = builder.audioFilterGraph;
        this.probeTimeout = builder.probeTimeout;
    }

    public boolean enabled() {
        return enabled;
    }

    public String executable() {
        return executable;
    }

    public String videoFilterGraph() {
        return videoFilterGraph;
    }

    public String audioFilterGraph() {
        return audioFilterGraph;
    }

    public Duration probeTimeout() {
        return probeTimeout;
    }

    public static FfmpegOptions disabled() {
        return new Builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean enabled = false;
        private String executable = "ffmpeg";
        private String videoFilterGraph = null;
        private String audioFilterGraph = null;
        private Duration probeTimeout = Duration.ofSeconds(5);

        private Builder() {
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder executable(String executable) {
            this.executable = executable;
            return this;
        }

        public Builder videoFilterGraph(String filterGraph) {
            this.videoFilterGraph = filterGraph;
            return this;
        }

        public Builder audioFilterGraph(String filterGraph) {
            this.audioFilterGraph = filterGraph;
            return this;
        }

        public Builder probeTimeout(Duration timeout) {
            this.probeTimeout = timeout;
            return this;
        }

        public FfmpegOptions build() {
            if (executable == null || executable.isBlank()) {
                throw new IllegalArgumentException("executable can't be blank");
            }

            return new FfmpegOptions(this);
        }
    }
}
