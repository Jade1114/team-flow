package com.example.server.entity;

import java.time.LocalDateTime;

public class TaskActivityEntity {
    public long id;
    public long projectId;
    public Long taskId;
    public long userId;
    public String type;
    public String content;
    public String oldValue;
    public String newValue;
    public LocalDateTime createdAt;

    public TaskActivityEntity(long id, long projectId, Long taskId, long userId, String type, String content, String oldValue, String newValue, LocalDateTime createdAt) {
        this.id = id;
        this.projectId = projectId;
        this.taskId = taskId;
        this.userId = userId;
        this.type = type;
        this.content = content;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.createdAt = createdAt;
    }
}
