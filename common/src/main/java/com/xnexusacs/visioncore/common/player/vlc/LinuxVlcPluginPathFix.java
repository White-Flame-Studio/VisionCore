package com.xnexusacs.visioncore.common.player.vlc;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.xnexusacs.visioncore.common.log.MediaLogger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

final class LinuxVlcPluginPathFix {

    private static final List<String> COMMON_PLUGIN_PATHS = List.of(
            "/usr/lib/vlc/plugins",
            "/usr/lib64/vlc/plugins",
            "/usr/lib/x86_64-linux-gnu/vlc/plugins",
            "/usr/lib/i386-linux-gnu/vlc/plugins",
            "/usr/local/lib/vlc/plugins"
    );

    private interface CLibrary extends Library {
        CLibrary INSTANCE = Native.load("c", CLibrary.class);

        int setenv(String name, String value, int overwrite);
    }

    private LinuxVlcPluginPathFix() { }

    static void applyIfNeeded(MediaLogger logger) {
        if (!Platform.isLinux()) {
            return;
        }

        if (System.getenv("VLC_PLUGIN_PATH") != null) {
            logger.debug("VLC_PLUGIN_PATH is already defined");
            return;
        }

        for (String candidate : COMMON_PLUGIN_PATHS) {
            Path path = Paths.get(candidate);
            if (Files.isDirectory(path)) {
                int result = CLibrary.INSTANCE.setenv("VLC_PLUGIN_PATH", candidate, 1);
                if (result == 0) {
                    logger.info("VLC_PLUGIN_PATH setted up in '{}' (autodetected)", candidate);
                } else {
                    logger.warn("setenv(VLC_PLUGIN_PATH) returned error code {}", result);
                }
                return;
            }
        }

        logger.warn("Error searching up for the VLC plugins path.");
    }
}
