package com.example.accounting.common;

import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Mono<ApiResponse<?>> handleBusinessException(BusinessException ex) {
        return Mono.just(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public Mono<ApiResponse<?>> handleAuthenticationException(AuthenticationException ex) {
        return Mono.just(ApiResponse.error(401, ex.getMessage()));
    }

    @ExceptionHandler(ServerWebInputException.class)
    public Mono<ApiResponse<?>> handleServerWebInputException(ServerWebInputException ex) {
        return Mono.just(ApiResponse.error(400, "请求参数错误: " + ex.getReason()));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ApiResponse<?>> handleWebExchangeBindException(WebExchangeBindException ex) {
        String message = ex.getAllErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("请求参数错误");
        return Mono.just(ApiResponse.error(400, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Mono<ApiResponse<?>> handleConstraintViolationException(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse("请求参数错误");
        return Mono.just(ApiResponse.error(400, message));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ApiResponse<?>> handleException(Exception ex) {
        return Mono.just(ApiResponse.error(500, "系统内部错误: " + ex.getMessage()));
    }
}
