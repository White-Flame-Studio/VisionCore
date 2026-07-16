package com.xnexusacs.visioncore.common.plugin;

public interface MediaPlugin {

    String id();

    void init(PluginContext context);

    default void shutdown() { }
}
