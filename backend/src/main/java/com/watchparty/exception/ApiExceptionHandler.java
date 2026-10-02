package com.watchparty.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns an ApiException into a small JSON error: {"error": "..."} with the right status code. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handle(ApiException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", e.getMessage());
        return ResponseEntity.status(e.getStatus()).body(body);
    }
}
