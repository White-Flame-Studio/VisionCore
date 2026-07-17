package com.xnexusacs.visioncore.client.log;

import com.xnexusacs.visioncore.common.log.MediaLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Slf4jMediaLogger implements MediaLogger {

    private final Logger delegate;

    public Slf4jMediaLogger(String name) {
        this.delegate = LoggerFactory.getLogger(name);
    }

    @Override
    public void debug(String message, Object... args) {
        delegate.debug(message, args);
    }

    @Override
    public void info(String message, Object... args) {
        delegate.info(message, args);
    }

    @Override
    public void warn(String message, Object... args) {
        delegate.warn(message, args);
    }

    @Override
    public void warn(String message, Throwable cause) {
        delegate.warn(message, cause);
    }

    @Override
    public void error(String message, Object... args) {
        delegate.error(message, args);
    }

    @Override
    public void error(String message, Throwable cause) {
        delegate.error(message, cause);
    }
}
