package com.example.rest.product;

import com.example.product.v1.CreateProductRequest;
import com.example.product.v1.ListProductsRequest;
import com.example.product.v1.ListProductsResponse;
import com.example.product.v1.Product;
import com.example.product.v1.ProductServiceGrpc.ProductServiceBlockingStub;
import com.google.protobuf.Timestamp;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductGrpcClientTest {

    @Mock
    ProductServiceBlockingStub stub;

    @Test
    void create_mapsNullDescriptionToEmptyString() {
        when(stub.withDeadlineAfter(3, TimeUnit.SECONDS)).thenReturn(stub);
        Product created = Product.newBuilder()
                .setId("id-1")
                .setName("N")
                .setPrice("1.00")
                .setStock(1)
                .setCreatedAt(Timestamp.getDefaultInstance())
                .setUpdatedAt(Timestamp.getDefaultInstance())
                .build();
        when(stub.createProduct(any())).thenReturn(created);

        ProductGrpcClient client = new ProductGrpcClient(stub);
        client.create(new CreateProductRequestDto("N", null, "1.00", 1));

        ArgumentCaptor<CreateProductRequest> captor = ArgumentCaptor.forClass(CreateProductRequest.class);
        verify(stub).createProduct(captor.capture());
        assertEquals("", captor.getValue().getDescription());
    }

    @Test
    void list_mapsNullQueryToEmptyNameContains() {
        when(stub.withDeadlineAfter(3, TimeUnit.SECONDS)).thenReturn(stub);
        when(stub.listProducts(any())).thenReturn(ListProductsResponse.getDefaultInstance());

        ProductGrpcClient client = new ProductGrpcClient(stub);
        client.list(0, 20, null);

        ArgumentCaptor<ListProductsRequest> captor = ArgumentCaptor.forClass(ListProductsRequest.class);
        verify(stub).listProducts(captor.capture());
        assertEquals("", captor.getValue().getNameContains());
        assertEquals(0, captor.getValue().getPage());
        assertEquals(20, captor.getValue().getSize());
    }
}
