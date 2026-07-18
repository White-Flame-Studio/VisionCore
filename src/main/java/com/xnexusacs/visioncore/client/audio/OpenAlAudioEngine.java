package com.xnexusacs.visioncore.client.audio;

import com.xnexusacs.visioncore.common.log.MediaLogger;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.lwjgl.openal.ALC10.*;

public final class OpenAlAudioEngine implements AutoCloseable {

    private final MediaLogger logger;
    private final ExecutorService audioThread;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private long device;
    private long context;

    public OpenAlAudioEngine(MediaLogger logger) {
        this.logger = logger;
        this.audioThread = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "visioncore-openal");
            thread.setDaemon(true);
            return thread;
        });

        runAndWait(() -> {
            device = alcOpenDevice((ByteBuffer) null);

            if (device == 0L) {
                throw new IllegalStateException("Couldn't open default OpenAL device");
            }

            ALCCapabilities deviceCaps = ALC.createCapabilities(device);
            context = alcCreateContext(device, (IntBuffer) null);
            if (context == 0L) {
                throw new IllegalStateException("Error creating OpenAL context");
            }
            alcMakeContextCurrent(context);
            AL.createCapabilities(deviceCaps);
        });

        logger.info("OpenAlAudioEngine started");
    }

    public OpenAlAudioSink newSink() {
        return new OpenAlAudioSink(audioThread, logger);
    }

    private void runAndWait(Runnable task) {
        try {
            audioThread.submit(task).get();
        } catch (Exception e) {
            throw new IllegalStateException("There was an error starting up OpenAlAudioEngine", e);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        runAndWait(() -> {
            alcMakeContextCurrent(0L);
            if (context != 0L) {
                alcDestroyContext(context);
            }
            if (device != 0L) {
                alcCloseDevice(device);
            }
        });

        audioThread.shutdown();
        logger.info("OpenAlAudioEngine closed");
    }
}

