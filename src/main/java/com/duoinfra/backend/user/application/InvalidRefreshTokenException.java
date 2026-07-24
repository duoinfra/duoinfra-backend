package com.duoinfra.backend.user.application;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("Refresh Token이 유효하지 않습니다.");
    }
}
