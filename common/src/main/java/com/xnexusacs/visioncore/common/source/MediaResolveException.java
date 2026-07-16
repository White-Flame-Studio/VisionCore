package com.xnexusacs.visioncore.common.source;

import com.xnexusacs.visioncore.common.exception.MediaException;
import java.net.URI;

public class MediaResolveException extends MediaException {

    public MediaResolveException(URI uri, String reason) {
        super("URI could not be resolved '" + uri + "': " + reason);
    }

    public MediaResolveException(URI uri, Throwable cause) {
        super("URI could not be resolved '" + uri + "'", cause);
    }
}
