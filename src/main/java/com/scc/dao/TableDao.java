package com.scc.dao;

import com.scc.model.DiningTable;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TableDao {

    private static final String SELECT =
            "SELECT table_id, table_number, capacity, table_status FROM restaurant_tables ";

    // CREATE
    public boolean addTable(int tableNumber, int capacity) {
        String sql = "INSERT INTO restaurant_tables(table_number, capacity) VALUES(?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tableNumber);
            ps.setInt(2, capacity);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                System.err.println("[ERROR] Table number already exists.");
            } else {
                System.err.println("[ERROR] Could not add table: " + e.getMessage());
            }
            return false;
        }
    }

    // READ
    /** @param status AVAILABLE / OCCUPIED, or null for all tables. */
    public List<DiningTable> getAll(String status) {
        List<DiningTable> list = new ArrayList<>();
        String sql = SELECT + (status == null ? "" : "WHERE table_status = ? ") + "ORDER BY table_number";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (status != null) {
                ps.setString(1, status);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching tables: " + e.getMessage());
        }
        return list;
    }

    public DiningTable getById(int id) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT + "WHERE table_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching table: " + e.getMessage());
        }
        return null;
    }

    public boolean hasOpenOrder(int tableId) {
        String sql = "SELECT COUNT(*) FROM orders WHERE table_id = ? AND order_status = 'OPEN'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tableId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error checking table orders: " + e.getMessage());
            return true;
        }
    }

    // UPDATE
    public boolean updateStatus(int tableId, String status) {
        return exec("UPDATE restaurant_tables SET table_status = ? WHERE table_id = ?", status, tableId);
    }

    public boolean updateCapacity(int tableId, int capacity) {
        String sql = "UPDATE restaurant_tables SET capacity = ? WHERE table_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, capacity);
            ps.setInt(2, tableId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ERROR] Could not update capacity: " + e.getMessage());
            return false;
        }
    }

    private boolean exec(String sql, String text, int id) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, text);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ERROR] Could not update table: " + e.getMessage());
            return false;
        }
    }

    // DELETE (only tables that never had an order)
    public boolean deleteTable(int tableId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM orders WHERE table_id = ?")) {
                ps.setInt(1, tableId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        System.err.println("[ERROR] This table already has order history and cannot be deleted.");
                        return false;
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM restaurant_tables WHERE table_id = ?")) {
                ps.setInt(1, tableId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] Could not delete table: " + e.getMessage());
            return false;
        }
    }

    private DiningTable map(ResultSet rs) throws SQLException {
        return new DiningTable(
                rs.getInt("table_id"),
                rs.getInt("table_number"),
                rs.getInt("capacity"),
                rs.getString("table_status"));
    }
}