package com.xnexusacs.visioncore.client.render;

import com.xnexusacs.visioncore.client.VisionCoreClient;
import com.xnexusacs.visioncore.common.exception.MediaException;
import com.xnexusacs.visioncore.common.player.MediaPlayerHandle;
import com.xnexusacs.visioncore.common.player.PlaybackListener;
import com.xnexusacs.visioncore.common.player.PlaybackState;
import com.xnexusacs.visioncore.common.player.PlayerPool;
import com.xnexusacs.visioncore.common.source.ResolvedMedia;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.net.URI;
import java.util.Objects;

public final class VideoScreen extends Screen {

    private static final Identifier TEXTURE_ID = new Identifier("visioncore", "video/player-screen");

    private final PlayerPool playerPool;
    private final URI mediaUri;

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

    private volatile String errorMessage;
    private volatile PlaybackState lastKnownState = PlaybackState.IDLE;

    public VideoScreen(PlayerPool playerPool, URI mediaUri) {
        super(Text.literal("VisionCore - VideoPlayer Screen"));
        this.playerPool = playerPool;
        this.mediaUri = mediaUri;
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

        super.render(context, mouseX, mouseY, delta);
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
