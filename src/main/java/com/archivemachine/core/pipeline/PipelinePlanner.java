/*
 * Plans the list of CoreTask instances for a given run.
 *
 * Responsibilities (extracted from the presenter):
 *  - Enumerate level-0 items (with ignore-list applied).
 *  - Resolve bucket + (optional) partition per item.
 *  - Build transfer + optional decompression tasks.
 *
 * Pure: returns a list; does not submit to a manager and does not touch the
 * destination filesystem except to enumerate existing P# folders for re-run continuation.
 */
package com.archivemachine.core.pipeline;

import com.archivemachine.core.destination.BucketStrategy;
import com.archivemachine.core.destination.DestinationResolver;
import com.archivemachine.core.task.CoreTask;
import com.archivemachine.core.tasks.Decompress7ZipTask;
import com.archivemachine.core.tasks.TransferMoveTask;
import com.archivemachine.core.util.ArchiveType;
import com.archivemachine.core.util.Level0Enumerator;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PipelinePlanner {

    private static final Pattern PARTITION_PATTERN = Pattern.compile("^P(\\d+)$");

    public record Input(
            Path source,
            Path destinationRoot,
            BucketStrategy strategy,
            boolean skipShortcuts,
            int partitionItemLimit,
            boolean decompressionEnabled,
            Path sevenZipExe,
            Collection<String> ignoredPaths
    ) {}

    public List<CoreTask> plan(Input in) throws IOException {
        List<CoreTask> out = new ArrayList<>();
        DestinationResolver resolver = new DestinationResolver(in.destinationRoot(), in.strategy());

        Map<Path, Integer> partitionIndex = new HashMap<>();
        Map<Path, Integer> partitionCount = new HashMap<>();

        for (Path item : Level0Enumerator.listLevel0Items(in.source(), in.ignoredPaths())) {
            String bucketName;
            try {
                bucketName = resolver.resolveBucketName(item);
            } catch (Exception e) {
                throw new IOException("Failed to resolve bucket for " + item + ": " + e.getMessage(), e);
            }

            String partitionName = null;
            if (in.partitionItemLimit() > 0) {
                Path bucketDir = resolver.resolveBucketDirectory(bucketName);

                Integer idx = partitionIndex.get(bucketDir);
                Integer cnt = partitionCount.get(bucketDir);

                if (idx == null) {
                    idx = findStartingPartitionIndex(bucketDir);
                    cnt = 0;
                }
                if (cnt != null && cnt == in.partitionItemLimit()) {
                    idx = idx + 1;
                    cnt = 0;
                }

                partitionName = "P" + idx;
                partitionIndex.put(bucketDir, idx);
                partitionCount.put(bucketDir, (cnt == null ? 0 : cnt) + 1);
            }

            Path finalDest = resolver.resolveFinalDestinationForItem(item, bucketName, partitionName);
            out.add(new TransferMoveTask(item, finalDest, in.skipShortcuts()));

            if (in.decompressionEnabled()
                    && Files.isRegularFile(item)
                    && ArchiveType.isSupportedArchiveName(item.getFileName().toString())) {
                Path exe = (in.sevenZipExe() == null) ? Path.of("7z") : in.sevenZipExe();
                out.add(new Decompress7ZipTask(finalDest, exe));
            }
        }
        return out;
    }

    private static int findStartingPartitionIndex(Path bucketDir) throws IOException {
        if (bucketDir == null || !Files.isDirectory(bucketDir)) return 1;

        int maxP = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(bucketDir)) {
            for (Path p : ds) {
                if (!Files.isDirectory(p)) continue;
                String name = p.getFileName() == null ? "" : p.getFileName().toString();
                Matcher m = PARTITION_PATTERN.matcher(name);
                if (!m.matches()) continue;
                try {
                    int n = Integer.parseInt(m.group(1));
                    if (n > maxP) maxP = n;
                } catch (NumberFormatException ignore) { }
            }
        }
        return Math.max(1, maxP + 1);
    }
}
