package com.xnexusacs.visioncore.common.subtitles;

import java.util.List;
import java.util.Locale;

public final class SrtSubtitleParser implements SubtitleParser {

    @Override
    public String id() {
        return "srt";
    }

    @Override
    public boolean supports(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".srt");
    }

    @Override
    public SubtitleTrack parse(String content, String label) throws SubtitleParseException {
        List<SubtitleCue> cues = SubtitleBlockParser.parseBlocks(content, block -> false);

        if (cues.isEmpty()) {
            throw new SubtitleParseException("No valid subtitle cues found while parsing SRT content" + (label != null ? " ('" + label + "')" : ""));
        }

        return new SubtitleTrack(label, cues);
    }
}
