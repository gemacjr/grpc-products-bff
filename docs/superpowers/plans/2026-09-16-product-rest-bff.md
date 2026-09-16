# Product REST BFF Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Spring Boot 4.1 REST/JSON BFF on port 8081 that exposes full Product CRUD + stock adjust and calls `product.v1.ProductService` over gRPC on port 9090.

**Architecture:** HTTP controllers validate JSON DTOs, a `ProductGrpcClient` maps to/from protobuf and calls a blocking stub with a 3s deadline, and `@RestControllerAdvice` maps `StatusRuntimeException` to RFC 7807 `ProblemDetail`. No local Product database.

**Tech Stack:** Java 21, Spring Boot 4.1.1, `spring-boot-starter-web`, `validation`, `spring-boot-starter-grpc-client`, protobuf Gradle plugin 0.9.6, JUnit 5 + MockMvc.

**Spec:** `docs/superpowers/specs/2026-09-16-product-rest-bff-design.md`

## Global Constraints

- Spring Boot **4.1.x**, Java **21**
- No authentication on REST endpoints
- Plaintext gRPC locally; channel name `product` → `static://${PRODUCT_GRPC_HOST:localhost}:${PRODUCT_GRPC_PORT:9090}`
- Share only `product.proto` — do not reuse server JPA entities or Postgres
- Price fields are **strings** in JSON and proto
- REST port **8081**
- Package root: `com.example.rest`
- Proto Java package: `com.example.product.v1`
- Outbound gRPC deadline: **3 seconds**
- This workspace may not be a git repo yet — if `git status` fails, run `git init` once before the first commit; do not force-push or amend unless the user asks

---

## File Structure

| File | Responsibility |
|------|----------------|
| `build.gradle.kts` | Boot 4.1, protobuf/grpc codegen, web + grpc-client deps |
| `settings.gradle.kts` | Root project name |
| `src/main/proto/product.proto` | Shared ProductService contract (copy from integration docs) |
| `src/main/resources/application.yml` | Port 8081 + gRPC client channel |
| `src/main/java/com/example/rest/ProductRestApplication.java` | Boot entry + `@ImportGrpcClients` |
| `src/main/java/com/example/rest/product/CreateProductRequestDto.java` | Create/update request record + validation |
| `src/main/java/com/example/rest/product/AdjustStockDto.java` | Stock delta request |
| `src/main/java/com/example/rest/product/ProductResponse.java` | Product JSON response |
| `src/main/java/com/example/rest/product/ProductListResponse.java` | List JSON response |
| `src/main/java/com/example/rest/product/ProductDtoMapper.java` | Proto → REST DTO |
| `src/main/java/com/example/rest/product/ProductGrpcClient.java` | All RPC invocations |
| `src/main/java/com/example/rest/product/ProductController.java` | `/api/products` REST surface |
| `src/main/java/com/example/rest/product/GrpcExceptionAdvice.java` | gRPC → HTTP ProblemDetail |
| `src/test/java/com/example/rest/product/ProductDtoMapperTest.java` | Mapper unit tests |
| `src/test/java/com/example/rest/product/GrpcExceptionAdviceTest.java` | Status mapping tests |
| `src/test/java/com/example/rest/product/ProductControllerTest.java` | MockMvc + mocked client |

---

### Task 1: Gradle + proto contract

**Files:**
- Modify: `build.gradle.kts`
- Modify: `settings.gradle.kts` (only if name is wrong)
- Create: `src/main/proto/product.proto`
- Delete if present: default `src/main/java/org/example/...` placeholder classes from the empty Gradle template (keep nothing under `org.example`)

**Interfaces:**
- Consumes: none
- Produces: compilable project that generates `com.example.product.v1.ProductServiceGrpc.ProductServiceBlockingStub` and message types under `build/generated/sources/proto/`

- [ ] **Step 1: Replace `build.gradle.kts` with Boot + gRPC client setup**

```kotlin
plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.protobuf") version "0.9.6"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-grpc-client")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-grpc-client-test")
}

protobuf {
    plugins {
        create("grpc")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

Ensure `settings.gradle.kts` contains:

```kotlin
rootProject.name = "sb_grpc_rest"
```

- [ ] **Step 2: Copy the proto contract**

Copy from `/Users/edmini/Desktop/grpc_client/docs/client-integration/product.proto` to `src/main/proto/product.proto` unchanged (must keep `option java_package = "com.example.product.v1"` and `java_multiple_files = true`).

- [ ] **Step 3: Generate stubs**

Run: `./gradlew compileJava`

Expected: BUILD SUCCESSFUL; generated sources include `ProductServiceGrpc.java` under `build/generated/sources/proto/main/grpc/com/example/product/v1/`.

- [ ] **Step 4: Commit**

```bash
git add build.gradle.kts settings.gradle.kts src/main/proto/product.proto
git commit -m "$(cat <<'EOF'
chore: add Spring Boot 4.1 and Product proto for gRPC client

