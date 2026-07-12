package com.duoinfra.backend.user.application;

public record LoginResult(String accessToken, String tokenType, String email, String nickname) {
}
