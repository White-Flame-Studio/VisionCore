package com.xnexusacs.visioncore.resource;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ModResourceExtractor {

    private static final String RESOURCE_BASE = "assets/visioncore/videos/";

    private ModResourceExtractor() { }

    public static URI extractToCache(String fileName) {
        String resourcePath = RESOURCE_BASE + fileName;
        Path cacheDir = FabricLoader.getInstance().getGameDir().resolve("visioncore-cache").resolve("bundled-media");
        Path target = cacheDir.resolve(fileName);

        try {
            if (Files.exists(target) && Files.size(target) > 0) {
                return target.toUri();
            }
            Files.createDirectories(cacheDir);
            try (InputStream in = ModResourceExtractor.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (in == null) {
                    throw new IllegalArgumentException("The bundled resource doesn't exist: " + resourcePath);
                }
                Path temp = target.resolveSibling(target.getFileName() + ".tmp");
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            return target.toUri();
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't find bundled resource '" + resourcePath + "' in disk", e);
        }
    }
}
