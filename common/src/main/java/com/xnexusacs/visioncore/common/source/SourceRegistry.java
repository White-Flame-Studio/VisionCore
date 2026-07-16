package com.xnexusacs.visioncore.common.source;

import com.xnexusacs.visioncore.common.exception.UnsupportedSourceException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SourceRegistry {

    private final List<MediaSource> sources = new CopyOnWriteArrayList<>();

    public synchronized void register(MediaSource source) {
        sources.add(source);
        sources.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
    }

    public void unregister(MediaSource source) {
        sources.remove(source);
    }

    public ResolvedMedia resolve(URI uri) throws MediaResolveException {
        for (MediaSource source : sources) {
            if (source.supports(uri)) return source.resolve(uri);
        }

        throw new UnsupportedSourceException(uri);
    }

    public List<MediaSource> snapshot() {
        return List.copyOf(sources);
    }
}
