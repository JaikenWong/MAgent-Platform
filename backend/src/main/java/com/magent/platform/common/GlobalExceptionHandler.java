package com.magent.platform.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> handleBiz(BizException e, HttpServletRequest req) {
        log.warn("BIZ {} {} -> {} {}", req.getMethod(), req.getRequestURI(), e.getCode(), e.getMessage());
        return ResponseEntity.status(e.getCode() >= 500 ? HttpStatus.INTERNAL_SERVER_ERROR
                : HttpStatus.BAD_REQUEST).body(R.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst().map(f -> f.getField() + ": " + f.getDefaultMessage())
                .orElse("validation failed");
        log.warn("VALID {}", msg);
        return ResponseEntity.badRequest().body(R.fail(422, msg));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<R<Void>> handleAuth(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(R.fail(401, "unauthorized"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<R<Void>> handleDeny(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(R.fail(403, "forbidden"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleOther(Exception e) {
        log.error("UNEXPECTED", e);
        return ResponseEntity.internalServerError().body(R.fail(500, e.getMessage()));
    }
}