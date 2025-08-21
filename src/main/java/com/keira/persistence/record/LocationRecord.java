package com.keira.persistence.record;

import java.time.LocalDateTime;

/**
 * Immutable location row mapped via MyBatis constructor args.
 */
public record LocationRecord(
        String locationName,
        String world,
        double x,
        double y,
        double z,
        String description,
        LocalDateTime savedAt
) {}

