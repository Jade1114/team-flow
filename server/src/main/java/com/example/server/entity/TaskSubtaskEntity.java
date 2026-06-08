package com.example.server.entity;

import java.time.LocalDateTime;

public record TaskSubtaskEntity(
        long id,
        long taskId,
        String title,
        boolean completed,
        int sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
