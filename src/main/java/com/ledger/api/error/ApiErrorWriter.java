package com.ledger.api.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.api.dto.response.ApiError;
import com.ledger.api.dto.response.FieldViolation;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Component
public class ApiErrorWriter {

    private final ObjectMapper objectMapper;

    public ApiErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ApiError body(HttpStatus status, String code, String message, String path, List<FieldViolation> violations) {
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), code, message, path, violations);
    }

    public void write(HttpServletResponse response, HttpStatus status, String code, String message, String path)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body(status, code, message, path, List.of()));
    }
}
