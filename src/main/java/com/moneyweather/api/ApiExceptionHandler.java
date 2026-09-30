package com.moneyweather.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> fields.put(error.getField(), error.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "요청 값이 올바르지 않습니다.", request.getRequestURI(), fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Map<String, Object>> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage(), request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "요청 본문을 읽을 수 없습니다. JSON 형식과 값의 타입을 확인하세요.", request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<Map<String, Object>> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "필수 파라미터가 없습니다: " + ex.getParameterName(), request.getRequestURI(),
                Map.of(ex.getParameterName(), "필수 값입니다."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "파라미터 형식이 올바르지 않습니다: " + ex.getName(), request.getRequestURI(),
                Map.of(ex.getName(), "형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "지원하지 않는 HTTP 메서드입니다: " + ex.getMethod(), request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "존재하지 않는 경로입니다.", request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Map<String, Object>> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "다른 요청이 같은 데이터를 먼저 수정했습니다. 다시 시도하세요.", request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, Object>> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return error(HttpStatus.CONFLICT, "DATA_CONFLICT", "다른 데이터와 충돌해 처리할 수 없습니다.", request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return error(status, status.name(), ex.getReason(), request.getRequestURI(), Map.of());
    }

    /** 예상하지 못한 오류. 내부 메시지는 응답에 싣지 않고 서버 로그로만 남긴다. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> handleUnknown(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.", request.getRequestURI(), Map.of());
    }

    static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message, String path, Map<String, ?> fields) {
        return ResponseEntity.status(status).body(body(status, code, message, path, fields));
    }

    /** Security 필터처럼 컨트롤러 밖에서 오류를 쓸 때도 같은 형식을 쓰도록 공개한다. */
    public static Map<String, Object> body(HttpStatus status, String code, String message, String path, Map<String, ?> fields) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("code", code);
        body.put("message", message);
        body.put("path", path);
        body.put("fields", fields);
        return body;
    }
}
