package com.scc.dao;

import com.scc.model.ActivityLog;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ActivityLogDao {

    private static final String SELECT =
            "SELECT log_id, user_id, action_type, description, order_id, timestamp FROM activity_logs ";

    public void logAction(int userId, String actionType, String description) {
        logAction(userId, actionType, description, null);
    }

    public void logAction(int userId, String actionType, String description, Integer orderId) {
        String sql = "INSERT INTO activity_logs (user_id, action_type, description, order_id) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            pstmt.setString(2, actionType);
            pstmt.setString(3, description);
            if (orderId == null) {
                pstmt.setNull(4, Types.INTEGER);
            } else {
                pstmt.setInt(4, orderId);
            }
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error recording activity log: " + e.getMessage());
        }
    }

    public List<ActivityLog> getAllLogs() {
        List<ActivityLog> logs = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT + "ORDER BY log_id DESC")) {
            while (rs.next()) {
                logs.add(map(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching all activity logs: " + e.getMessage());
        }
        return logs;
    }

    public List<ActivityLog> getLogsByUserId(int userId) {
        List<ActivityLog> logs = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT + "WHERE user_id = ? ORDER BY log_id DESC")) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(map(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching user activity logs: " + e.getMessage());
        }
        return logs;
    }

    private ActivityLog map(ResultSet rs) throws SQLException {
        int orderId = rs.getInt("order_id");
        Integer orderRef = rs.wasNull() ? null : orderId;
        return new ActivityLog(
                rs.getInt("log_id"),
                rs.getInt("user_id"),
                rs.getString("action_type"),
                rs.getString("description"),
                orderRef,
                rs.getString("timestamp"));
    }

    public boolean clearLogs() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM activity_logs");
            System.out.println(" -> All historical activity logs cleared safely.");
            return true;
        } catch (SQLException e) {
            System.err.println("Error clearing activity logs: " + e.getMessage());
        }
        return false;
    }
}