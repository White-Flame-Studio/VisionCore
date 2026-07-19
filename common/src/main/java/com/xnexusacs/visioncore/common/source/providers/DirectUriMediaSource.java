package com.xnexusacs.visioncore.common.source.providers;

import com.xnexusacs.visioncore.common.source.MediaSource;
import com.xnexusacs.visioncore.common.source.ResolvedMedia;
import java.net.URI;

public final class DirectUriMediaSource implements MediaSource {

    @Override
    public String id() {
        return "direct-uri";
    }

    @Override
    public boolean supports(URI uri) {
        return true;
    }

    @Override
    public ResolvedMedia resolve(URI uri) {
        return ResolvedMedia.of(uri);
    }

    @Override
    public int priority() {
        return Integer.MIN_VALUE;
    }
}
