package com.example.server.entity;

import java.time.LocalDateTime;

public class UserEntity {
    public long id;
    public String name;
    public String email;
    public String passwordHash;
    public String avatarUrl;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public UserEntity(long id, String name, String email, String passwordHash, String avatarUrl, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.avatarUrl = avatarUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
