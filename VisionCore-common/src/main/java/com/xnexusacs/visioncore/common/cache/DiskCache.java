package com.xnexusacs.visioncore.common.cache;

import com.xnexusacs.visioncore.common.exception.MediaException;
import com.xnexusacs.visioncore.common.log.MediaLogger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class DiskCache implements MediaCache<byte[]> {

    private final Path directory;
    private final Duration ttl;
    private final MediaLogger logger;

    public DiskCache(Path directory, Duration ttl, MediaLogger logger) {
        this.directory = directory;
        this.ttl = ttl;
        this.logger = logger;
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new MediaException("Couldn't create the specified directory on disk: " + directory, e);
        }
    }

    @Override
    public Optional<byte[]> get(CacheKey key) {
        Path file = fileFor(key);
        try {
            if (!Files.exists(file)) {
                return Optional.empty();
            }

            Instant modified = Files.getLastModifiedTime(file).toInstant();

            if (Instant.now().isAfter(modified.plus(ttl))) {
                Files.deleteIfExists(file);
                return Optional.empty();
            }

            return Optional.of(Files.readAllBytes(file));
        } catch (IOException e) {
            logger.warn("Couldn't read the disk cache: " + file, e);
            return Optional.empty();
        }
    }

    @Override
    public void put(CacheKey key, byte[] value) {
        Path file = fileFor(key);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.write(temp, value, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.warn("Couldn't write the cache on disk: " + file, e);
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // Ignore.
            }
        }
    }

    @Override
    public void invalidate(CacheKey key) {
        try {
            Files.deleteIfExists(fileFor(key));
        } catch (IOException e) {
            logger.warn("There was an error invalidating the disk cache: " + key.value(), e);
        }
    }

    @Override
    public void clear() {
        try (var stream = Files.list(directory)) {
            stream.forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Ignore.
                }
            });
        } catch (IOException e) {
            logger.warn("There was an error cleaning up the disk cache: " + directory, e);
        }
    }

    private Path fileFor(CacheKey key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.value().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);

            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }

            return directory.resolve(hex + ".cache");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available in this JVM", e);
        }
    }
}
