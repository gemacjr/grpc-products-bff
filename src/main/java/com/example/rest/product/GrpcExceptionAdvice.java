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
        String detail = switch (ex.getStatus().getCode()) {
            case NOT_FOUND, INVALID_ARGUMENT -> ex.getStatus().getDescription();
            default -> "Upstream gRPC call failed";
        };
        problem.setDetail(detail);
        problem.setProperty("grpcCode", ex.getStatus().getCode().name());
        return problem;
    }
}
