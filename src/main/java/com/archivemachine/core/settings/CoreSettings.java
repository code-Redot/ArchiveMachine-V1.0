/*
 * Immutable snapshot of user preferences used by Presenter and View.
 * Adds: ignoredPaths (absolute paths excluded from the level-0 sweep).
 */
package com.archivemachine.core.settings;

import com.archivemachine.core.destination.DestinationMode;

import java.util.List;

public record CoreSettings(
        String lastSourceDirectory,
        String lastDestinationDirectory,
        boolean decompressionEnabled,
        boolean skipShortcutsEnabled,
        int partitionItemLimit,
        DestinationMode destinationMode,
        String sevenZipPath,
        List<String> ignoredPaths
) {
    public CoreSettings {
        ignoredPaths = ignoredPaths == null ? List.of() : List.copyOf(ignoredPaths);
    }

    public static CoreSettings defaults() {
        return new CoreSettings("", "", false, false, 0, DestinationMode.TRANSFER_DATE, "7z", List.of());
    }
}
