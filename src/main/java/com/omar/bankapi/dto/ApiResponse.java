package com.omar.bankapi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Instant timestamp,
        String path,
        String traceId
) {

    public static <T> ApiResponse<T> ok(
            T data,
            String message,
            HttpServletRequest request
    ) {
        return new ApiResponse<>(
                true,
                message,
                data,
                Instant.now(),
                request.getRequestURI(),
                MDC.get("traceId")
        );
    }

    public static <T> ApiResponse<T> ok(
            T data,
            HttpServletRequest request
    ) {
        return ok(data, "Success", request);
    }

    @JsonValue
    public T value() {
        return data;
    }
}
