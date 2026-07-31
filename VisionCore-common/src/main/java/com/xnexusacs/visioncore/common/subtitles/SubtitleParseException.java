package com.xnexusacs.visioncore.common.subtitles;

import com.xnexusacs.visioncore.common.exception.MediaException;

public class SubtitleParseException extends MediaException {

    public SubtitleParseException(String message) {
        super(message);
    }

    public SubtitleParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
