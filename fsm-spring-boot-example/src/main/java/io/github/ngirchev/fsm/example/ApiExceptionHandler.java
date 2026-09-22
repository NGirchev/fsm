package io.github.ngirchev.fsm.example;

import io.github.ngirchev.fsm.exception.FsmException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;

// Spring maps controller/service exceptions to HTTP responses through these handlers.
@RestControllerAdvice
public class ApiExceptionHandler {
    public record ApiError(String message) {
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError notFound(NoSuchElementException error) {
        return new ApiError(message(error, "Not found"));
    }

    @ExceptionHandler(FsmException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError conflict(RuntimeException error) {
        return new ApiError(message(error, "Conflict"));
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError invalidFlowState(IllegalStateException error) {
        return new ApiError(message(error, "Conflict"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> status(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode())
                .body(new ApiError(error.getReason() == null ? "Request failed" : error.getReason()));
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError badRequest(Exception error) {
        return new ApiError(message(error, "Bad request"));
    }

    private static String message(Exception error, String fallback) {
        return error.getMessage() == null ? fallback : error.getMessage();
    }
}