EOF
)"
```

---

### Task 2: Application config and Boot entry

**Files:**
- Create: `src/main/resources/application.yml`
- Create: `src/main/java/com/example/rest/ProductRestApplication.java`

**Interfaces:**
- Consumes: generated `ProductServiceGrpc.ProductServiceBlockingStub`
- Produces: Spring app that registers a bean of type `ProductServiceGrpc.ProductServiceBlockingStub` for channel target `product`

- [ ] **Step 1: Add `application.yml`**

```yaml
server:
  port: 8081

spring:
  application:
    name: product-rest-bff
  grpc:
    client:
      channel:
        product:
          target: static://${PRODUCT_GRPC_HOST:localhost}:${PRODUCT_GRPC_PORT:9090}
```

- [ ] **Step 2: Add application class**

```java
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
```

- [ ] **Step 3: Verify the context can start when gRPC is up (optional smoke)**

If the Product gRPC server is already running on `:9090`:

Run: `./gradlew bootRun`

Expected: app listens on `8081` without failing channel creation. Stop with Ctrl-C. If gRPC is down, defer this smoke to Task 6 — context load may still succeed with lazy channel use depending on Boot version; do not block the task if only connection fails at first RPC.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/application.yml src/main/java/com/example/rest/ProductRestApplication.java
git commit -m "$(cat <<'EOF'
feat: wire Spring Boot app and product gRPC channel

EOF
)"
```

---

### Task 3: DTOs and mapper (TDD)

**Files:**
- Create: `src/main/java/com/example/rest/product/CreateProductRequestDto.java`
- Create: `src/main/java/com/example/rest/product/AdjustStockDto.java`
- Create: `src/main/java/com/example/rest/product/ProductResponse.java`
- Create: `src/main/java/com/example/rest/product/ProductListResponse.java`
- Create: `src/main/java/com/example/rest/product/ProductDtoMapper.java`
- Test: `src/test/java/com/example/rest/product/ProductDtoMapperTest.java`

**Interfaces:**
- Consumes: `com.example.product.v1.Product`, `com.google.protobuf.Timestamp`
- Produces:
  - `record CreateProductRequestDto(String name, String description, String price, int stock)`
  - `record AdjustStockDto(Integer delta)`
  - `record ProductResponse(String id, String name, String description, String price, int stock, Instant createdAt, Instant updatedAt)`
  - `record ProductListResponse(List<ProductResponse> products, long totalCount)`
  - `ProductDtoMapper.toResponse(Product p) → ProductResponse`

- [ ] **Step 1: Write the failing mapper test**

```java
package com.example.rest.product;

import com.example.product.v1.Product;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.example.rest.product.ProductDtoMapperTest`

Expected: FAIL (class or method missing).

- [ ] **Step 3: Add DTOs and mapper**

```java
package com.example.rest.product;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public record CreateProductRequestDto(
        @NotBlank String name,
        String description,
        @NotBlank String price,
        @Min(0) int stock
) {}

public record AdjustStockDto(@NotNull Integer delta) {}

public record ProductResponse(
        String id,
        String name,
        String description,
        String price,
        int stock,
        Instant createdAt,
        Instant updatedAt
) {}

public record ProductListResponse(List<ProductResponse> products, long totalCount) {}
```

Put each record in its own file with matching public class name (four files). Then:

```java
package com.example.rest.product;

import com.example.product.v1.Product;
import com.google.protobuf.Timestamp;
import java.time.Instant;

public final class ProductDtoMapper {

    private ProductDtoMapper() {}

    public static ProductResponse toResponse(Product p) {
        return new ProductResponse(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getPrice(),
                p.getStock(),
                toInstant(p.getCreatedAt()),
                toInstant(p.getUpdatedAt())
        );
    }

    private static Instant toInstant(Timestamp ts) {
        return Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.example.rest.product.ProductDtoMapperTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/rest/product src/test/java/com/example/rest/product/ProductDtoMapperTest.java
git commit -m "$(cat <<'EOF'
feat: add product REST DTOs and proto mapper

EOF
)"
```

---

### Task 4: gRPC exception advice (TDD)

**Files:**
- Create: `src/main/java/com/example/rest/product/GrpcExceptionAdvice.java`
- Test: `src/test/java/com/example/rest/product/GrpcExceptionAdviceTest.java`

**Interfaces:**
- Consumes: `io.grpc.StatusRuntimeException`
- Produces: `ProblemDetail handleGrpc(StatusRuntimeException ex)` with mapping:
  - `NOT_FOUND` → 404
  - `INVALID_ARGUMENT` → 400
  - `UNAVAILABLE` → 503
  - `DEADLINE_EXCEEDED` → 504
  - else → 502
  - property `grpcCode` = status code name; `detail` = description

