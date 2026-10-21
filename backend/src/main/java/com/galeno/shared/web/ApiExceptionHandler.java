package com.galeno.shared.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Todos los errores salen en formato RFC 9457 ({@code application/problem+json}). */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiProblem.class)
    ProblemDetail apiProblem(ApiProblem e) {
        return ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
    }

    /** Agrega {@code errores: {campo: mensaje}} para que el front marque cada campo. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errores.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail body = ex.getBody();
        body.setDetail("La solicitud tiene datos inválidos");
        body.setProperty("errores", errores);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /** Errores de API con su código HTTP. */
    public static class ApiProblem extends RuntimeException {

        private final HttpStatus status;

        public ApiProblem(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        public HttpStatus status() {
            return status;
        }

        public static ApiProblem notFound(String message) {
            return new ApiProblem(HttpStatus.NOT_FOUND, message);
        }

        public static ApiProblem conflict(String message) {
            return new ApiProblem(HttpStatus.CONFLICT, message);
        }

        public static ApiProblem unauthorized(String message) {
            return new ApiProblem(HttpStatus.UNAUTHORIZED, message);
        }

        public static ApiProblem forbidden(String message) {
            return new ApiProblem(HttpStatus.FORBIDDEN, message);
        }

        public static ApiProblem badRequest(String message) {
            return new ApiProblem(HttpStatus.BAD_REQUEST, message);
        }

        public static ApiProblem tooManyRequests(String message) {
            return new ApiProblem(HttpStatus.TOO_MANY_REQUESTS, message);
        }
    }
}
