package com.xnexusacs.visioncore.common.source.providers;

import com.xnexusacs.visioncore.common.log.MediaLogger;
import com.xnexusacs.visioncore.common.source.MediaResolveException;
import com.xnexusacs.visioncore.common.source.MediaSource;
import com.xnexusacs.visioncore.common.source.ResolvedMedia;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class YtDlpMediaSource implements MediaSource {

    private static final Duration RESOLVE_TIMEOUT = Duration.ofSeconds(20);
    private static final String FORMAT_SELECTOR = "best[acodec!=none][vcodec!=none]/best";

    private final MediaLogger logger;

    public YtDlpMediaSource(MediaLogger logger) {
        this.logger = logger;
    }

    @Override
    public String id() {
        return "youtube-ytdlp";
    }

    @Override
    public boolean supports(URI uri) {
        String host = uri.getHost();

        if (host == null) {
            return false;
        }

        return host.endsWith("youtube.com") || host.equals("youtu.be");
    }

    @Override
    public ResolvedMedia resolve(URI uri) throws MediaResolveException {
        List<String> command = List.of("yt-dlp", "--no-playlist", "-f", FORMAT_SELECTOR, "--print", "%(title)s", "--print", "urls", uri.toString());

        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException e) {
            throw new MediaResolveException(uri, "Unable to find 'yt-dlp' executable in the system PATH. " + "Install it using ('pipx install yt-dlp', or through your distro package) to play YouTube URLs.");
        }

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Thread stdoutReader = new Thread(() -> appendAll(process.getInputStream(), stdout), "ytdlp-stdout");
        Thread stderrReader = new Thread(() -> appendAll(process.getErrorStream(), stderr), "ytdlp-stderr");
        stdoutReader.start();
        stderrReader.start();

        boolean finished;
        try {
            finished = process.waitFor(RESOLVE_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new MediaResolveException(uri, e);
        }

        if (!finished) {
            process.destroyForcibly();
            throw new MediaResolveException(uri, "yt-dlp took too long to resolve the URL (timeout of " + RESOLVE_TIMEOUT.toSeconds() + "s)");
        }

        joinQuietly(stdoutReader);
        joinQuietly(stderrReader);

        if (process.exitValue() != 0) {
            logger.warn("yt-dlp finished with code {} for '{}'. stderr: {}", process.exitValue(), uri, stderr.toString().trim());
            throw new MediaResolveException(uri, "yt-dlp couldn't resolve the URL (video deleted, private, or age restricted in region)");
        }

        String[] lines = stdout.toString().strip().split("\\R", 2);

        if (lines.length < 2 || lines[1].isBlank()) {
            throw new MediaResolveException(uri, "yt-dlp didn't return a playable stream URL");
        }

        String title = lines[0].isBlank() ? null : lines[0].trim();
        URI playableUri = URI.create(lines[1].trim());

        return new ResolvedMedia(playableUri, title, -1, null, false);
    }

    private static void appendAll(InputStream stream, StringBuilder out) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append('\n');
            }
        } catch (IOException ignored) {
            // Ignore.
        }
    }

    private static void joinQuietly(Thread thread) {
        try {
            thread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
