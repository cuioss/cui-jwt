package de.cuioss.benchmarking.common.badge;

import lombok.Builder;
import lombok.Value;

/**
 * Represents a simple badge with a schema version, label, message, and color.
 * This class is designed to be serialized to JSON for shields.io.
 */
@Value
@Builder
public class Badge {
    int schemaVersion;
    String label;
    String message;
    String color;
}
