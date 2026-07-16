package com.xnexusacs.visioncore.common.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

public final class MemoryLruCache<V> implements MediaCache<V> {

    private final int maxEntries;
    private final ReentrantLock lock = new ReentrantLock();
    private final LinkedHashMap<CacheKey, V> map;

    public MemoryLruCache(int maxEntries) {
        this.maxEntries = maxEntries;
        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CacheKey, V> eldest) {
                return size() > MemoryLruCache.this.maxEntries;
            }
        };
    }

    @Override
    public Optional<V> get(CacheKey key) {
        lock.lock();
        try {
            return Optional.ofNullable(map.get(key));
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public void put(CacheKey key, V value) {
        lock.lock();
        try {
            map.put(key, value);
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public void invalidate(CacheKey key) {
        lock.lock();
        try {
            map.remove(key);
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            map.clear();
        }
        finally {
            lock.unlock();
        }
    }
}
