/*
 * Purpose: Immutable snapshot of user preferences used by Presenter and View.
 * Kept small and backward-compatible so older preference nodes can be read safely.
 */
package com.Redot.core.settings;

import com.Redot.core.destination.DestinationMode;

public record CoreSettings(
        String lastSourceDirectory,
        String lastDestinationDirectory,
        boolean decompressionEnabled,
        boolean skipShortcutsEnabled,
        int partitionItemLimit,
        DestinationMode destinationMode,
        String sevenZipPath
) {
    // region Preferences keys + defaults
    public static CoreSettings defaults() {
        return new CoreSettings("", "", false, false, 0, DestinationMode.TRANSFER_DATE, "7z");
    }
}
