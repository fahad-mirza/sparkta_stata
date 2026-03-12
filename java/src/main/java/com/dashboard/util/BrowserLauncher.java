package com.dashboard.util;

import java.io.File;
import java.net.URI;

/**
 * Opens a local HTML file in the system's default web browser.
 * Handles Windows, macOS, and Linux environments.
 */
public class BrowserLauncher {

    /**
     * Opens a file path in the default browser.
     *
     * @param filePath  Absolute path to the HTML file
     */
    public static void open(String filePath) throws Exception {
        File file = new File(filePath);
        URI uri = file.toURI();
        openUri(uri);
    }

    /**
     * Opens a URI in the default browser.
     */
    public static void openUri(URI uri) throws Exception {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            // Windows
            Runtime.getRuntime().exec(
                new String[]{"rundll32", "url.dll,FileProtocolHandler", uri.toString()}
            );
        } else if (os.contains("mac")) {
            // macOS
            Runtime.getRuntime().exec(new String[]{"open", uri.toString()});
        } else {
            // Linux / Unix -- try xdg-open first, then fallbacks
            String[] browsers = {"xdg-open", "firefox", "chromium-browser",
                                 "google-chrome", "sensible-browser"};
            boolean opened = false;
            for (String browser : browsers) {
                try {
                    Runtime.getRuntime().exec(new String[]{browser, uri.toString()});
                    opened = true;
                    break;
                } catch (Exception e) {
                    // Try next browser
                }
            }
            if (!opened) {
                throw new Exception(
                    "Could not open browser automatically. " +
                    "Please open this file manually: " + uri.toString()
                );
            }
        }
    }
}
