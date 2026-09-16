package com.example.rest.product;

import java.time.Instant;

public record ProductResponse(
        String id,
        String name,
        String description,
        String price,
        int stock,
        Instant createdAt,
        Instant updatedAt
) {}
