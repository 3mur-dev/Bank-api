package com.omar.bankapi.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ErrorResponse(
        String message,
        int statusCode,
        Instant timestamp,
        String path,
        String traceId,
        Map<String, List<String>> fieldErrors
) {
        public int status() {
                return statusCode;
        }

        public Map<String, List<String>> errors() {
                return fieldErrors;
        }
}
