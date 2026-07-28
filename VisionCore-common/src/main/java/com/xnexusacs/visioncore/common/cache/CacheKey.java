package com.xnexusacs.visioncore.common.cache;

import java.util.Locale;

public record CacheKey(String value) {

    public static CacheKey of(String rawValue) {
        return new CacheKey(rawValue.trim().toLowerCase(Locale.ROOT));
    }
}
