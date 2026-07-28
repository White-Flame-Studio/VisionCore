package com.xnexusacs.visioncore.common.exception;

public class NativeLibraryException extends MediaException {

    public NativeLibraryException(String message) {
        super(message);
    }

    public NativeLibraryException(String message, Throwable cause) {
        super(message, cause);
    }
}
