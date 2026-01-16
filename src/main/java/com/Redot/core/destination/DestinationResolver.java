/*
 * Purpose: Centralizes destination path policy (date bucketing and optional partitioning).
 * The resolver is pure: it computes paths only and does not touch the file system.
 */
package com.Redot.core.destination;

import java.nio.file.Path;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class DestinationResolver {
    // region Date folder naming (MMM yyyy)
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    // region Bucket strategy (TRANSFER_DATE / CREATION_DATE / LAST_MODIFIED)
    private final Path destinationRoot;
    private final DestinationStrategy strategy;

    public DestinationResolver(Path destinationRoot, DestinationStrategy strategy) {
        this.destinationRoot = destinationRoot;
        this.strategy = strategy;
    }

    // Resolves {destinationRoot}\{MMM yyyy}\{itemName} using the selected bucket strategy.
    public Path resolveFinalDestinationForItem(Path level0Item) throws Exception {
        YearMonth bucket = strategy.resolveBucket(level0Item);
        Path monthDir = destinationRoot.resolve(bucket.format(FMT));
        return monthDir.resolve(level0Item.getFileName().toString());
    }

    // region Partition integration (P# inside date folder)
    /**
     * Resolves the date bucket directory for the given level-0 item:
     * {destinationRoot}\{MMM yyyy}
     */
    public Path resolveDateDirectory(Path level0Item) throws Exception {
        YearMonth bucket = strategy.resolveBucket(level0Item);
        return destinationRoot.resolve(bucket.format(FMT));
    }

    /**
     * Resolves the final destination for a level-0 item, optionally placing it under a partition
     * folder (e.g. "P1") inside the date bucket directory.
     */
    public Path resolveFinalDestinationForItem(Path level0Item, String partitionFolderName) throws Exception {
        YearMonth bucket = strategy.resolveBucket(level0Item);
        Path monthDir = destinationRoot.resolve(bucket.format(FMT));
        if (partitionFolderName != null && !partitionFolderName.isBlank()) {
            monthDir = monthDir.resolve(partitionFolderName.trim());
        }
        return monthDir.resolve(level0Item.getFileName().toString());
    }
}
