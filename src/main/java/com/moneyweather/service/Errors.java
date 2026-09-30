package com.moneyweather.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 서비스에서 던지는 HTTP 오류. {@code ApiExceptionHandler}가 공통 형식의 응답으로 바꾼다. */
final class Errors {
    private Errors() {
    }

    static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
