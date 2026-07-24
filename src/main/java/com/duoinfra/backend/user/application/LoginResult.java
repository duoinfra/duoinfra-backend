package com.duoinfra.backend.user.application;

public record LoginResult(String accessToken, String refreshToken, String tokenType, String email, String nickname) {
}
