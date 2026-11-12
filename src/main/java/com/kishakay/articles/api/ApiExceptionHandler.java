package com.kishakay.articles.api;

import com.kishakay.articles.article.exception.ArticleNotEditableException;
import com.kishakay.articles.article.exception.ArticleNotFoundException;
import com.kishakay.articles.article.exception.ArticleVersionConflictException;
import com.kishakay.articles.article.exception.IdempotencyKeyConflictException;
import com.kishakay.articles.article.exception.InvalidIdempotencyKeyException;
import com.kishakay.articles.article.exception.InvalidPaginationException;
import com.kishakay.articles.article.exception.SummaryPreviewNotAllowedException;
import com.kishakay.articles.article.exception.SummaryProviderUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
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

    @ExceptionHandler(ArticleNotEditableException.class)
    public ResponseEntity<ApiError> handleNotEditable(
            ArticleNotEditableException ex,
            HttpServletRequest request
    ) {
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                "Published articles cannot be edited",
                request.getRequestURI(),
                List.of()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler({
            ArticleVersionConflictException.class,
            OptimisticLockingFailureException.class
    })
    public ResponseEntity<ApiError> handleVersionConflict(
            RuntimeException ex,
            HttpServletRequest request
    ) {
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                "Article version conflict",
                request.getRequestURI(),
                List.of()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ApiError> handleIdempotencyConflict(
            IdempotencyKeyConflictException ex,
            HttpServletRequest request
    ) {
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                "Idempotency key reused with a different payload",
                request.getRequestURI(),
                List.of()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    public ResponseEntity<ApiError> handleInvalidIdempotencyKey(
            InvalidIdempotencyKeyException ex,
            HttpServletRequest request
    ) {
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Invalid Idempotency-Key",
                request.getRequestURI(),
                List.of(new ApiError.FieldViolation("Idempotency-Key", ex.getDetail()))
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(InvalidPaginationException.class)
    public ResponseEntity<ApiError> handleInvalidPagination(
            InvalidPaginationException ex,
            HttpServletRequest request
    ) {
        List<ApiError.FieldViolation> violations = ex.getViolations().stream()
                .map(v -> new ApiError.FieldViolation(v.field(), v.message()))
                .toList();

        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Invalid pagination parameters",
                request.getRequestURI(),
                violations
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(SummaryPreviewNotAllowedException.class)
    public ResponseEntity<ApiError> handleSummaryPreviewNotAllowed(
            SummaryPreviewNotAllowedException ex,
            HttpServletRequest request
    ) {
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                "Summary preview is only available for draft articles",
                request.getRequestURI(),
                List.of()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(SummaryProviderUnavailableException.class)
    public ResponseEntity<ApiError> handleSummaryProviderUnavailable(
            SummaryProviderUnavailableException ex,
            HttpServletRequest request
    ) {
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                "Summary provider unavailable",
                request.getRequestURI(),
                List.of()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
