package com.duoinfra.backend.user.application;

public class InvalidAccessTokenException extends RuntimeException {
    public InvalidAccessTokenException() {
        super("Access Token이 유효하지 않습니다.");
    }
}
