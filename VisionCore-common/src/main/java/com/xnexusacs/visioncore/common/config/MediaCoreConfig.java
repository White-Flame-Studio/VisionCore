package com.xnexusacs.visioncore.common.config;

import com.xnexusacs.visioncore.common.log.ConsoleMediaLogger;
import com.xnexusacs.visioncore.common.log.MediaLogger;
import java.nio.file.Path;
import java.time.Duration;

public final class MediaCoreConfig {

    private final Path cacheDirectory;
    private final Duration diskCacheTtl;
    private final int memoryCacheMaxEntries;
    private final int ioThreadPoolSize;
    private final int dispatchThreadPoolSize;
    private final Duration httpConnectTimeout;
    private final int playerPoolMaxSize;
    private final int frameBufferPoolMaxPerBucket;
    private final MediaLogger logger;
    private final FfmpegOptions ffmpeg;
    private final Duration statsSampleInterval;

    private MediaCoreConfig(Builder builder) {
        this.cacheDirectory = builder.cacheDirectory;
        this.diskCacheTtl = builder.diskCacheTtl;
        this.memoryCacheMaxEntries = builder.memoryCacheMaxEntries;
        this.ioThreadPoolSize = builder.ioThreadPoolSize;
        this.dispatchThreadPoolSize = builder.dispatchThreadPoolSize;
        this.httpConnectTimeout = builder.httpConnectTimeout;
        this.playerPoolMaxSize = builder.playerPoolMaxSize;
        this.frameBufferPoolMaxPerBucket = builder.frameBufferPoolMaxPerBucket;
        this.logger = builder.logger;
        this.ffmpeg = builder.ffmpeg;
        this.statsSampleInterval = builder.statsSampleInterval;
    }

    public Path cacheDirectory() {
        return cacheDirectory;
    }

    public Duration diskCacheTtl() {
        return diskCacheTtl;
    }

    public int memoryCacheMaxEntries() {
        return memoryCacheMaxEntries;
    }

    public int ioThreadPoolSize() {
        return ioThreadPoolSize;
    }

    public int dispatchThreadPoolSize() {
        return dispatchThreadPoolSize;
    }

    public Duration httpConnectTimeout() {
        return httpConnectTimeout;
    }

    public int playerPoolMaxSize() {
        return playerPoolMaxSize;
    }

    public int frameBufferPoolMaxPerBucket() {
        return frameBufferPoolMaxPerBucket;
    }

    public MediaLogger logger() {
        return logger;
    }

    public FfmpegOptions ffmpeg() {
        return ffmpeg;
    }

    public Duration statsSampleInterval() {
        return statsSampleInterval;
    }

    public static Builder builder(Path cacheDirectory) {
        return new Builder(cacheDirectory);
    }

    public static final class Builder {
        private final Path cacheDirectory;
        private Duration diskCacheTtl = Duration.ofDays(3);
        private int memoryCacheMaxEntries = 256;
        private int ioThreadPoolSize = 4;
        private int dispatchThreadPoolSize = 2;
        private Duration httpConnectTimeout = Duration.ofSeconds(10);
        private int playerPoolMaxSize = 8;
        private int frameBufferPoolMaxPerBucket = 4;
        private MediaLogger logger = new ConsoleMediaLogger();
        private FfmpegOptions ffmpeg = FfmpegOptions.disabled();
        private Duration statsSampleInterval = Duration.ofSeconds(2);

        private Builder(Path cacheDirectory) {
            this.cacheDirectory = cacheDirectory;
        }

        public Builder diskCacheTtl(Duration ttl) {
            this.diskCacheTtl = ttl;
            return this;
        }

        public Builder memoryCacheMaxEntries(int entries) {
            this.memoryCacheMaxEntries = entries;
            return this;
        }

        public Builder ioThreadPoolSize(int size) {
            this.ioThreadPoolSize = size;
            return this;
        }

        public Builder dispatchThreadPoolSize(int size) {
            this.dispatchThreadPoolSize = size;
            return this;
        }

        public Builder httpConnectTimeout(Duration timeout) {
            this.httpConnectTimeout = timeout;
            return this;
        }

        public Builder playerPoolMaxSize(int size) {
            this.playerPoolMaxSize = size;
            return this;
        }

        public Builder frameBufferPoolMaxPerBucket(int size) {
            this.frameBufferPoolMaxPerBucket = size;
            return this;
        }

        public Builder logger(MediaLogger logger) {
            this.logger = logger;
            return this;
        }

        public Builder ffmpeg(FfmpegOptions ffmpeg) {
            this.ffmpeg = ffmpeg;
            return this;
        }

        public Builder statsSampleInterval(Duration interval) {
            this.statsSampleInterval = interval;
            return this;
        }

        public MediaCoreConfig build() {
            if (ioThreadPoolSize < 1 || dispatchThreadPoolSize < 1) {
                throw new IllegalArgumentException("Thread Pools need at least 1 thread");
            }
            if (playerPoolMaxSize < 1) {
                throw new IllegalArgumentException("playerPoolMaxSize must be >= 1");
            }
            return new MediaCoreConfig(this);
        }
    }
}
