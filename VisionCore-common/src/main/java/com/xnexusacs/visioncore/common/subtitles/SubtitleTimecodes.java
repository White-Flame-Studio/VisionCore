package com.xnexusacs.visioncore.common.subtitles;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SubtitleTimecodes {

    private static final Pattern TIMESTAMP = Pattern.compile("(?:(\\d+):)?(\\d{2}):(\\d{2})[.,](\\d{1,3})");

    private SubtitleTimecodes() { }

    static long parse(String raw) throws SubtitleParseException {
        Matcher matcher = TIMESTAMP.matcher(raw.trim());

        if (!matcher.find()) {
            throw new SubtitleParseException("Invalid subtitle timestamp: '" + raw + "'");
        }

        long hours = matcher.group(1) != null ? Long.parseLong(matcher.group(1)) : 0;
        long minutes = Long.parseLong(matcher.group(2));
        long seconds = Long.parseLong(matcher.group(3));
        String millisPart = matcher.group(4);
        long millis = Long.parseLong(millisPart) * pow10(3 - millisPart.length());

        return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis;
    }

    private static long pow10(int exponent) {
        long result = 1;

        for (int i = 0; i < exponent; i++) {
            result *= 10;
        }

        return result;
    }
}