- [ ] **Step 1: Write failing tests**

```java
package com.example.rest.product;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GrpcExceptionAdviceTest {

    private final GrpcExceptionAdvice advice = new GrpcExceptionAdvice();

    @Test
    void mapsNotFound() {
        ProblemDetail problem = advice.handleGrpc(
                Status.NOT_FOUND.withDescription("missing").asRuntimeException());
        assertEquals(HttpStatus.NOT_FOUND.value(), problem.getStatus());
        assertEquals("missing", problem.getDetail());
        assertEquals("NOT_FOUND", problem.getProperties().get("grpcCode"));
    }

    @Test
    void mapsInvalidArgument() {
        ProblemDetail problem = advice.handleGrpc(
                Status.INVALID_ARGUMENT.withDescription("bad").asRuntimeException());
        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
    }

    @Test
    void mapsUnavailable() {
        ProblemDetail problem = advice.handleGrpc(
                Status.UNAVAILABLE.asRuntimeException());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), problem.getStatus());
    }

    @Test
    void mapsDeadlineExceeded() {
        ProblemDetail problem = advice.handleGrpc(
                Status.DEADLINE_EXCEEDED.asRuntimeException());
        assertEquals(HttpStatus.GATEWAY_TIMEOUT.value(), problem.getStatus());
    }

    @Test
    void mapsOtherToBadGateway() {
        ProblemDetail problem = advice.handleGrpc(
                Status.INTERNAL.asRuntimeException());
        assertEquals(HttpStatus.BAD_GATEWAY.value(), problem.getStatus());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests com.example.rest.product.GrpcExceptionAdviceTest`

Expected: FAIL (missing class).

- [ ] **Step 3: Implement advice**

```java
package com.example.rest.product;

import io.grpc.StatusRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GrpcExceptionAdvice {

    @ExceptionHandler(StatusRuntimeException.class)
    public ProblemDetail handleGrpc(StatusRuntimeException ex) {
        HttpStatus http = switch (ex.getStatus().getCode()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
            default -> HttpStatus.BAD_GATEWAY;
        };
        ProblemDetail problem = ProblemDetail.forStatus(http);
        problem.setDetail(ex.getStatus().getDescription());
        problem.setProperty("grpcCode", ex.getStatus().getCode().name());
        return problem;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests com.example.rest.product.GrpcExceptionAdviceTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/rest/product/GrpcExceptionAdvice.java src/test/java/com/example/rest/product/GrpcExceptionAdviceTest.java
git commit -m "$(cat <<'EOF'
feat: map gRPC status exceptions to HTTP ProblemDetail

EOF
)"
```

---

### Task 5: Product gRPC client

**Files:**
- Create: `src/main/java/com/example/rest/product/ProductGrpcClient.java`
- Test: `src/test/java/com/example/rest/product/ProductGrpcClientTest.java`

**Interfaces:**
- Consumes: `ProductServiceBlockingStub`, DTOs, `ProductDtoMapper`
- Produces:
  - `ProductResponse create(CreateProductRequestDto dto)`
  - `ProductResponse get(String id)`
  - `ProductListResponse list(int page, int size, String nameContains)`
  - `ProductResponse update(String id, CreateProductRequestDto dto)`
  - `boolean delete(String id)` — returns `deleted` from proto
  - `ProductResponse adjustStock(String id, int delta)`
  - All calls use `stub.withDeadlineAfter(3, TimeUnit.SECONDS)`
  - Null `description` / `nameContains` become `""`

- [ ] **Step 1: Write a failing unit test with a mock stub**

Use Mockito (comes with `spring-boot-starter-webmvc-test`). Focus on `create` and `list` null-safety:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.example.rest.product.ProductGrpcClientTest`

Expected: FAIL (missing `ProductGrpcClient`).

- [ ] **Step 3: Implement `ProductGrpcClient`**

```java
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests com.example.rest.product.ProductGrpcClientTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/rest/product/ProductGrpcClient.java src/test/java/com/example/rest/product/ProductGrpcClientTest.java
git commit -m "$(cat <<'EOF'
feat: add ProductGrpcClient with deadlines and proto mapping

EOF
)"
```

---

### Task 6: REST controller (TDD)

**Files:**
- Create: `src/main/java/com/example/rest/product/ProductController.java`
- Test: `src/test/java/com/example/rest/product/ProductControllerTest.java`

**Interfaces:**
- Consumes: `ProductGrpcClient` methods from Task 5
- Produces: REST mappings exactly as in the spec table; delete returns 204 when `delete` is true, otherwise throws `Status.NOT_FOUND` so `GrpcExceptionAdvice` returns 404

- [ ] **Step 1: Write failing MockMvc tests**

```java
package com.example.rest.product;

