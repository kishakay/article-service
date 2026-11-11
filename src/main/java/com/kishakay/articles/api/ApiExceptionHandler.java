package com.kishakay.articles.api;

import com.kishakay.articles.article.ArticleNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ApiError.FieldViolation(
                        error.getField(),
                        error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage()
                ))
                .toList();

        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed",
                request.getRequestURI(),
                violations
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        String message;
        List<ApiError.FieldViolation> violations;

        if (ex.getRequiredType() == UUID.class) {
            message = "Malformed UUID";
            violations = List.of(new ApiError.FieldViolation(
                    ex.getName() == null ? "id" : ex.getName(),
                    "must be a valid UUID"
            ));
        } else {
            message = "Invalid request parameter";
            violations = List.of(new ApiError.FieldViolation(
                    ex.getName() == null ? "unknown" : ex.getName(),
                    "invalid value"
            ));
        }

        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message,
                request.getRequestURI(),
                violations
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ArticleNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            ArticleNotFoundException ex,
            HttpServletRequest request
    ) {
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                "Article not found",
                request.getRequestURI(),
                List.of()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }
}
