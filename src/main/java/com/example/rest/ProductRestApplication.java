package com.example.rest;

import com.example.product.v1.ProductServiceGrpc;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.grpc.client.ImportGrpcClients;

@SpringBootApplication
@ImportGrpcClients(
        target = "product",
        types = ProductServiceGrpc.ProductServiceBlockingStub.class
)
public class ProductRestApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductRestApplication.class, args);
    }
}
