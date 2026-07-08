package com.duoinfra.backend.item;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;

import java.time.LocalDateTime;

@Schema(description = "아이템")
@Entity
public class Item {

    @Schema(description = "아이템 ID", example = "1")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Schema(description = "아이템 이름", example = "노트북")
    @Column(nullable = false, length = 100)
    private String name;

    @Schema(description = "아이템 설명", example = "15인치 노트북")
    @Column(length = 255)
    private String description;

    @Schema(description = "생성 일시", example = "2026-07-08T12:00:00")
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Item() {
    }

    public Item(String name, String description) {
        this.name = name;
        this.description = description;
    }

    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
