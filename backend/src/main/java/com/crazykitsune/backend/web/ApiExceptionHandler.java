package com.crazykitsune.backend.web;

import com.crazykitsune.backend.generated.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return ResponseEntity.status(status).body(errorResponse(status, exception.getReason(), request.getRequestURI(), List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
            .map(this::toDetail)
            .toList();
        return ResponseEntity.badRequest().body(errorResponse(
            HttpStatus.BAD_REQUEST,
            "La solicitud no cumple el contrato definido.",
            request.getRequestURI(),
            details
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Ocurrio un error interno no controlado.",
            request.getRequestURI(),
            List.of(exception.getClass().getSimpleName())
        ));
    }

    private String toDetail(FieldError fieldError) {
        return fieldError.getField() + ": " + (fieldError.getDefaultMessage() == null ? "valor invalido" : fieldError.getDefaultMessage());
    }

    private ErrorResponse errorResponse(HttpStatus status, String message, String path, List<String> details) {
        return new ErrorResponse()
            .timestamp(Instant.now().atOffset(java.time.ZoneOffset.UTC))
            .status(status.value())
            .error(status.getReasonPhrase())
            .message(message)
            .path(path)
            .details(details);
    }
}