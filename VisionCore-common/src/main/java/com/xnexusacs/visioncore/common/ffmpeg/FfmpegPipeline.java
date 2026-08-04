package com.xnexusacs.visioncore.common.ffmpeg;

import com.xnexusacs.visioncore.common.log.MediaLogger;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class FfmpegPipeline implements AutoCloseable {

    private final Process process;
    private final OutputStream stdin;
    private final InputStream stdout;
    private final Thread stderrDrain;

    private FfmpegPipeline(Process process, String tag, MediaLogger logger) {
        this.process = process;
        this.stdin = process.getOutputStream();
        this.stdout = process.getInputStream();
        this.stderrDrain = new Thread(() -> drainStderr(process, tag, logger), "ffmpeg-stderr-" + tag);
        this.stderrDrain.setDaemon(true);
        this.stderrDrain.start();
    }

    public static FfmpegPipeline start(String executable, List<String> args, String tag, MediaLogger logger) throws IOException {
        List<String> command = new ArrayList<>(args.size() + 1);
        command.add(executable);
        command.addAll(args);
        Process process = new ProcessBuilder(command).start();
        return new FfmpegPipeline(process, tag, logger);
    }

    public OutputStream stdin() {
        return stdin;
    }

    public InputStream stdout() {
        return stdout;
    }

    @Override
    public void close() {
        try {
            stdin.close();
        } catch (IOException ignored) {
            // Ignore.
        }

        process.destroyForcibly();

        try {
            process.waitFor(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        stderrDrain.interrupt();
    }

    private static void drainStderr(Process process, String tag, MediaLogger logger) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    logger.warn("Ffmpeg ({}): {}", tag, line);
                }
            }
        } catch (IOException ignored) {
            // Ignore.
        }
    }
}
