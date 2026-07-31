package com.xnexusacs.visioncore.client.render;

import com.xnexusacs.visioncore.client.VisionCoreClient;
import com.xnexusacs.visioncore.common.exception.MediaException;
import com.xnexusacs.visioncore.common.player.MediaPlayerHandle;
import com.xnexusacs.visioncore.common.player.PlaybackListener;
import com.xnexusacs.visioncore.common.player.PlaybackState;
import com.xnexusacs.visioncore.common.player.PlayerPool;
import com.xnexusacs.visioncore.common.source.ResolvedMedia;
import com.xnexusacs.visioncore.common.subtitles.SubtitleCue;
import com.xnexusacs.visioncore.common.subtitles.SubtitleSink;
import com.xnexusacs.visioncore.common.subtitles.SubtitleTrack;
import com.xnexusacs.visioncore.common.subtitles.SubtitleTrackController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

public final class VideoScreen extends Screen {

    private static final Identifier TEXTURE_ID = new Identifier("visioncore", "video/player-screen");
    private static final int SUBTITLE_BOTTOM_MARGIN = 20;
    private static final int SUBTITLE_COLOR = 0xFFFFFF;

    private final PlayerPool playerPool;
    private final URI mediaUri;
    private final Path subtitleFile;

    private final SubtitleSink subtitleSink = new SubtitleSink() {
        @Override
        public void onCue(SubtitleCue cue) {
            subtitleText = cue.plainText();
        }

        @Override
        public void onCueCleared() {
            subtitleText = null;
        }
    };

    private final PlaybackListener playbackListener = new PlaybackListener() {
        @Override
        public void onStateChanged(PlaybackState previous, PlaybackState current) {
            lastKnownState = current;
        }

        @Override
        public void onError(MediaException error) {
            errorMessage = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
        }

        @Override
        public void onEndReached() {
            MinecraftClient client = MinecraftClient.getInstance();
            VisionCoreClient.runNextTick(() -> client.setScreen(null));
        }
    };

    private MediaPlayerHandle handle;
    private VideoFrameSink frameSink;
    private VideoTexture texture;
    private SubtitleTrackController subtitleController;

    private volatile String errorMessage;
    private volatile String subtitleText;
    private volatile PlaybackState lastKnownState = PlaybackState.IDLE;

    public VideoScreen(PlayerPool playerPool, URI mediaUri) {
        this(playerPool, mediaUri, null);
    }

    public VideoScreen(PlayerPool playerPool, URI mediaUri, Path subtitleFile) {
        super(Text.literal("VisionCore - VideoPlayer Screen"));
        this.playerPool = playerPool;
        this.mediaUri = mediaUri;
        this.subtitleFile = subtitleFile;
    }

    @Override
    protected void init() {
        super.init();
        this.frameSink = new VideoFrameSink();
        this.texture = new VideoTexture();
        Objects.requireNonNull(this.client).getTextureManager().registerTexture(TEXTURE_ID, texture);

        try {
            this.handle = playerPool.borrow();
            this.handle.addListener(playbackListener);
            this.handle.play(ResolvedMedia.of(mediaUri), frameSink);
        } catch (RuntimeException e) {
            this.errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return;
        }

        if (subtitleFile != null) {
            try {
                SubtitleTrack track = VisionCoreClient.subtitleEngine().load(subtitleFile);
                this.subtitleController = VisionCoreClient.subtitleEngine().attach(handle, track, subtitleSink);
            } catch (RuntimeException e) {
                VisionCoreClient.core().logger().warn("Couldn't load subtitles from '" + subtitleFile + "'", e);
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        if (errorMessage != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Error: " + errorMessage), width / 2, height / 2, 0xFF5555);
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        texture.uploadFrom(frameSink);

        int texWidth = texture.width();
        int texHeight = texture.height();

        if (texWidth <= 0 || texHeight <= 0) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Loading Video... (" + lastKnownState + ")"), width / 2, height / 2, 0xFFFFFF);
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        float scaleX = (float) width / texWidth;
        float scaleY = (float) height / texHeight;

        context.getMatrices().push();
        context.getMatrices().scale(scaleX, scaleY, 1.0f);
        context.drawTexture(TEXTURE_ID, 0, 0, 0, 0, texWidth, texHeight, texWidth, texHeight);
        context.getMatrices().pop();

        renderSubtitles(context);

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderSubtitles(DrawContext context) {
        String text = subtitleText;

        if (text == null || text.isEmpty()) {
            return;
        }

        String[] lines = text.split("\n");
        int lineHeight = textRenderer.fontHeight + 2;
        int y = height - SUBTITLE_BOTTOM_MARGIN - lines.length * lineHeight;

        for (String line : lines) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(line), width / 2, y, SUBTITLE_COLOR);
            y += lineHeight;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void removed() {
        if (subtitleController != null) {
            subtitleController.close();
            subtitleController = null;
        }
        if (handle != null) {
            handle.removeListener(playbackListener);
            playerPool.release(handle);
            handle = null;
        }
        if (client != null) {
            client.getTextureManager().destroyTexture(TEXTURE_ID);
        }
        super.removed();
    }
}
