package com.xnexusacs.visioncore.common.exception;

import java.net.URI;

public class UnsupportedSourceException extends MediaException {

    public UnsupportedSourceException(URI uri) {
        super("No MediaSource supports this URI: " + uri);
    }
}
