package com.example.rest.product;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateProductRequestDto(
        @NotBlank String name,
        String description,
        @NotBlank String price,
        @Min(0) int stock
) {}
