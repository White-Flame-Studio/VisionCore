package com.xnexusacs.visioncore.common.cache;

import java.util.Optional;

public interface MediaCache<V> {

    Optional<V> get(CacheKey key);

    void put(CacheKey key, V value);

    void invalidate(CacheKey key);

    void clear();
}
