package com.example.rest.product;

import com.example.product.v1.Product;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProductDtoMapperTest {

    @Test
    void toResponse_mapsFieldsAndTimestamps() {
        Instant created = Instant.parse("2026-09-16T16:00:35.382542Z");
        Instant updated = Instant.parse("2026-09-16T17:00:00Z");

        Product product = Product.newBuilder()
                .setId("8dd95081-3434-4681-9bfd-53bee5de9738")
                .setName("Wireless Mouse")
                .setDescription("Ergonomic 2.4GHz")
                .setPrice("39.50")
                .setStock(80)
                .setCreatedAt(Timestamp.newBuilder()
                        .setSeconds(created.getEpochSecond())
                        .setNanos(created.getNano())
                        .build())
                .setUpdatedAt(Timestamp.newBuilder()
                        .setSeconds(updated.getEpochSecond())
                        .setNanos(updated.getNano())
                        .build())
                .build();

        ProductResponse response = ProductDtoMapper.toResponse(product);

        assertEquals("8dd95081-3434-4681-9bfd-53bee5de9738", response.id());
        assertEquals("Wireless Mouse", response.name());
        assertEquals("Ergonomic 2.4GHz", response.description());
        assertEquals("39.50", response.price());
        assertEquals(80, response.stock());
        assertEquals(created, response.createdAt());
        assertEquals(updated, response.updatedAt());
    }

    @Test
    void toResponse_mapsUnsetTimestampsToNull() {
        Product product = Product.newBuilder().setId("id-1").build();

        ProductResponse response = ProductDtoMapper.toResponse(product);

        assertNull(response.createdAt());
        assertNull(response.updatedAt());
    }
}
