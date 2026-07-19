package com.xnexusacs.visioncore.client.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.xnexusacs.visioncore.client.VisionCoreClient;
import com.xnexusacs.visioncore.client.audio.OpenAlAudioSink;
import com.xnexusacs.visioncore.client.render.VideoScreen;
import com.xnexusacs.visioncore.common.MediaCore;
import com.xnexusacs.visioncore.common.exception.MediaException;
import com.xnexusacs.visioncore.common.player.MediaPlayerHandle;
import com.xnexusacs.visioncore.common.player.PlaybackListener;
import com.xnexusacs.visioncore.common.player.PlayerPool;
import com.xnexusacs.visioncore.common.source.ResolvedMedia;
import com.xnexusacs.visioncore.resource.ModResourceExtractor;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import java.io.UncheckedIOException;
import java.net.URI;

public final class VisionCoreClientCommand {

    private static volatile MediaPlayerHandle currentAudioHandle;
    private static volatile OpenAlAudioSink currentAudioSink;
    private static volatile PlaybackListener currentAudioListener;

    private VisionCoreClientCommand() { }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("visioncoreclient")
                        .then(ClientCommandManager.literal("play")
                                .then(ClientCommandManager.argument("url", StringArgumentType.greedyString())
                                        .executes(VisionCoreClientCommand::executePlay)))
                        .then(ClientCommandManager.literal("play-file")
                                .then(ClientCommandManager.argument("file", StringArgumentType.string())
                                        .executes(VisionCoreClientCommand::executePlayFile)))
                        .then(ClientCommandManager.literal("stop-video")
                                .executes(VisionCoreClientCommand::executeStopVideo))
                        .then(ClientCommandManager.literal("play-audio")
                                .then(ClientCommandManager.argument("url", StringArgumentType.greedyString())
                                        .executes(VisionCoreClientCommand::executePlayAudio)))
                        .then(ClientCommandManager.literal("play-audio-file")
                                .then(ClientCommandManager.argument("file", StringArgumentType.string())
                                        .executes(VisionCoreClientCommand::executePlayAudioFile)))
                        .then(ClientCommandManager.literal("volume-audio")
                                .then(ClientCommandManager.argument("percent", IntegerArgumentType.integer(0, 100))
                                        .executes(VisionCoreClientCommand::executeVolumeAudio)))
                        .then(ClientCommandManager.literal("volume-master")
                                .then(ClientCommandManager.argument("percent", IntegerArgumentType.integer(0, 100))
                                        .executes(VisionCoreClientCommand::executeVolumeMaster)))
                        .then(ClientCommandManager.literal("stop-audio")
                                .executes(VisionCoreClientCommand::executeStopAudio))));
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

        MediaCore core = VisionCoreClient.core();
        PlayerPool playerPool = VisionCoreClient.playerPool();
        if (core == null || playerPool == null) {
            context.getSource().sendError(Text.literal("VisionCore is not initialized"));
            return 0;
        }

        context.getSource().sendFeedback(Text.literal("Resolving: " + rawUrl + "..."));
        URI finalUri = uri;
        core.executors().io().execute(() -> {
            ResolvedMedia resolved;
            try {
                resolved = core.sources().resolve(finalUri);
            } catch (RuntimeException e) {
                VisionCoreClient.runNextTick(() -> context.getSource().sendError(Text.literal("Couldn't resolve '" + rawUrl + "': " + e.getMessage())));
                return;
            }
            String label = resolved.title() != null ? resolved.title() : rawUrl;
            VisionCoreClient.runNextTick(() -> {
                MinecraftClient.getInstance().setScreen(new VideoScreen(playerPool, resolved.playableUri()));
                context.getSource().sendFeedback(Text.literal("Playing: " + label));
            });
        });

        return 1;
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

    private static int executeStopVideo(CommandContext<FabricClientCommandSource> context) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (!(client.currentScreen instanceof VideoScreen)) {
            context.getSource().sendFeedback(Text.literal("No video is currently playing"));
            return 0;
        }

        VisionCoreClient.runNextTick(() -> client.setScreen(null));
        context.getSource().sendFeedback(Text.literal("Video stopped"));
        return 1;
    }

    private static int executeVolumeAudio(CommandContext<FabricClientCommandSource> context) {
        if (currentAudioSink == null) {
            context.getSource().sendError(Text.literal("No audio is currently playing"));
            return 0;
        }

        int percent = IntegerArgumentType.getInteger(context, "percent");
        currentAudioSink.setVolume(percent / 100f);
        context.getSource().sendFeedback(Text.literal("Audio volume set to " + percent + "%"));
        return 1;
    }

    private static int executeVolumeMaster(CommandContext<FabricClientCommandSource> context) {
        int percent = IntegerArgumentType.getInteger(context, "percent");
        VisionCoreClient.audioEngine().setMasterVolume(percent / 100f);
        context.getSource().sendFeedback(Text.literal("Master volume set to " + percent + "%"));
        return 1;
    }

    private static int executePlayAudio(CommandContext<FabricClientCommandSource> context) {
        String rawUrl = StringArgumentType.getString(context, "url");

        URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (IllegalArgumentException e) {
            context.getSource().sendError(Text.literal("Invalid URL: " + rawUrl));
            return 0;
        }

        MediaCore core = VisionCoreClient.core();
        PlayerPool playerPool = VisionCoreClient.playerPool();
        if (core == null || playerPool == null) {
            context.getSource().sendError(Text.literal("VisionCore is not initialized"));
            return 0;
        }

        context.getSource().sendFeedback(Text.literal("Resolving: " + rawUrl + "..."));
        URI finalUri = uri;
        core.executors().io().execute(() -> {
            ResolvedMedia resolved;
            try {
                resolved = core.sources().resolve(finalUri);
            } catch (RuntimeException e) {
                VisionCoreClient.runNextTick(() -> context.getSource().sendError(Text.literal("Couldn't resolve '" + rawUrl + "': " + e.getMessage())));
                return;
            }
            String label = resolved.title() != null ? resolved.title() : rawUrl;
            ResolvedMedia finalResolved = resolved;
            VisionCoreClient.runNextTick(() -> playAudio(context, finalResolved, label));
        });

        return 1;
    }

    private static int executePlayAudioFile(CommandContext<FabricClientCommandSource> context) {
        String fileName = StringArgumentType.getString(context, "file");

        URI uri;
        try {
            uri = ModResourceExtractor.extractToCache(fileName);
        } catch (IllegalArgumentException | UncheckedIOException e) {
            context.getSource().sendError(Text.literal("Couldn't load '" + fileName + "': " + e.getMessage()));
            return 0;
        }

        return playAudio(context, ResolvedMedia.of(uri), fileName);
    }

    private static int executeStopAudio(CommandContext<FabricClientCommandSource> context) {
        if (currentAudioHandle == null) {
            context.getSource().sendFeedback(Text.literal("No audio is currently playing"));
            return 0;
        }

        stopCurrentAudio();
        context.getSource().sendFeedback(Text.literal("Audio stopped"));
        return 1;
    }

    private static synchronized int playAudio(CommandContext<FabricClientCommandSource> context, ResolvedMedia media, String feedbackLabel) {
        PlayerPool playerPool = VisionCoreClient.playerPool();
        if (playerPool == null) {
            context.getSource().sendError(Text.literal("VisionCore is not initialized"));
            return 0;
        }

        stopCurrentAudio();

        MediaPlayerHandle handle = playerPool.borrow();
        OpenAlAudioSink sink = VisionCoreClient.audioEngine().newSink();

        PlaybackListener listener = new PlaybackListener() {
            @Override
            public void onEndReached() {
                finishAudio(handle, sink, this);
            }

            @Override
            public void onError(MediaException error) {
                finishAudio(handle, sink, this);
            }
        };

        handle.addListener(listener);
        currentAudioHandle = handle;
        currentAudioSink = sink;
        currentAudioListener = listener;

        handle.play(media, sink);

        context.getSource().sendFeedback(Text.literal("Playing audio: " + feedbackLabel));
        return 1;
    }

    private static synchronized void finishAudio(MediaPlayerHandle handle, OpenAlAudioSink sink, PlaybackListener listener) {
        handle.removeListener(listener);
        sink.close();

        PlayerPool playerPool = VisionCoreClient.playerPool();

        if (playerPool != null) {
            playerPool.release(handle);
        }

        if (currentAudioHandle == handle) {
            currentAudioHandle = null;
            currentAudioSink = null;
            currentAudioListener = null;
        }
    }

    private static synchronized void stopCurrentAudio() {
        MediaPlayerHandle handle = currentAudioHandle;

        if (handle == null) {
            return;
        }

        handle.stop();
        finishAudio(handle, currentAudioSink, currentAudioListener);
    }

    private static int openVideoScreen(CommandContext<FabricClientCommandSource> context, URI uri, String feedbackLabel) {
        PlayerPool playerPool = VisionCoreClient.playerPool();

        if (playerPool == null) {
            context.getSource().sendError(Text.literal("VisionCore is not initialized"));
            return 0;
        }

        VisionCoreClient.runNextTick(() -> MinecraftClient.getInstance().setScreen(new VideoScreen(playerPool, uri)));
        context.getSource().sendFeedback(Text.literal("Playing: " + feedbackLabel));
        return 1;
    }
}
