package com.xnexusacs.visioncore.common.player.vlc;

import com.xnexusacs.visioncore.common.exception.NativeLibraryException;
import com.xnexusacs.visioncore.common.log.MediaLogger;
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery;
import java.util.concurrent.*;

public class VlcNativeDiscovery {

    private static final long DISCOVERY_TIMEOUT_SECONDS = 10;

    private final MediaLogger logger;
    private volatile boolean discovered = false;

    public VlcNativeDiscovery(MediaLogger logger) {
        this.logger = logger;
    }

    public synchronized void ensureAvailable() {
        if (discovered) {
            return;
        }

        LinuxVlcPluginPathFix.applyIfNeeded(logger);

        boolean found = discoverWithTimeout();

        if (!found) {
            throw new NativeLibraryException("Couldn't find libVLC in the system. Make sure VLC is installed in the system " + "via the native package manager (no Flatpak/Snap/sandboxed) and that its version is higher than " + "3.x. On Linux, install the 'vlc' package from your distro; on Windows/macOS, " + "install VLC from videolan.org.");
        }

        logger.info("libVLC found and loaded");
        discovered = true;
    }

    private boolean discoverWithTimeout() {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "visioncore-vlc-discovery");
            thread.setDaemon(true);
            return thread;
        });
        try {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> new NativeDiscovery().discover(), executor);
            return future.get(DISCOVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.warn("VLC discovery didn't end in {}s, assumed that VLC is not available", DISCOVERY_TIMEOUT_SECONDS);
            return false;
        } catch (Exception e) {
            logger.warn("VLC discovery failed with an unhandled exception, assumed that VLC is not available", e);
            return false;
        } finally {
            executor.shutdownNow();
        }
    }
}
