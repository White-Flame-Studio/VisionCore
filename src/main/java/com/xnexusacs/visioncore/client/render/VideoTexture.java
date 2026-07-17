package com.xnexusacs.visioncore.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.resource.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.system.MemoryUtil;
import java.io.IOException;
import java.nio.ByteBuffer;

public final class VideoTexture extends AbstractTexture {

    private int currentWidth = -1;
    private int currentHeight = -1;

    public boolean uploadFrom(VideoFrameSink sink) {
        return sink.consumePending(this::upload);
    }

    private void upload(ByteBuffer bgra, int width, int height) {
        RenderSystem.assertOnRenderThreadOrInit();
        bindTexture();

        if (width != currentWidth || height != currentHeight) {
            GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, null);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            currentWidth = width;
            currentHeight = height;
        }

        GlStateManager._texSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, MemoryUtil.memAddress(bgra));
    }

    @Override
    public void load(ResourceManager manager) throws IOException {
        // Ignore.
    }

    public int width() {
        return currentWidth;
    }

    public int height() {
        return currentHeight;
    }
}
