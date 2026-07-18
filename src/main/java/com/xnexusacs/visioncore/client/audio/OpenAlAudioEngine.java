package com.xnexusacs.visioncore.client.audio;

import com.xnexusacs.visioncore.common.log.MediaLogger;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC11;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.ALUtil;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.lwjgl.openal.AL10.AL_GAIN;
import static org.lwjgl.openal.AL10.alListenerf;
import static org.lwjgl.openal.ALC10.*;

public final class OpenAlAudioEngine implements AutoCloseable {

    private final MediaLogger logger;
    private final ExecutorService audioThread;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private long device;
    private long context;
    private volatile float masterVolume = 1.0f;

    public OpenAlAudioEngine(MediaLogger logger) {
        this(logger, null);
    }

    public OpenAlAudioEngine(MediaLogger logger, String deviceName) {
        this.logger = logger;
        this.audioThread = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "visioncore-openal");
            thread.setDaemon(true);
            return thread;
        });

        runAndWait(() -> {
            device = alcOpenDevice(deviceName);

            if (device == 0L) {
                if (deviceName != null) {
                    logger.warn("Couldn't open OpenAL device '{}', trying with system default device", deviceName);
                    device = alcOpenDevice((ByteBuffer) null);
                }
                if (device == 0L) {
                    throw new IllegalStateException("Couldn't open any OpenAL device");
                }
            }

            ALCCapabilities deviceCaps = ALC.createCapabilities(device);
            context = alcCreateContext(device, (IntBuffer) null);

            if (context == 0L) {
                throw new IllegalStateException("Error creating OpenAL context");
            }

            alcMakeContextCurrent(context);
            AL.createCapabilities(deviceCaps);
            alListenerf(AL_GAIN, masterVolume);
        });

        logger.info("OpenAlAudioEngine started (device: {})", deviceName != null ? deviceName : "system default");
    }

    public static List<String> listOutputDevices() {
        try {
            List<String> devices = ALUtil.getStringList(0L, ALC11.ALC_ALL_DEVICES_SPECIFIER);
            return devices != null ? devices : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public OpenAlAudioSink newSink() {
        return new OpenAlAudioSink(audioThread, logger);
    }

    public void setMasterVolume(float volume) {
        float clamped = Math.max(0f, volume);
        this.masterVolume = clamped;
        audioThread.execute(() -> alListenerf(AL_GAIN, clamped));
    }

    public float masterVolume() {
        return masterVolume;
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

