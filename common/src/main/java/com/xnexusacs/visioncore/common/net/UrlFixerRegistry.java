package com.xnexusacs.visioncore.common.net;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class UrlFixerRegistry {

    private final List<UrlFixer> fixers = new CopyOnWriteArrayList<>();

    public void register(UrlFixer fixer) {
        fixers.add(fixer);
    }

    public void unregister(UrlFixer fixer) {
        fixers.remove(fixer);
    }

    public URI apply(URI uri) {
        URI current = uri;

        for (UrlFixer fixer : fixers) {
            if (fixer.supports(current)) current = fixer.fix(current);
        }

        return current;
    }
}
