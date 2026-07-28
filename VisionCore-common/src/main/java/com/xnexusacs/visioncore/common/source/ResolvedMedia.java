package com.xnexusacs.visioncore.common.source;

import java.net.URI;

public record ResolvedMedia(URI playableUri, String title, long durationMillis, URI thumbnailUri, boolean live) {

    public static ResolvedMedia of(URI playableUri) {
        return new ResolvedMedia(playableUri, null, -1, null, false);
    }
}
