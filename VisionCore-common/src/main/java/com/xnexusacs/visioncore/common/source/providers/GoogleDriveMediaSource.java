package com.xnexusacs.visioncore.common.source.providers;

import com.xnexusacs.visioncore.common.net.HttpClientProvider;
import com.xnexusacs.visioncore.common.source.MediaResolveException;
import com.xnexusacs.visioncore.common.source.MediaSource;
import com.xnexusacs.visioncore.common.source.ResolvedMedia;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GoogleDriveMediaSource implements MediaSource {

    private static final Pattern FILE_ID_PATTERN = Pattern.compile("(?:/d/|[?&]id=)([a-zA-Z0-9_-]{10,})");

    private final HttpClientProvider http;

    public GoogleDriveMediaSource(HttpClientProvider http) {
        this.http = http;
    }

    @Override
    public String id() {
        return "google-drive";
    }

    @Override
    public boolean supports(URI uri) {
        String host = uri.getHost();
        return host != null && host.endsWith("drive.google.com");
    }

    @Override
    public ResolvedMedia resolve(URI uri) throws MediaResolveException {
        String fileId = extractFileId(uri).orElseThrow(() -> new MediaResolveException(uri, "Couldn't extract file ID from Google Drive URL"));

        URI directUri = URI.create("https://drive.usercontent.google.com/download?id=" + fileId + "&export=download&confirm=t");

        try {
            HttpRequest headRequest = http.newRequest(directUri).method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<Void> response = http.raw().send(headRequest, HttpResponse.BodyHandlers.discarding());
            String contentType = response.headers().firstValue("content-type").orElse("");

            if (contentType.startsWith("text/html")) {
                throw new MediaResolveException(uri, "Google Drive returned a HTML page instead of the file, probably requires additional permissions or exceeds the direct download limit without a session.");
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            throw new MediaResolveException(uri, e);
        }

        return ResolvedMedia.of(directUri);
    }

    private static Optional<String> extractFileId(URI uri) {
        Matcher matcher = FILE_ID_PATTERN.matcher(uri.toString());
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
