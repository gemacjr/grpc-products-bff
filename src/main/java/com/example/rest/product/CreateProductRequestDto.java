package com.example.rest.product;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateProductRequestDto(
        @NotBlank String name,
        String description,
        @NotBlank String price,
        @NotNull @Min(0) Integer stock
) {}