import com.example.rest.ProductRestApplication;
import io.grpc.Status;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProductController.class)
class ProductControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProductGrpcClient products;

    private ProductResponse sample() {
        return new ProductResponse(
                "id-1", "Mouse", "desc", "39.50", 80,
                Instant.parse("2026-09-16T16:00:35Z"),
                Instant.parse("2026-09-16T16:00:35Z"));
    }

    @Test
    void create_returns201() throws Exception {
        when(products.create(any())).thenReturn(sample());

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mouse","description":"desc","price":"39.50","stock":80}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("id-1"))
                .andExpect(jsonPath("$.price").value("39.50"));
    }

    @Test
    void get_returns200() throws Exception {
        when(products.get("id-1")).thenReturn(sample());
        mockMvc.perform(get("/api/products/id-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mouse"));
    }

    @Test
    void list_passesQueryParams() throws Exception {
        when(products.list(1, 5, "mo")).thenReturn(new ProductListResponse(List.of(sample()), 1));
        mockMvc.perform(get("/api/products").param("page", "1").param("size", "5").param("q", "mo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.products[0].id").value("id-1"));
    }

    @Test
    void update_returns200() throws Exception {
        when(products.update(eq("id-1"), any())).thenReturn(sample());
        mockMvc.perform(put("/api/products/id-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mouse","description":"desc","price":"39.50","stock":80}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void delete_returns204WhenDeleted() throws Exception {
        when(products.delete("id-1")).thenReturn(true);
        mockMvc.perform(delete("/api/products/id-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns404WhenNotDeleted() throws Exception {
        when(products.delete("missing")).thenReturn(false);
        mockMvc.perform(delete("/api/products/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adjustStock_returns200() throws Exception {
        when(products.adjustStock("id-1", -5)).thenReturn(sample());
        mockMvc.perform(post("/api/products/id-1/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"delta":-5}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void grpcNotFound_mapsTo404() throws Exception {
        when(products.get(anyString())).thenThrow(
                Status.NOT_FOUND.withDescription("gone").asRuntimeException());
        mockMvc.perform(get("/api/products/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("gone"))
                .andExpect(jsonPath("$.grpcCode").value("NOT_FOUND"));
    }
}
```

If `@WebMvcTest` fails to pick up `GrpcExceptionAdvice`, add `@Import(GrpcExceptionAdvice.class)` on the test class. If `spring-boot-starter-webmvc-test` / `@MockitoBean` package differs in 4.1.1, adjust imports to match what Boot generates (check compile errors). Do **not** load full `ProductRestApplication` if it tries to create a real gRPC stub — keep slice to `ProductController` (+ advice).

Remove unused imports (`ProductRestApplication`, `anyInt`, `isNull`) if the compiler warns.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests com.example.rest.product.ProductControllerTest`

Expected: FAIL (missing controller).

- [ ] **Step 3: Implement controller**

```java
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
```

- [ ] **Step 4: Run all product tests**

Run: `./gradlew test`

Expected: BUILD SUCCESSFUL; all tests green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/rest/product/ProductController.java src/test/java/com/example/rest/product/ProductControllerTest.java
git commit -m "$(cat <<'EOF'
feat: expose /api/products REST surface over gRPC client

EOF
)"
```

---

### Task 7: Manual end-to-end smoke (no code unless broken)

**Files:** none expected

**Interfaces:**
- Consumes: running gRPC Product API on `:9090` and this app on `:8081`
- Produces: verified create/list via HTTP

- [ ] **Step 1: Start gRPC server** (other repo / compose as documented in the integration guide)

- [ ] **Step 2: Start REST BFF**

```bash
export PRODUCT_GRPC_HOST=localhost PRODUCT_GRPC_PORT=9090
./gradlew bootRun
```

- [ ] **Step 3: Create a product**

```bash
curl -s -w "\nHTTP:%{http_code}\n" http://localhost:8081/api/products \
  -H 'Content-Type: application/json' \
  -d '{"name":"From REST","description":"via gRPC","price":"10.00","stock":2}'
```

Expected: HTTP 201 and JSON with `id`, `price` `"10.00"`.

- [ ] **Step 4: List products**

```bash
curl -s "http://localhost:8081/api/products?page=0&size=5"
```

Expected: `products` array includes the created item; `totalCount` ≥ 1.

- [ ] **Step 5: Commit nothing unless smoke revealed a fix** — if a fix was required, commit that fix with a clear message, then re-run Steps 3–4.

---

## Self-Review Checklist

- [x] Spec coverage: full REST surface, DTOs, mapper, client deadlines, exception advice, delete 204/404, Gradle/proto, config, tests, manual smoke
- [x] No placeholders / TBD steps
- [x] Type names consistent across tasks (`ProductGrpcClient`, DTO records, mapper)

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-09-16-product-rest-bff.md`. Two execution options:

**1. Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** — execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
