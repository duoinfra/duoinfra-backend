package com.duoinfra.backend.user.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider provider;

    @Column
    private String providerId;

    @Column(nullable = false)
    private boolean termsAgreed;

    @Column(nullable = false)
    private LocalDateTime termsAgreedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected User() {}

    public User(String email, String password, String nickname, boolean termsAgreed) {
        if (!termsAgreed) {
            throw new IllegalArgumentException("이용약관에 동의해야 회원가입할 수 있습니다.");
        }
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.role = Role.USER;
        this.provider = AuthProvider.LOCAL;
        this.providerId = null;
        this.termsAgreed = true;
        LocalDateTime now = LocalDateTime.now();
        this.termsAgreedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getNickname() { return nickname; }
    public Role getRole() { return role; }
    public AuthProvider getProvider() { return provider; }
    public String getProviderId() { return providerId; }
    public boolean isTermsAgreed() { return termsAgreed; }
    public LocalDateTime getTermsAgreedAt() { return termsAgreedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
