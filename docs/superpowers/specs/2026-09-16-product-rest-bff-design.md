# Product REST BFF — Design

Date: 2026-09-16  
Status: Approved (pending final user review of this document)

## Goal

Build a Spring Boot REST/JSON API in `sb_grpc_rest` that calls the existing Product gRPC service (`product.v1.ProductService` on port `9090`). This app is a BFF: no Product database of its own; the gRPC API is the source of truth.

## Constraints

- Spring Boot **4.1.x**, Java **21**
- No authentication on REST endpoints (local / open BFF)
- Plaintext gRPC locally (no TLS in this phase)
- Share only `product.proto` (or generated stubs) — do not reuse server JPA entities
- Contract source: `/Users/edmini/Desktop/grpc_client/docs/client-integration/`

## Architecture

```
Browser / HTTP client
        │  JSON over HTTP (:8081)
        ▼
┌───────────────────────────┐
│  product-rest-bff         │
│  Controllers → Client svc │
└─────────────┬─────────────┘
              │  protobuf / gRPC
              ▼
┌───────────────────────────┐
│  grpc-product-api         │
│  ProductService :9090     │
└───────────────────────────┘
```

Chosen approach: **guide-shaped BFF** — `ProductController` → `ProductGrpcClient` → `ProductServiceBlockingStub`, with a DTO mapper and central gRPC→HTTP exception advice. Rejected: controllers calling stubs directly (proto leakage); reactive stubs (unnecessary complexity for this scope).

## Components

| Piece | Responsibility |
|-------|----------------|
| `ProductRestApplication` | Boot entry; `@ImportGrpcClients(target = "product", types = …BlockingStub)` |
| `ProductController` | HTTP mapping, validation, status codes |
| `ProductGrpcClient` | Build proto requests, call stub (with deadline), return REST DTOs |
| `ProductDtoMapper` | Proto `Product` / timestamps → `ProductResponse` |
| `GrpcExceptionAdvice` | `StatusRuntimeException` → `ProblemDetail` |
| DTOs | `CreateProductRequestDto`, `AdjustStockDto`, `ProductResponse`, `ProductListResponse` |
| `product.proto` | Copied to `src/main/proto/product.proto` |
| `application.yml` | Server port `8081`; channel `product` → `static://${PRODUCT_GRPC_HOST:localhost}:${PRODUCT_GRPC_PORT:9090}` |

### Project layout

```
sb_grpc_rest/
├── build.gradle.kts
├── src/main/proto/product.proto
├── src/main/resources/application.yml
└── src/main/java/com/example/rest/
    ├── ProductRestApplication.java
    └── product/
        ├── ProductController.java
        ├── ProductGrpcClient.java
        ├── ProductDtoMapper.java
        ├── GrpcExceptionAdvice.java
        └── (DTO records — same package or nested)
```

### Gradle

- Plugins: `java`, Spring Boot `4.1.1`, dependency-management, `com.google.protobuf` `0.9.6`
- Dependencies: `spring-boot-starter-web`, `validation`, `grpc-client`; test: `webmvc-test`, `grpc-client-test`
- Protobuf: `plugins { create("grpc") }` so blocking stubs are generated

## REST ↔ gRPC mapping

| REST | gRPC | Notes |
|------|------|--------|
| `POST /api/products` | `CreateProduct` | `201` + body |
| `GET /api/products/{id}` | `GetProduct` | path id |
| `GET /api/products?page&size&q` | `ListProducts` | `q` → `name_contains`; defaults page=0, size=20 |
| `PUT /api/products/{id}` | `UpdateProduct` | full replace of name, description, price, stock |
| `DELETE /api/products/{id}` | `DeleteProduct` | `204` if `deleted=true`; `404` otherwise |
| `POST /api/products/{id}/stock` | `AdjustStock` | body `{ "delta": n }` |

### JSON shapes

Create/Update request: `{ "name", "description", "price", "stock" }` — **price is a string**.  
Product response: `{ "id", "name", "description", "price", "stock", "createdAt", "updatedAt" }`.  
List response: `{ "products": [...], "totalCount": N }`.  
Adjust stock: `{ "delta": -5 }`.

Validation: `@NotBlank` on name and price; `@Min(0)` on stock; `@NotNull` on delta.

## Error handling

Map `StatusRuntimeException` via `@RestControllerAdvice`:

| gRPC code | HTTP |
|-----------|------|
| `NOT_FOUND` | 404 |
| `INVALID_ARGUMENT` | 400 |
| `UNAVAILABLE` | 503 |
| `DEADLINE_EXCEEDED` | 504 |
| other | 502 |

Response body: `ProblemDetail` with `detail` from gRPC description and property `grpcCode`.

Outbound stub calls use a short deadline (e.g. 3 seconds).

## Testing

- Mapper unit tests (proto → DTO, including timestamps)
- Exception advice tests (status code mapping)
- Optional MockMvc tests with a mocked `ProductServiceBlockingStub` / client
- No embedded Postgres; live end-to-end against the gRPC server is manual (`curl`) unless a later follow-up adds Testcontainers

## Out of scope

- Authentication / authorization
- TLS for gRPC
- Docker Compose packaging for this REST app
- Publishing a shared `product-api-proto` JAR
- Any local Product persistence

## Local run (success criteria)

1. gRPC Product API running on `:9090`
2. `PRODUCT_GRPC_HOST=localhost PRODUCT_GRPC_PORT=9090 ./gradlew bootRun` — REST on `:8081`
3. `POST /api/products` with valid JSON returns `201` and a product with id
4. Missing id → `404` ProblemDetail; invalid stock adjust → `400`

## Reference

- Integration guide: `grpc_client/docs/client-integration/REST-CLIENT-INTEGRATION.md`
- Spring Boot gRPC client: https://docs.spring.io/spring-boot/reference/io/grpc.html
