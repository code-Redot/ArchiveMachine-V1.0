/*
 * Case-aware path containment + system-folder denylist (Windows-aware).
 */
package com.archivemachine.core.util;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

public final class PathSafety {

    private PathSafety() {}

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private static final Set<String> SYSTEM_FOLDER_NAMES = Set.of(
            "$recycle.bin",
            "system volume information",
            "$windows.~bt",
            "$windows.~ws",
            "config.msi",
            "recovery",
            "msocache"
    );

    private static final Set<String> SYSTEM_FILE_NAMES = Set.of(
            "thumbs.db",
            "desktop.ini",
            "ehthumbs.db",
            ".ds_store"
    );

    /** True if {@code inner} is the same as or nested inside {@code outer}, with OS-appropriate case sensitivity. */
    public static boolean isInsideOrEqual(Path outer, Path inner) {
        if (outer == null || inner == null) return false;
        String o = outer.toAbsolutePath().normalize().toString();
        String i = inner.toAbsolutePath().normalize().toString();
        if (IS_WINDOWS) {
            o = o.toLowerCase(Locale.ROOT);
            i = i.toLowerCase(Locale.ROOT);
        }
        if (!o.endsWith(java.io.File.separator)) o = o + java.io.File.separator;
        String iWithSep = i.endsWith(java.io.File.separator) ? i : i + java.io.File.separator;
        return iWithSep.equals(o) || iWithSep.startsWith(o);
    }

    /** True if the level-0 item looks like a Windows/system housekeeping folder or file we should never touch. */
    public static boolean isSystemItem(Path item) {
        if (item == null || item.getFileName() == null) return false;
        String name = item.getFileName().toString().toLowerCase(Locale.ROOT);
        if (SYSTEM_FOLDER_NAMES.contains(name)) return true;
        if (SYSTEM_FILE_NAMES.contains(name)) return true;
        return false;
    }
}
