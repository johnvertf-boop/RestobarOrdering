package com.scc.dao;

import com.scc.model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDao {

    public UserDao() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            defaultAdminGenerator(conn);
        } catch (SQLException e) {
            System.err.println("Error default seeding admin: " + e.getMessage());
        }
    }

    public void defaultAdminGenerator(Connection conn) {
        String checkSql = "SELECT COUNT(*) FROM users WHERE roles = 'ADMIN'";
        String insertSql = "INSERT INTO users(username, password, roles, status) VALUES('admin','admin123','ADMIN','APPROVED')";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(checkSql)) {
            if (rs.next() && rs.getInt(1) == 0) {
                try (Statement ins = conn.createStatement()) {
                    ins.executeUpdate(insertSql);
                }
                System.out.println(" -> Default Admin created <-");
            }
        } catch (SQLException e) {
            System.err.println("Error seeding default admin: " + e.getMessage());
        }
    }

    /** Self-registration: account waits for admin approval. */
    public boolean registerUser(String username, String password, String role) {
        return insertUser(username, password, role, "PENDING", true);
    }

    /** Admin-created account: approved immediately. */
    public boolean addUser(String username, String password, String role) {
        return insertUser(username, password, role, "APPROVED", false);
    }

    private boolean insertUser(String username, String password, String role, String status, boolean showMessage) {
        String sql = "INSERT INTO users(username, password, roles, status) VALUES(?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.setString(3, role.toUpperCase());
            pstmt.setString(4, status);
            pstmt.executeUpdate();
            if (showMessage) {
                System.out.println("SUCCESSFUL REGISTRATION, waiting for approval.");
            }
            return true;
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                System.err.println("FAILED: Username already exists.");
            } else {
                System.err.println("FAILED: " + e.getMessage());
            }
            return false;
        }
    }

    public User login(String username, String password) {
        String sql = "SELECT id, username, password, roles, status FROM users WHERE username = ? AND password = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String status = rs.getString("status");
                    if ("PENDING".equalsIgnoreCase(status)) {
                        System.out.println("> Account is pending. Waiting for approval. < ");
                        return null;
                    }
                    if ("INACTIVE".equalsIgnoreCase(status)) {
                        System.out.println("> Account is deactivated. Contact the administrator. <");
                        return null;
                    }
                    return mapUser(rs);
                } else {
                    System.out.println(" > Invalid Username or Password");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error During login: " + e.getMessage());
        }
        return null;
    }

    private User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("id"),
                rs.getString("username"),
                rs.getString("password"),
                rs.getString("roles"),
                rs.getString("status"));
    }

    public List<User> getAllUsers() {
        return queryUsers("SELECT id, username, password, roles, status FROM users ORDER BY id");
    }

    public List<User> getPendingUser() {
        return queryUsers("SELECT id, username, password, roles, status FROM users WHERE status = 'PENDING' ORDER BY id");
    }

    private List<User> queryUsers(String sql) {
        List<User> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapUser(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching users: " + e.getMessage());
        }
        return list;
    }

    public boolean approveUser(int userID) {
        String sql = "UPDATE users SET status = 'APPROVED' WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userID);
            int rowsUpdated = pstmt.executeUpdate();

            if (rowsUpdated > 0) {
                System.out.println(" -> USER ID: " + userID + " APPROVED SUCCESSFULLY!");
                return true;
            } else {
                System.out.println("ID NOT FOUND.");
            }
        } catch (SQLException e) {
            System.out.println("Error Approving User: " + e.getMessage());
        }
        return false;
    }

    public boolean updateUserRoleAndStatus(int userId, String newRole, String newStatus) {
        String sql = "UPDATE users SET roles = ?, status = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newRole.toUpperCase());
            pstmt.setString(2, newStatus.toUpperCase());
            pstmt.setInt(3, userId);

            int rowsUpdated = pstmt.executeUpdate();
            if (rowsUpdated > 0) {
                System.out.println(" -> USER ID: " + userId + " UPDATED SUCCESSFULLY!");
                return true;
            } else {
                System.out.println("[ERROR] User ID not found.");
            }
        } catch (SQLException e) {
            System.err.println("Error updating user role and status: " + e.getMessage());
        }
        return false;
    }

    /**
     * Removes a user. Accounts that already have orders or activity logs are only
     * deactivated so that history is preserved. Returns "DELETED", "DEACTIVATED" or null.
     */
    public String deleteUser(int userId) {
        String usedSql = "SELECT (SELECT COUNT(*) FROM orders WHERE user_id = ?) + "
                + "(SELECT COUNT(*) FROM activity_logs WHERE user_id = ?)";
        try (Connection conn = DatabaseConnection.getConnection()) {
            boolean used;
            try (PreparedStatement ps = conn.prepareStatement(usedSql)) {
                ps.setInt(1, userId);
                ps.setInt(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    used = rs.next() && rs.getInt(1) > 0;
                }
            }
            String sql = used ? "UPDATE users SET status = 'INACTIVE' WHERE id = ?"
                    : "DELETE FROM users WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                if (ps.executeUpdate() > 0) {
                    return used ? "DEACTIVATED" : "DELETED";
                }
            }
        } catch (SQLException e) {
            System.err.println("Error deleting user: " + e.getMessage());
        }
        return null;
    }
}