package com.example.server.entity;

import java.time.LocalDateTime;

public class InviteEntity {
    public long id;
    public long projectId;
    public String email;
    public String role;
    public String status;
    public long invitedBy;
    public LocalDateTime createdAt;
    public LocalDateTime handledAt;

    public InviteEntity(long id, long projectId, String email, String role, String status, long invitedBy, LocalDateTime createdAt, LocalDateTime handledAt) {
        this.id = id;
        this.projectId = projectId;
        this.email = email;
        this.role = role;
        this.status = status;
        this.invitedBy = invitedBy;
        this.createdAt = createdAt;
        this.handledAt = handledAt;
    }
}
