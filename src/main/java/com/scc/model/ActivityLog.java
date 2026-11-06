package com.scc.model;

import java.sql.Timestamp;

public record ActivityLog(
        int logId,
        int userId,
        String actionType,
        String description,
        Timestamp timestamp
) {
    @Override
    public String toString() {
        return String.format("[%d] User ID: %d | Type: %s | Description: %s | Time: %s",
                logId, userId, actionType, description, timestamp);
    }
}