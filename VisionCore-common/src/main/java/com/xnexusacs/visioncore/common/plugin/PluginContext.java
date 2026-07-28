package com.xnexusacs.visioncore.common.plugin;

import com.xnexusacs.visioncore.common.event.EventBus;
import com.xnexusacs.visioncore.common.log.MediaLogger;
import com.xnexusacs.visioncore.common.net.HttpClientProvider;
import com.xnexusacs.visioncore.common.net.UrlFixerRegistry;
import com.xnexusacs.visioncore.common.source.SourceRegistry;

public record PluginContext(SourceRegistry sources, UrlFixerRegistry urlFixers, HttpClientProvider http, EventBus events, MediaLogger logger) { }
