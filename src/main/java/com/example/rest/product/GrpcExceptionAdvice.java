package com.example.rest.product;

import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GrpcExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(GrpcExceptionAdvice.class);

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
        if (http.is5xxServerError()) {
            log.warn("Upstream gRPC call failed: code={} description={}",
                    ex.getStatus().getCode(), ex.getStatus().getDescription(), ex);
        }
        problem.setDetail(detail);
        problem.setProperty("grpcCode", ex.getStatus().getCode().name());
        return problem;
    }
}
