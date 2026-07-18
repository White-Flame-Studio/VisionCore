package com.xnexusacs.visioncore.client.audio;

import com.xnexusacs.visioncore.common.audio.AudioBuffer;
import com.xnexusacs.visioncore.common.audio.AudioSampleSink;
import com.xnexusacs.visioncore.common.log.MediaLogger;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import static org.lwjgl.openal.AL10.*;

public final class OpenAlAudioSink implements AudioSampleSink, AutoCloseable {

    private static final int QUEUED_BUFFERS = 4;

    private final ExecutorService audioThread;
    private final MediaLogger logger;

    private int sourceId = -1;
    private final Deque<Integer> freeBuffers = new ArrayDeque<>();
    private boolean initialized = false;
    private volatile float pendingVolume = 1.0f;

    OpenAlAudioSink(ExecutorService audioThread, MediaLogger logger) {
        this.audioThread = audioThread;
        this.logger = logger;
    }

    public void setVolume(float volume) {
        float clamped = Math.max(0f, volume);
        this.pendingVolume = clamped;
        audioThread.execute(() -> {
            if (initialized) {
                alSourcef(sourceId, AL_GAIN, clamped);
            }
        });
    }

    public float volume() {
        return pendingVolume;
    }

    @Override
    public void onSamples(AudioBuffer buffer) {
        ByteBuffer copy = ByteBuffer.allocateDirect(buffer.data().remaining());
        copy.put(buffer.data());
        copy.flip();
        int sampleRate = buffer.format().sampleRate();
        int channels = buffer.format().channels();

        audioThread.execute(() -> uploadAndQueue(copy, sampleRate, channels));
    }

    private void uploadAndQueue(ByteBuffer pcm, int sampleRate, int channels) {
        if (!initialized) {
            sourceId = alGenSources();
            alSourcef(sourceId, AL_GAIN, pendingVolume);
            for (int i = 0; i < QUEUED_BUFFERS; i++) {
                freeBuffers.push(alGenBuffers());
            }
            initialized = true;
        }

        int processed = alGetSourcei(sourceId, AL_BUFFERS_PROCESSED);
        for (int i = 0; i < processed; i++) {
            freeBuffers.push(alSourceUnqueueBuffers(sourceId));
        }

        if (freeBuffers.isEmpty()) {
            logger.warn("OpenAlAudioSink has no buffers, audio block dropped");
            return;
        }

        int bufferId = freeBuffers.pop();
        int format = channels == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16;
        alBufferData(bufferId, format, pcm, sampleRate);
        alSourceQueueBuffers(sourceId, bufferId);

        int state = alGetSourcei(sourceId, AL_SOURCE_STATE);
        if (state != AL_PLAYING) {
            alSourcePlay(sourceId);
        }
    }

    @Override
    public void close() {
        audioThread.execute(() -> {
            if (!initialized) {
                return;
            }

            alSourceStop(sourceId);
            int queued = alGetSourcei(sourceId, AL_BUFFERS_QUEUED);

            for (int i = 0; i < queued; i++) {
                alSourceUnqueueBuffers(sourceId);
            }

            alDeleteSources(sourceId);

            while (!freeBuffers.isEmpty()) {
                alDeleteBuffers(freeBuffers.pop());
            }

            initialized = false;
        });
    }
}
