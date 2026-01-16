package com.Redot.core.destination;

import java.nio.file.Path;
import java.time.YearMonth;

public interface DestinationStrategy {
    YearMonth resolveBucket(Path item) throws Exception;
}
