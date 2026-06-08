package com.example.server.entity;

import java.time.LocalDateTime;

public class CommentMentionEntity {
    public long id;
    public long commentId;
    public long taskId;
    public long projectId;
    public long mentionedUserId;
    public LocalDateTime readAt;
    public LocalDateTime createdAt;

    public CommentMentionEntity(long id, long commentId, long taskId, long projectId, long mentionedUserId, LocalDateTime readAt, LocalDateTime createdAt) {
        this.id = id;
        this.commentId = commentId;
        this.taskId = taskId;
        this.projectId = projectId;
        this.mentionedUserId = mentionedUserId;
        this.readAt = readAt;
        this.createdAt = createdAt;
    }
}
