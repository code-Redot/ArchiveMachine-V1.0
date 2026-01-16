package com.Redot.core.util;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * LEVEL-0 enumerator: returns only direct children of the selected source root.
 * No deep traversal.
 */
public final class Level0Enumerator {

    private Level0Enumerator() {}

    public static List<Path> listLevel0Items(Path sourceRoot) throws IOException {
        List<Path> out = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(sourceRoot)) {
            for (Path p : stream) out.add(p);
        }
        return out;
    }
}
