package com.xnexusacs.visioncore.common.source;

import java.net.URI;

public interface MediaSource {

    String id();

    boolean supports(URI uri);

    ResolvedMedia resolve(URI uri) throws MediaResolveException;

    default int priority() {
        return 0;
    }
}
