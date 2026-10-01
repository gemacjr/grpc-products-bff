package com.example.rest.product;

import com.example.product.v1.Product;
import com.google.protobuf.Timestamp;
import java.time.Instant;

public final class ProductDtoMapper {

    private ProductDtoMapper() {}

    public static ProductResponse toResponse(Product p) {
        return new ProductResponse(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getPrice(),
                p.getStock(),
                p.hasCreatedAt() ? toInstant(p.getCreatedAt()) : null,
                p.hasUpdatedAt() ? toInstant(p.getUpdatedAt()) : null
        );
    }

    private static Instant toInstant(Timestamp ts) {
        return Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
    }
}
