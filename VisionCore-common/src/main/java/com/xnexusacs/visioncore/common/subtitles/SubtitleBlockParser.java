package com.xnexusacs.visioncore.common.subtitles;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

final class SubtitleBlockParser {

    private SubtitleBlockParser() { }

    static List<SubtitleCue> parseBlocks(String content, Predicate<String> skipBlock) throws SubtitleParseException {
        String normalized = content.replace("\r\n", "\n").replace("\r", "\n");
        String[] blocks = normalized.split("\\n\\s*\\n");
        List<SubtitleCue> cues = new ArrayList<>();

        for (String block : blocks) {
            String trimmed = block.trim();

            if (trimmed.isEmpty() || skipBlock.test(trimmed)) {
                continue;
            }

            String[] lines = trimmed.split("\n");
            int timingLineIndex = -1;

            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains("-->")) {
                    timingLineIndex = i;
                    break;
                }
            }

            if (timingLineIndex == -1) {
                continue;
            }

            String[] timing = lines[timingLineIndex].split("-->");

            if (timing.length != 2) {
                continue;
            }

            long start = SubtitleTimecodes.parse(timing[0]);
            long end = SubtitleTimecodes.parse(timing[1].trim().split("\\s+")[0]);

            StringBuilder text = new StringBuilder();
            for (int i = timingLineIndex + 1; i < lines.length; i++) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(lines[i]);
            }

            if (text.isEmpty()) {
                continue;
            }

            cues.add(new SubtitleCue(start, end, text.toString()));
        }

        return cues;
    }
}
