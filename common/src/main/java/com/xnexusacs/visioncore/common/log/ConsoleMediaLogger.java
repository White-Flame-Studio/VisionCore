package com.xnexusacs.visioncore.common.log;

import java.io.PrintStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class ConsoleMediaLogger implements MediaLogger {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private final String tag;

    public ConsoleMediaLogger(String tag) {
        this.tag = tag;
    }

    public ConsoleMediaLogger() {
        this("VisionCore");
    }

    @Override
    public void debug(String message, Object... args) {
        log("DEBUG", System.out, message, args);
    }

    @Override
    public void info(String message, Object... args) {
        log("INFO", System.out, message, args);
    }

    @Override
    public void warn(String message, Object... args) {
        log("WARN", System.out, message, args);
    }

    @Override
    public void warn(String message, Throwable cause) {
        log("WARN", System.out, message);
        cause.printStackTrace(System.out);
    }

    @Override
    public void error(String message, Object... args) {
        log("ERROR", System.err, message, args);
    }

    @Override
    public void error(String message, Throwable cause) {
        log("ERROR", System.err, message);
        cause.printStackTrace(System.err);
    }

    private void log(String level, PrintStream stream, String message, Object... args) {
        stream.printf("[%s] [%s] [%s] %s%n", LocalTime.now().format(TIME_FORMAT), level, tag, format(message, args));
    }

    private static String format(String pattern, Object... args) {
        if (args == null || args.length == 0) return pattern;
        StringBuilder result = new StringBuilder(pattern.length() + 16);
        int argIndex = 0, i = 0;

        while (i < pattern.length()) {
            boolean isPlaceholder = i < pattern.length() - 1 && pattern.charAt(i) == '{' && pattern.charAt(i + 1) == '}' && argIndex < args.length;
            if (isPlaceholder) {
                result.append(args[argIndex++]);
                i += 2;
            } else {
                result.append(pattern.charAt(i));
                i++;
            }
        }

        return result.toString();
    }
}
