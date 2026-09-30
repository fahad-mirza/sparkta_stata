package com.dashboard_test.util;

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
    /**
     * Resolves export path. Uses Stata's c(pwd) as base for relative paths.
     * Falls back to user.home if stataPwd is empty.
     * v3.6.0-s6: matches Stata graph export behaviour.
     */
    public static Path resolveExportPath(String rawPath, String stataPwd) {
        String p = rawPath.trim();
        if (p.startsWith("~/") || p.startsWith("~\\") || p.equals("~")) {
            p = System.getProperty("user.home") + p.substring(1);
        }
        p = p.replace("/", File.separator).replace("\\", File.separator);
        Path path = Paths.get(p);
        if (!path.isAbsolute()) {
            String base = (stataPwd != null && !stataPwd.trim().isEmpty())
                ? stataPwd.trim() : System.getProperty("user.home");
            path = Paths.get(base).resolve(p);
        }
        return path.normalize();
    }

    // Backward-compatible overload: uses user.home as base (safe fallback)
    public static Path resolveExportPath(String rawPath) {
        return resolveExportPath(rawPath, "");
    }
    // Overload: write file with Stata working directory for relative path resolution
    public static void writeFile(String path, String stataPwd, String content) throws IOException {
        writeAtomically(resolveExportPath(path, stataPwd), content.getBytes(StandardCharsets.UTF_8));
    }

    public static void writeFile(String path, String content) throws IOException {
        writeAtomically(resolveExportPath(path), content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * t2j fix4 (ported from Astra rc1): write bytes to a sibling temp file and then
     * atomically move it onto the target. A failure mid-write (disk full, permission,
     * OOM building a large HTML string) therefore never truncates or destroys an
     * existing good file -- the old file is only replaced once the new one is complete.
     * Falls back to a plain replace on filesystems without ATOMIC_MOVE.
     */
    private static void writeAtomically(Path target, byte[] bytes) throws IOException {
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path dir = (parent != null) ? parent : Paths.get(".");
        Path tmp = Files.createTempFile(dir, ".sparkta_", ".tmp");
        try {
            Files.write(tmp, bytes, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * t2j fix4 (ported from Astra rc1): copy source onto dest via a sibling temp file
     * plus an atomic move, refusing a missing or empty source. Used by saveas() so a
     * failed headless-browser export can never leave the user with a lost or
     * zero-byte destination file. Returns the resolved destination path string.
     */
    public static String copyFileAtomically(String source, String dest) throws IOException {
        Path src = Paths.get(source);
        if (!Files.exists(src)) throw new IOException("export source does not exist: " + source);
        if (Files.size(src) <= 0L) throw new IOException("export source is empty: " + source);
        Path dst = Paths.get(dest);
        Path parent = dst.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path dir = (parent != null) ? parent : Paths.get(".");
        Path tmp = Files.createTempFile(dir, ".sparkta_", ".tmp");
        try {
            Files.copy(src, tmp, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
        return dst.toString();
    }

    /**
     * Returns the resolved absolute path string for display to the user.
     * Call this before writeFile() to show where the file will be written.
     * v2.0.6
     */
    public static String resolvedPathString(String rawPath, String stataPwd) {
        return resolveExportPath(rawPath, stataPwd).toString();
    }

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
