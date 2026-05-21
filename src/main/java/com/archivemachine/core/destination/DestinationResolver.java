/*
 * Pure path planner: computes the bucket folder and final destination for a level-0 item.
 * Does not touch the filesystem (the bucket strategy may; the resolver itself does not).
 */
package com.archivemachine.core.destination;

import java.nio.file.Path;

public final class DestinationResolver {

    private final Path destinationRoot;
    private final BucketStrategy strategy;

    public DestinationResolver(Path destinationRoot, BucketStrategy strategy) {
        this.destinationRoot = destinationRoot;
        this.strategy = strategy;
    }

    /** Returns the bucket name (e.g. "MMM yyyy" or "A"). */
    public String resolveBucketName(Path level0Item) throws Exception {
        return strategy.resolveBucket(level0Item);
    }

    /** {destinationRoot}/{bucketName} */
    public Path resolveBucketDirectory(String bucketName) {
        return destinationRoot.resolve(bucketName);
    }

    /**
     * Final destination for a level-0 item, given an already-resolved bucket name and
     * (optionally) a partition folder (e.g. "P1") that sits inside the bucket dir.
     */
    public Path resolveFinalDestinationForItem(Path level0Item, String bucketName, String partitionFolderName) {
        Path bucketDir = destinationRoot.resolve(bucketName);
        if (partitionFolderName != null && !partitionFolderName.isBlank()) {
            bucketDir = bucketDir.resolve(partitionFolderName.trim());
        }
        return bucketDir.resolve(level0Item.getFileName().toString());
    }
}
