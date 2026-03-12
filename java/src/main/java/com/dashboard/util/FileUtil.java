package com.dashboard.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Utility class for file operations.
 */
public class FileUtil {

    /**
     * Writes content to a specific file path.
     *
     * @param path    Target file path
     * @param content File content (HTML string)
     */
    /**
     * Resolves a user-supplied export path to an absolute Path.
     * Handles:
     *   - Unix ~ expansion (not supported by Windows Paths.get natively)
     *   - Mixed separators (forward slash on Windows)
     *   - Relative paths (resolved against working directory)
     * v2.0.6
     */
    public static Path resolveExportPath(String rawPath) {
        String p = rawPath.trim();
        // Expand leading ~ to the Java user.home property (works on all platforms)
        if (p.startsWith("~/") || p.startsWith("~\\") || p.equals("~")) {
            p = System.getProperty("user.home") + p.substring(1);
        }
        // Normalise separators to the platform default
        p = p.replace("/", File.separator).replace("\\", File.separator);
        return Paths.get(p).toAbsolutePath().normalize();
    }

    public static void writeFile(String path, String content) throws IOException {
        Path filePath = resolveExportPath(path);
        // Create parent directories if they do not exist
        if (filePath.getParent() != null) {
            Files.createDirectories(filePath.getParent());
        }
        Files.write(filePath, content.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Returns the resolved absolute path string for display to the user.
     * Call this before writeFile() to show where the file will be written.
     * v2.0.6
     */
    public static String resolvedPathString(String rawPath) {
        return resolveExportPath(rawPath).toString();
    }

    /**
     * Writes HTML content to a temporary file and returns its absolute path.
     * The file is created in the system temp directory.
     *
     * @param html   HTML content to write
     * @return       Absolute path to the temporary file
     */
    public static String writeTempHtml(String html) throws IOException {
        File tempFile = File.createTempFile("stata_dashboard_", ".html");
        tempFile.deleteOnExit();
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
            writer.write(html);
        }
        return tempFile.getAbsolutePath();
    }
}
