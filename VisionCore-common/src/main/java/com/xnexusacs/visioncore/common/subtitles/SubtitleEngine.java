package com.xnexusacs.visioncore.common.subtitles;

import com.xnexusacs.visioncore.common.log.MediaLogger;
import com.xnexusacs.visioncore.common.player.MediaPlayerHandle;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public final class SubtitleEngine {

    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(200);

    private final SubtitleParserRegistry parsers;
    private final MediaLogger logger;

    public SubtitleEngine(MediaLogger logger) {
        this(new SubtitleParserRegistry(), logger);
    }

    public SubtitleEngine(SubtitleParserRegistry parsers, MediaLogger logger) {
        this.parsers = parsers;
        this.logger = logger;
    }

    public SubtitleParserRegistry parsers() {
        return parsers;
    }

    public SubtitleTrack load(Path file) throws SubtitleParseException {
        return load(file, StandardCharsets.UTF_8);
    }

    public SubtitleTrack load(Path file, Charset charset) throws SubtitleParseException {
        String content;

        try {
            content = Files.readString(file, charset);
        } catch (IOException e) {
            throw new SubtitleParseException("Couldn't read subtitle file: " + file, e);
        }

        String fileName = file.getFileName().toString();
        return parsers.parse(fileName, content, fileName);
    }

    public SubtitleTrack parse(String fileName, String content) throws SubtitleParseException {
        return parsers.parse(fileName, content, fileName);
    }

    public SubtitleTrackController attach(MediaPlayerHandle handle, SubtitleTrack track, SubtitleSink sink) {
        return attach(handle, track, sink, DEFAULT_POLL_INTERVAL);
    }

    public SubtitleTrackController attach(MediaPlayerHandle handle, SubtitleTrack track, SubtitleSink sink, Duration pollInterval) {
        SubtitleTrackController controller = new SubtitleTrackController(handle, track, sink, pollInterval, logger);
        controller.start();
        return controller;
    }
}
