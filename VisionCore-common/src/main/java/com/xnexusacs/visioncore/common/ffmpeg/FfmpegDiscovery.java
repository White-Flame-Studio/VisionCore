package com.xnexusacs.visioncore.common.ffmpeg;

import com.xnexusacs.visioncore.common.log.MediaLogger;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class FfmpegDiscovery {

    private final String executable;
    private final Duration timeout;
    private final MediaLogger logger;
    private volatile Boolean available;

    public FfmpegDiscovery(String executable, Duration timeout, MediaLogger logger) {
        this.executable = executable;
        this.timeout = timeout;
        this.logger = logger;
    }

    public boolean isAvailable() {
        Boolean cached = available;

        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            if (available != null) {
                return available;
            }

            available = probe();
            return available;
        }
    }

    private boolean probe() {
        Process process;

        try {
            process = new ProcessBuilder(executable, "-version").redirectErrorStream(true).start();
        } catch (IOException e) {
            logger.info("Ffmpeg executable '{}' not found, optional media enhancement stays disabled", executable);
            return false;
        }

        try {
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);

            if (!finished) {
                process.destroyForcibly();
                logger.warn("Ffmpeg probe ('{} -version') timed out after {}ms, treating Ffmpeg as unavailable", executable, timeout.toMillis());
                return false;
            }

            if (process.exitValue() != 0) {
                logger.warn("Ffmpeg probe ('{} -version') exited with code {}, treating Ffmpeg as unavailable", executable, process.exitValue());
                return false;
            }

            logger.info("Ffmpeg found ('{}'), optional media enhancement is available", executable);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return false;
        }
    }
}
