package com.xnexusacs.visioncore.client.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.xnexusacs.visioncore.client.VisionCoreClient;
import com.xnexusacs.visioncore.client.render.VideoScreen;
import com.xnexusacs.visioncore.common.player.PlayerPool;
import com.xnexusacs.visioncore.resource.ModResourceExtractor;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import java.io.UncheckedIOException;
import java.net.URI;

public final class VisionCoreClientCommand {

    private VisionCoreClientCommand() { }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("visioncoreclient")
                        .then(ClientCommandManager.literal("play")
                                .then(ClientCommandManager.argument("url", StringArgumentType.greedyString())
                                        .executes(VisionCoreClientCommand::executePlay)))
                        .then(ClientCommandManager.literal("play-file")
                                .then(ClientCommandManager.argument("file", StringArgumentType.string())
                                        .executes(VisionCoreClientCommand::executePlayFile)))));
    }

    private static int executePlay(CommandContext<FabricClientCommandSource> context) {
        String rawUrl = StringArgumentType.getString(context, "url");

        URI uri;

        try {
            uri = URI.create(rawUrl);
        } catch (IllegalArgumentException e) {
            context.getSource().sendError(Text.literal("Invalid URL: " + rawUrl));
            return 0;
        }

        return openVideoScreen(context, uri, rawUrl);
    }

    private static int executePlayFile(CommandContext<FabricClientCommandSource> context) {
        String fileName = StringArgumentType.getString(context, "file");

        URI uri;
        try {
            uri = ModResourceExtractor.extractToCache(fileName);
        } catch (IllegalArgumentException | UncheckedIOException e) {
            context.getSource().sendError(Text.literal("Couldn't load '" + fileName + "': " + e.getMessage()));
            return 0;
        }

        return openVideoScreen(context, uri, fileName);
    }

    private static int openVideoScreen(CommandContext<FabricClientCommandSource> context, URI uri, String feedbackLabel) {
        PlayerPool playerPool = VisionCoreClient.playerPool();
        if (playerPool == null) {
            context.getSource().sendError(Text.literal("VisionCore is not initialized"));
            return 0;
        }

        MinecraftClient.getInstance().setScreen(new VideoScreen(playerPool, uri));
        context.getSource().sendFeedback(Text.literal("Playing: " + feedbackLabel));
        return 1;
    }
}
