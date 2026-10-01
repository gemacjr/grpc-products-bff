package com.example.rest.product;

import io.grpc.Status;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    void create_withBlankName_returns400ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" ","description":"desc","price":"39.50","stock":80}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void create_withoutStock_returns400ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mouse","description":"desc","price":"39.50"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101"})
    void list_withOutOfRangePaging_returns400ProblemDetail(String query) throws Exception {
        String[] kv = query.split("=");
        mockMvc.perform(get("/api/products").param(kv[0], kv[1]))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
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

        mockMvc.perform(get("/api/products")
                        .param("page", "1")
                        .param("size", "5")
                        .param("q", "mo"))
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("id-1"));
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(80));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"delta\":null}"})
    void adjustStock_withoutDelta_returns400ProblemDetail(String body) throws Exception {
        mockMvc.perform(post("/api/products/id-1/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").isNotEmpty());
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
