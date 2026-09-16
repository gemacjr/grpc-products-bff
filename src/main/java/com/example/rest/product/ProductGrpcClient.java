package com.example.rest.product;

import com.example.product.v1.AdjustStockRequest;
import com.example.product.v1.CreateProductRequest;
import com.example.product.v1.DeleteProductRequest;
import com.example.product.v1.GetProductRequest;
import com.example.product.v1.ListProductsRequest;
import com.example.product.v1.ProductServiceGrpc.ProductServiceBlockingStub;
import com.example.product.v1.UpdateProductRequest;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class ProductGrpcClient {

    private static final long DEADLINE_SECONDS = 3;

    private final ProductServiceBlockingStub productStub;

    public ProductGrpcClient(ProductServiceBlockingStub productStub) {
        this.productStub = productStub;
    }

    private ProductServiceBlockingStub stub() {
        return productStub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS);
    }

    public ProductResponse create(CreateProductRequestDto dto) {
        var created = stub().createProduct(CreateProductRequest.newBuilder()
                .setName(dto.name())
                .setDescription(dto.description() == null ? "" : dto.description())
                .setPrice(dto.price())
                .setStock(dto.stock())
                .build());
        return ProductDtoMapper.toResponse(created);
    }

    public ProductResponse get(String id) {
        var product = stub().getProduct(GetProductRequest.newBuilder().setId(id).build());
        return ProductDtoMapper.toResponse(product);
    }

    public ProductListResponse list(int page, int size, String nameContains) {
        var response = stub().listProducts(ListProductsRequest.newBuilder()
                .setPage(page)
                .setSize(size)
                .setNameContains(nameContains == null ? "" : nameContains)
                .build());
        var items = response.getProductsList().stream()
                .map(ProductDtoMapper::toResponse)
                .toList();
        return new ProductListResponse(items, response.getTotalCount());
    }

    public ProductResponse update(String id, CreateProductRequestDto dto) {
        var updated = stub().updateProduct(UpdateProductRequest.newBuilder()
                .setId(id)
                .setName(dto.name())
                .setDescription(dto.description() == null ? "" : dto.description())
                .setPrice(dto.price())
                .setStock(dto.stock())
                .build());
        return ProductDtoMapper.toResponse(updated);
    }

    public boolean delete(String id) {
        return stub().deleteProduct(DeleteProductRequest.newBuilder().setId(id).build())
                .getDeleted();
    }

    public ProductResponse adjustStock(String id, int delta) {
        var updated = stub().adjustStock(AdjustStockRequest.newBuilder()
                .setId(id)
                .setDelta(delta)
                .build());
        return ProductDtoMapper.toResponse(updated);
    }
}
