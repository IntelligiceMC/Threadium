package com.itarqos.threadium.util;

import com.itarqos.threadium.Threadium;
import org.slf4j.Logger;

public final class ThreadiumLog {
    private static final Logger L = Threadium.LOGGER;
    private static boolean verboseLogging = false;

    private ThreadiumLog() {}

    public static void setVerbose(boolean verbose) {
        verboseLogging = verbose;
        info("Verbose logging %s", verbose ? "enabled" : "disabled");
    }

    public static boolean isVerbose() {
        return verboseLogging;
    }

    public static void debug(String msg, Object... args) {
        if (verboseLogging) {
            if (args != null && args.length > 0) {
                L.info("[DEBUG] " + String.format(msg, args));
            } else {
                L.info("[DEBUG] " + msg);
            }
        }
    }

    public static void info(String msg, Object... args) {
        if (args != null && args.length > 0) {
            L.info(String.format(msg, args));
        } else {
            L.info(msg);
        }
    }

    public static void warn(String msg, Object... args) {
        if (args != null && args.length > 0) {
            L.warn(String.format(msg, args));
        } else {
            L.warn(msg);
        }
    }

    public static void error(String msg, Object... args) {
        if (args != null && args.length > 0) {
            L.error(String.format(msg, args));
        } else {
            L.error(msg);
        }
    }

    public static void error(String msg, Throwable t) {
        L.error(msg, t);
    }
}
