package com.example.server.entity;

import java.time.LocalDateTime;

public class ProjectMemberEntity {
    public long id;
    public long projectId;
    public long userId;
    public String role;
    public LocalDateTime joinedAt;
    public LocalDateTime deletedAt;

    public ProjectMemberEntity(long id, long projectId, long userId, String role, LocalDateTime joinedAt, LocalDateTime deletedAt) {
        this.id = id;
        this.projectId = projectId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = joinedAt;
        this.deletedAt = deletedAt;
    }
}
