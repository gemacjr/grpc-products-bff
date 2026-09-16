package com.example.rest.product;

import java.util.List;

public record ProductListResponse(List<ProductResponse> products, long totalCount) {}
