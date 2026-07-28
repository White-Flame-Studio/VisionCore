package com.xnexusacs.visioncore.common.source.providers;

import com.xnexusacs.visioncore.common.log.MediaLogger;
import com.xnexusacs.visioncore.common.net.HttpClientProvider;
import com.xnexusacs.visioncore.common.source.MediaResolveException;
import com.xnexusacs.visioncore.common.source.MediaSource;
import com.xnexusacs.visioncore.common.source.ResolvedMedia;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SpotifyMediaSource implements MediaSource {

    private static final Pattern TRACK_ID_PATTERN = Pattern.compile("/track/([a-zA-Z0-9]{22})");
    private static final Pattern OEMBED_TITLE_PATTERN = Pattern.compile("\"title\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Duration YTDLP_TIMEOUT = Duration.ofSeconds(20);
    private static final String FORMAT_SELECTOR = "bestaudio/best";

    private final HttpClientProvider http;
    private final MediaLogger logger;

    public SpotifyMediaSource(HttpClientProvider http, MediaLogger logger) {
        this.http = http;
        this.logger = logger;
    }

    @Override
    public String id() {
        return "spotify-oembed-ytdlp";
    }

    @Override
    public boolean supports(URI uri) {
        String host = uri.getHost();

        if (host == null) {
            return false;
        }

        boolean spotifyHost = host.equals("open.spotify.com") || host.endsWith(".open.spotify.com");
        return spotifyHost && TRACK_ID_PATTERN.matcher(uri.getPath()).find();
    }

    @Override
    public ResolvedMedia resolve(URI uri) throws MediaResolveException {
        String title = fetchTrackTitle(uri);
        return searchAndResolveOnYoutube(uri, title);
    }

    private String fetchTrackTitle(URI trackUri) throws MediaResolveException {
        String encoded = URLEncoder.encode(trackUri.toString(), StandardCharsets.UTF_8);
        URI oembedUri = URI.create("https://open.spotify.com/oembed?url=" + encoded);

        HttpRequest request = http.newRequest(oembedUri).GET().build();
        HttpResponse<String> response;
        try {
            response = http.raw().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new MediaResolveException(trackUri, e);
        }

        if (response.statusCode() != 200) {
            throw new MediaResolveException(trackUri, "Spotify oEmbed returned status code " + response.statusCode());
        }

        Matcher matcher = OEMBED_TITLE_PATTERN.matcher(response.body());

        if (!matcher.find()) {
            throw new MediaResolveException(trackUri, "Couldn't extract title from Spotify oEmbed response");
        }

        return unescapeJson(matcher.group(1));
    }

    private ResolvedMedia searchAndResolveOnYoutube(URI originalUri, String title) throws MediaResolveException {
        String searchQuery = "ytsearch1:" + title + " audio";

        List<String> command = List.of("yt-dlp", "-f", FORMAT_SELECTOR, "--print", "%(title)s", "--print", "urls", searchQuery);

        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException e) {
            throw new MediaResolveException(originalUri, "Unable to find 'yt-dlp' executable in the system PATH. " + "Install it using ('pipx install yt-dlp', or through your distro package) to play Spotify URLs.");
        }

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Thread stdoutReader = new Thread(() -> appendAll(process.getInputStream(), stdout), "ytdlp-spotify-stdout");
        Thread stderrReader = new Thread(() -> appendAll(process.getErrorStream(), stderr), "ytdlp-spotify-stderr");
        stdoutReader.start();
        stderrReader.start();

        boolean finished;
        try {
            finished = process.waitFor(YTDLP_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new MediaResolveException(originalUri, e);
        }

        if (!finished) {
            process.destroyForcibly();
            throw new MediaResolveException(originalUri, "yt-dlp took too long to resolve the URL (timeout of " + YTDLP_TIMEOUT.toSeconds() + "s)");
        }

        joinQuietly(stdoutReader);
        joinQuietly(stderrReader);

        if (process.exitValue() != 0) {
            logger.warn("yt-dlp finished with code {} for '{}'. stderr: {}", process.exitValue(), title, stderr.toString().trim());
            throw new MediaResolveException(originalUri, "No results were found on YouTube for the Spotify track '" + title + "'");
        }

        String[] lines = stdout.toString().strip().split("\\R", 2);

        if (lines.length < 2 || lines[1].isBlank()) {
            throw new MediaResolveException(originalUri, "yt-dlp didn't return a playable stream URL for '" + title + "'");
        }

        String resolvedTitle = lines[0].isBlank() ? title : lines[0].trim();
        URI playableUri = URI.create(lines[1].trim());

        return new ResolvedMedia(playableUri, resolvedTitle, -1, null, false);
    }

    private static String unescapeJson(String raw) {
        return raw.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\/", "/");
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
