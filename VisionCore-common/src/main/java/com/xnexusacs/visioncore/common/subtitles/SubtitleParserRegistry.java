package com.xnexusacs.visioncore.common.subtitles;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SubtitleParserRegistry {

    private final List<SubtitleParser> parsers = new CopyOnWriteArrayList<>();

    public SubtitleParserRegistry() {
        register(new SrtSubtitleParser());
        register(new VttSubtitleParser());
    }

    public void register(SubtitleParser parser) {
        parsers.add(parser);
    }

    public void unregister(SubtitleParser parser) {
        parsers.remove(parser);
    }

    public SubtitleTrack parse(String fileName, String content, String label) throws SubtitleParseException {
        for (SubtitleParser parser : parsers) {
            if (parser.supports(fileName)) {
                return parser.parse(content, label);
            }
        }

        throw new SubtitleParseException("No SubtitleParser supports the file '" + fileName + "'");
    }

    public List<SubtitleParser> snapshot() {
        return List.copyOf(parsers);
    }
}
