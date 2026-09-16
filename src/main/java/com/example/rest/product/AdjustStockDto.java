package com.example.rest.product;

import jakarta.validation.constraints.NotNull;

public record AdjustStockDto(@NotNull Integer delta) {}
