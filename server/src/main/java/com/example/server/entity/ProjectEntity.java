package com.example.server.entity;

import java.time.LocalDateTime;

public class ProjectEntity {
    public long id;
    public String name;
    public String description;
    public String status;
    public long ownerId;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public ProjectEntity(long id, String name, String description, String status, long ownerId, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.status = status;
        this.ownerId = ownerId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
