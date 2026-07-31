package com.xnexusacs.visioncore.common.subtitles;

import java.util.List;
import java.util.Locale;

public final class VttSubtitleParser implements SubtitleParser {

    @Override
    public String id() {
        return "vtt";
    }

    @Override
    public boolean supports(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".vtt");
    }

    @Override
    public SubtitleTrack parse(String content, String label) throws SubtitleParseException {
        List<SubtitleCue> cues = SubtitleBlockParser.parseBlocks(content, block -> block.startsWith("WEBVTT") || block.startsWith("NOTE") || block.startsWith("STYLE") || block.startsWith("REGION"));

        if (cues.isEmpty()) {
            throw new SubtitleParseException("No valid subtitle cues found while parsing WebVTT content" + (label != null ? " ('" + label + "')" : ""));
        }

        return new SubtitleTrack(label, cues);
    }
}
