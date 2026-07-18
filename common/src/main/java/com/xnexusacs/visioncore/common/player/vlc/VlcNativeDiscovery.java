package com.xnexusacs.visioncore.common.player.vlc;

import com.xnexusacs.visioncore.common.exception.NativeLibraryException;
import com.xnexusacs.visioncore.common.log.MediaLogger;
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery;

public class VlcNativeDiscovery {

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

        boolean found = new NativeDiscovery().discover();

        if (!found) {
            throw new NativeLibraryException("Couldn't find libVLC in the system. Make sure VLC is installed in the system " + "via the native package manager (no Flatpak/Snap/sandboxed) and that its version is higher than " + "3.x. On Linux, install the 'vlc' package from your distro; on Windows/macOS, " + "install VLC from videolan.org.");
        }

        logger.info("libVLC found and loaded");
        discovered = true;
    }
}
