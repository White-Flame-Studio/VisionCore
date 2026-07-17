package com.xnexusacs.visioncore.client;

import com.xnexusacs.visioncore.client.commands.VisionCoreClientCommand;
import com.xnexusacs.visioncore.client.log.Slf4jMediaLogger;
import com.xnexusacs.visioncore.common.MediaCore;
import com.xnexusacs.visioncore.common.config.MediaCoreConfig;
import com.xnexusacs.visioncore.common.frame.FrameBufferPool;
import com.xnexusacs.visioncore.common.player.PlayerPool;
import com.xnexusacs.visioncore.common.player.vlc.VlcEngine;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Path;

public class VisionCoreClient implements ClientModInitializer {

    private static MediaCore core;
    private static VlcEngine vlcEngine;
    private static PlayerPool playerPool;

    @Override
    public void onInitializeClient() {
        Path cacheDir = FabricLoader.getInstance().getGameDir().resolve("visioncore-cache");

        MediaCoreConfig config = MediaCoreConfig.builder(cacheDir).logger(new Slf4jMediaLogger("VisionCore")).build();

        core = MediaCore.init(config);

        FrameBufferPool frameBufferPool = new FrameBufferPool(config.frameBufferPoolMaxPerBucket());
        vlcEngine = new VlcEngine(core.logger(), frameBufferPool, core.executors().dispatch());
        playerPool = core.createPlayerPool(vlcEngine::newHandle);

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> shutdown());
        VisionCoreClientCommand.register();

        core.logger().info("Client Initialized");
    }

    private static void shutdown() {
        if (playerPool != null) {
            playerPool.shutdownAll();
        }

        if (vlcEngine != null) {
            vlcEngine.close();
        }

        if (core != null) {
            core.shutdown();
        }
    }

    public static MediaCore core() {
        return core;
    }

    public static PlayerPool playerPool() {
        return playerPool;
    }
}
