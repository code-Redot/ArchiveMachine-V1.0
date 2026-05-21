/*
 * Level-0 enumerator: returns only direct children of the source root.
 * Filters: ignore-list (user-chosen paths) and system housekeeping items.
 */
package com.archivemachine.core.util;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Level0Enumerator {

    private Level0Enumerator() {}

    public static List<Path> listLevel0Items(Path sourceRoot, Collection<String> ignoredAbsolutePaths) throws IOException {
        Set<Path> ignored = new HashSet<>();
        if (ignoredAbsolutePaths != null) {
            for (String s : ignoredAbsolutePaths) {
                if (s == null || s.isBlank()) continue;
                try { ignored.add(Path.of(s).toAbsolutePath().normalize()); }
                catch (Exception ignore) { /* skip unparseable */ }
            }
        }

        List<Path> out = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(sourceRoot)) {
            for (Path p : stream) {
                if (PathSafety.isSystemItem(p)) continue;
                Path normalized = p.toAbsolutePath().normalize();
                if (ignored.contains(normalized)) continue;
                out.add(p);
            }
        }
        return out;
    }
}
