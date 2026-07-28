package com.xnexusacs.visioncore.common.plugin;

import com.xnexusacs.visioncore.common.log.MediaLogger;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public final class PluginLoader {

    private final MediaLogger logger;

    public PluginLoader(MediaLogger logger) {
        this.logger = logger;
    }

    public List<MediaPlugin> loadAll(PluginContext context) {
        List<MediaPlugin> loaded = new ArrayList<>();

        for (MediaPlugin plugin : ServiceLoader.load(MediaPlugin.class)) {
            try {
                plugin.init(context);
                loaded.add(plugin);
                logger.info("VisionCore plugin loaded: {}", plugin.id());
            } catch (RuntimeException e) {
                logger.error("There was an error loading the plugin '" + safeId(plugin) + "', omitted", e);
            }
        }

        return loaded;
    }

    private static String safeId(MediaPlugin plugin) {
        try {
            return plugin.id();
        }
        catch (RuntimeException e) {
            return plugin.getClass().getName();
        }
    }
}
