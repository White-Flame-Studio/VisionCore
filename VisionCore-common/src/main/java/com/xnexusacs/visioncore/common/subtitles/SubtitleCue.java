package com.xnexusacs.visioncore.common.subtitles;

import java.util.regex.Pattern;

public record SubtitleCue(long startMillis, long endMillis, String text) {

    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");

    public SubtitleCue {
        if (endMillis < startMillis) {
            throw new IllegalArgumentException("endMillis (" + endMillis + ") can't be before startMillis (" + startMillis + ")");
        }
    }

    public long durationMillis() {
        return endMillis - startMillis;
    }

    public boolean isActiveAt(long timeMillis) {
        return timeMillis >= startMillis && timeMillis < endMillis;
    }

    public String plainText() {
        return TAG_PATTERN.matcher(text).replaceAll("");
    }
}
