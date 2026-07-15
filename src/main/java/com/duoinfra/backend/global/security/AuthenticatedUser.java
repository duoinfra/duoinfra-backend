package com.duoinfra.backend.global.security;

import com.duoinfra.backend.user.domain.Role;

public record AuthenticatedUser(Long userId, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
