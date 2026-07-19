package com.xnexusacs.visioncore.common;

import com.xnexusacs.visioncore.common.cache.DiskCache;
import com.xnexusacs.visioncore.common.cache.MemoryLruCache;
import com.xnexusacs.visioncore.common.concurrency.MediaExecutors;
import com.xnexusacs.visioncore.common.concurrency.SequencedDispatcher;
import com.xnexusacs.visioncore.common.config.MediaCoreConfig;
import com.xnexusacs.visioncore.common.event.EventBus;
import com.xnexusacs.visioncore.common.log.MediaLogger;
import com.xnexusacs.visioncore.common.net.HttpClientProvider;
import com.xnexusacs.visioncore.common.net.UrlFixerRegistry;
import com.xnexusacs.visioncore.common.player.MediaPlayerHandle;
import com.xnexusacs.visioncore.common.player.PlayerPool;
import com.xnexusacs.visioncore.common.plugin.MediaPlugin;
import com.xnexusacs.visioncore.common.plugin.PluginContext;
import com.xnexusacs.visioncore.common.plugin.PluginLoader;
import com.xnexusacs.visioncore.common.source.SourceRegistry;
import com.xnexusacs.visioncore.common.source.providers.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class MediaCore {

    private final MediaCoreConfig config;
    private final MediaLogger logger;
    private final MediaExecutors executors;
    private final EventBus events;
    private final SourceRegistry sources;
    private final UrlFixerRegistry urlFixers;
    private final HttpClientProvider http;
    private final MemoryLruCache<Object> memoryCache;
    private final DiskCache diskCache;
    private final SequencedDispatcher<String> playerDispatcher;
    private final List<MediaPlugin> loadedPlugins;

    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private MediaCore(MediaCoreConfig config) {
        this.config = config;
        this.logger = config.logger();
        this.executors = new MediaExecutors(config, logger);
        this.events = new EventBus(executors.dispatch(), logger);
        this.sources = new SourceRegistry();
        this.urlFixers = new UrlFixerRegistry();
        this.http = new HttpClientProvider(executors.io(), config.httpConnectTimeout());

        this.sources.register(new DirectUriMediaSource());
        this.sources.register(new GoogleDriveMediaSource(http));
        this.sources.register(new YtDlpMediaSource(logger));
        this.sources.register(new TwitchMediaSource(logger));
        this.sources.register(new SoundCloudMediaSource(logger));

        this.memoryCache = new MemoryLruCache<>(config.memoryCacheMaxEntries());
        this.diskCache = new DiskCache(config.cacheDirectory(), config.diskCacheTtl(), logger);
        this.playerDispatcher = new SequencedDispatcher<>(executors.dispatch());
        PluginLoader pluginLoader = new PluginLoader(logger);

        PluginContext context = new PluginContext(sources, urlFixers, http, events, logger);
        this.loadedPlugins = pluginLoader.loadAll(context);
    }

    public static MediaCore init(MediaCoreConfig config) {
        MediaCore core = new MediaCore(config);
        core.logger.info("Vision Core core initialized ({} plugin(s) loaded)", core.loadedPlugins.size());
        return core;
    }

    public PlayerPool createPlayerPool(Supplier<MediaPlayerHandle> handleFactory) {
        return new PlayerPool(handleFactory, config.playerPoolMaxSize(), logger);
    }

    public MediaCoreConfig config() {
        return config;
    }

    public MediaLogger logger() {
        return logger;
    }

    public MediaExecutors executors() {
        return executors;
    }

    public EventBus events() {
        return events;
    }

    public SourceRegistry sources() {
        return sources;
    }

    public UrlFixerRegistry urlFixers() {
        return urlFixers;
    }

    public HttpClientProvider http() {
        return http;
    }

    public MemoryLruCache<Object> memoryCache() {
        return memoryCache;
    }

    public DiskCache diskCache() {
        return diskCache;
    }

    public SequencedDispatcher<String> playerDispatcher() {
        return playerDispatcher;
    }

    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }

        logger.info("Shutting down Vision Core...");

        for (MediaPlugin plugin : loadedPlugins) {
            try {
                plugin.shutdown();
            } catch (RuntimeException e) {
                logger.error("There was an error stopping the plugin '" + plugin.id() + "'", e);
            }
        }

        playerDispatcher.shutdown();
        executors.shutdown();
        logger.info("Vision Core shutdown complete");
    }
}
