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

    private static final int QUEUED_BUFFERS = 96;
    private static final int SCRATCH_POOL_SIZE = QUEUED_BUFFERS + 4;
    private static final int MIN_BUFFERS_TO_START = 4;

    private final ExecutorService audioThread;
    private final MediaLogger logger;

    private int sourceId = -1;
    private final Deque<Integer> freeBuffers = new ArrayDeque<>();
    private boolean initialized = false;
    private boolean priming = true;

    private final Object scratchLock = new Object();
    private final Deque<ByteBuffer> scratchPool = new ArrayDeque<>();

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
                checkAlError("alSourcef(AL_GAIN)");
            }
        });
    }

    public float volume() {
        return pendingVolume;
    }

    @Override
    public void onSamples(AudioBuffer buffer) {
        int required = buffer.data().remaining();
        ByteBuffer copy = acquireScratch(required);
        copy.clear();
        copy.put(buffer.data());
        copy.flip();
        int sampleRate = buffer.format().sampleRate();
        int channels = buffer.format().channels();

        audioThread.execute(() -> {
            uploadAndQueue(copy, sampleRate, channels);
            releaseScratch(copy);
        });
    }

    private ByteBuffer acquireScratch(int minCapacity) {
        synchronized (scratchLock) {
            ByteBuffer candidate = scratchPool.poll();
            if (candidate != null && candidate.capacity() >= minCapacity) {
                return candidate;
            }
        }

        return ByteBuffer.allocateDirect(minCapacity);
    }

    private void releaseScratch(ByteBuffer buffer) {
        synchronized (scratchLock) {
            if (scratchPool.size() < SCRATCH_POOL_SIZE) {
                scratchPool.push(buffer);
            }
        }
    }

    private void uploadAndQueue(ByteBuffer pcm, int sampleRate, int channels) {
        if (!initialized) {
            sourceId = alGenSources();
            checkAlError("alGenSources");
            alSourcef(sourceId, AL_GAIN, pendingVolume);
            checkAlError("alSourcef(AL_GAIN) init");

            for (int i = 0; i < QUEUED_BUFFERS; i++) {
                int bufId = alGenBuffers();
                checkAlError("alGenBuffers");
                freeBuffers.push(bufId);
            }

            initialized = true;
            priming = true;
            logger.info("OpenAlAudioSink started: sourceId={}, {} generated buffers, expected format sampleRate={} channels={}", sourceId, QUEUED_BUFFERS, sampleRate, channels);
        }

        int processed = alGetSourcei(sourceId, AL_BUFFERS_PROCESSED);
        checkAlError("alGetSourcei(AL_BUFFERS_PROCESSED)");
        for (int i = 0; i < processed; i++) {
            int unqueued = alSourceUnqueueBuffers(sourceId);
            checkAlError("alSourceUnqueueBuffers");
            freeBuffers.push(unqueued);
        }

        int sourceState = alGetSourcei(sourceId, AL_SOURCE_STATE);
        checkAlError("alGetSourcei(AL_SOURCE_STATE) diag");

        if (sourceState == AL_STOPPED && !priming) {
            priming = true;
        }

        if (freeBuffers.isEmpty()) {
            logger.warn("OpenAlAudioSink has no buffers, audio block dropped");
            return;
        }

        int bufferId = freeBuffers.pop();
        int format = channels == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16;
        alBufferData(bufferId, format, pcm, sampleRate);
        alSourceQueueBuffers(sourceId, bufferId);
        checkAlError("alSourceQueueBuffers");

        int queuedNow = QUEUED_BUFFERS - freeBuffers.size();
        if (priming && queuedNow >= MIN_BUFFERS_TO_START) {
            priming = false;
            alSourcePlay(sourceId);
            checkAlError("alSourcePlay (after the priming of " + queuedNow + " buffers)");
        }
    }

    private void checkAlError(String context) {
        int error = alGetError();
        if (error != AL_NO_ERROR) {
            logger.error("OpenAlAudioSink: OpenAL failed with error '{}': error code {}", context, error);
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

        synchronized (scratchLock) {
            scratchPool.clear();
        }
    }
}
