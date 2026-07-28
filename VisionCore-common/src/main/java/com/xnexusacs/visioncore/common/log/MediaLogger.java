package com.xnexusacs.visioncore.common.log;

public interface MediaLogger {

    void debug(String message, Object... args);

    void info(String message, Object... args);

    void warn(String message, Object... args);

    void warn(String message, Throwable cause);

    void error(String message, Object... args);

    void error(String message, Throwable cause);
}
