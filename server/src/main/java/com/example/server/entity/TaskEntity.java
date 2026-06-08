package com.example.server.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class TaskEntity {
    public long id;
    public long projectId;
    public String title;
    public String description;
    public String status;
    public String priority;
    public Long assigneeId;
    public long creatorId;
    public LocalDate dueDate;
    public int sortOrder;
    public String labels;
    public LocalDateTime deletedAt;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public TaskEntity(long id, long projectId, String title, String description, String status, String priority, Long assigneeId, long creatorId, LocalDate dueDate, int sortOrder, String labels, LocalDateTime deletedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.projectId = projectId;
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.assigneeId = assigneeId;
        this.creatorId = creatorId;
        this.dueDate = dueDate;
        this.sortOrder = sortOrder;
        this.labels = labels;
        this.deletedAt = deletedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
