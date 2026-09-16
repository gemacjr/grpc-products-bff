package com.example.rest.product;

import io.grpc.Status;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductGrpcClient products;

    public ProductController(ProductGrpcClient products) {
        this.products = products;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody CreateProductRequestDto body) {
        return products.create(body);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable String id) {
        return products.get(id);
    }

    @GetMapping
    public ProductListResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q) {
        return products.list(page, size, q);
    }

    @PutMapping("/{id}")
    public ProductResponse update(
            @PathVariable String id,
            @Valid @RequestBody CreateProductRequestDto body) {
        return products.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        if (!products.delete(id)) {
            throw Status.NOT_FOUND.withDescription("Product not found").asRuntimeException();
        }
    }

    @PostMapping("/{id}/stock")
    public ProductResponse adjustStock(
            @PathVariable String id,
            @Valid @RequestBody AdjustStockDto body) {
        return products.adjustStock(id, body.delta());
    }
}
