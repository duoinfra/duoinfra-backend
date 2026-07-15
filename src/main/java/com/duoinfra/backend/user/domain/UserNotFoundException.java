package com.duoinfra.backend.user.domain;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long userId) {
        super("사용자를 찾을 수 없습니다. userId=" + userId);
    }
}
