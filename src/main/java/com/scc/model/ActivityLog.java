package com.scc.model;

public record ActivityLog(
        int logId,
        int userId,
        String actionType,
        String description,
        Integer orderId,
        String timestamp
) {
    @Override
    public String toString() {
        return String.format("[%d] User ID: %d | Type: %s | Description: %s | Order: %s | Time: %s",
                logId, userId, actionType, description, orderId == null ? "-" : orderId, timestamp);
    }
}