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
        assertEquals("bad", problem.getDetail());
    }

    @Test
    void mapsUnavailable() {
        ProblemDetail problem = advice.handleGrpc(
                Status.UNAVAILABLE.withDescription("database host leaked").asRuntimeException());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), problem.getStatus());
        assertEquals("Upstream gRPC call failed", problem.getDetail());
    }

    @Test
    void mapsDeadlineExceeded() {
        ProblemDetail problem = advice.handleGrpc(
                Status.DEADLINE_EXCEEDED.withDescription("internal timeout leaked").asRuntimeException());
        assertEquals(HttpStatus.GATEWAY_TIMEOUT.value(), problem.getStatus());
        assertEquals("Upstream gRPC call failed", problem.getDetail());
    }

    @Test
    void mapsOtherToBadGateway() {
        ProblemDetail problem = advice.handleGrpc(
                Status.INTERNAL.withDescription("stack detail leaked").asRuntimeException());
        assertEquals(HttpStatus.BAD_GATEWAY.value(), problem.getStatus());
        assertEquals("Upstream gRPC call failed", problem.getDetail());
    }
}
