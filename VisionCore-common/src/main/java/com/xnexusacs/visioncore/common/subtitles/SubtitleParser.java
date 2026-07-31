package com.xnexusacs.visioncore.common.subtitles;

public interface SubtitleParser {

    String id();

    boolean supports(String fileName);

    SubtitleTrack parse(String content, String label) throws SubtitleParseException;
}
