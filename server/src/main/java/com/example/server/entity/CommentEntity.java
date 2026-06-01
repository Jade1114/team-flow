package com.example.server.entity;

import java.time.LocalDateTime;

public class CommentEntity {
    public long id;
    public long taskId;
    public long projectId;
    public long userId;
    public String content;
    public LocalDateTime deletedAt;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public CommentEntity(long id, long taskId, long projectId, long userId, String content, LocalDateTime deletedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.taskId = taskId;
        this.projectId = projectId;
        this.userId = userId;
        this.content = content;
        this.deletedAt = deletedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
