package com.archivemachine.core.destination;

import java.nio.file.Path;

/**
 * Resolves the bucket folder name (a single path segment) for a given level-0 item.
 * Implementations either read file attributes (date strategies) or inspect the item name (first letter).
 */
public interface BucketStrategy {
    String resolveBucket(Path item) throws Exception;
}
