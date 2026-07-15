package com.duoinfra.backend.user.application;

public record SignupCommand(String email, String password, String nickname, boolean termsAgreed) {
}
