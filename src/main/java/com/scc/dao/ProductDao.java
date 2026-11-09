package com.scc.dao;

import com.scc.model.Product;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductDao {

    private static final String SELECT =
            "SELECT product_id, product_name, category, price, stock_quantity, status FROM products ";

    // CREATE
    public boolean addProduct(String name, String category, double price, int stock) {
        String sql = "INSERT INTO products(product_name, category, price, stock_quantity) VALUES(?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, category);
            ps.setDouble(3, price);
            ps.setInt(4, stock);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                System.err.println("[ERROR] A product with that name already exists.");
            } else {
                System.err.println("[ERROR] Could not add product: " + e.getMessage());
            }
            return false;
        }
    }

    // READ
    /** @param includeDiscontinued false hides DISCONTINUED products. */
    public List<Product> getAll(boolean includeDiscontinued) {
        String where = includeDiscontinued ? "" : "WHERE status <> 'DISCONTINUED' ";
        List<Product> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(SELECT + where + "ORDER BY category, product_name")) {
            while (rs.next()) {
                list.add(map(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching products: " + e.getMessage());
        }
        return list;
    }

    public Product getById(int id) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT + "WHERE product_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching product: " + e.getMessage());
        }
        return null;
    }

    // UPDATE
    public boolean updatePrice(int id, double price) {
        return updateOne("UPDATE products SET price = ? WHERE product_id = ?", price, id);
    }

    public boolean updateStock(int id, int stock) {
        return updateOne("UPDATE products SET stock_quantity = ? WHERE product_id = ?", stock, id);
    }

    public boolean updateStatus(int id, String status) {
        String sql = "UPDATE products SET status = ? WHERE product_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ERROR] Could not update status: " + e.getMessage());
            return false;
        }
    }

    private boolean updateOne(String sql, Number value, int id) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (value instanceof Integer i) {
                ps.setInt(1, i);
            } else {
                ps.setDouble(1, value.doubleValue());
            }
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ERROR] Could not update product: " + e.getMessage());
            return false;
        }
    }

    // DELETE
    /**
     * Deletes a product that was never ordered; otherwise marks it DISCONTINUED so old
     * orders and reports stay correct. Returns "DELETED", "DISCONTINUED" or null on error.
     */
    public String deleteProduct(int id) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            boolean used;
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM order_items WHERE product_id = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    used = rs.next() && rs.getInt(1) > 0;
                }
            }
            String sql = used ? "UPDATE products SET status = 'DISCONTINUED' WHERE product_id = ?"
                    : "DELETE FROM products WHERE product_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                if (ps.executeUpdate() > 0) {
                    return used ? "DISCONTINUED" : "DELETED";
                }
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] Could not remove product: " + e.getMessage());
        }
        return null;
    }

    private Product map(ResultSet rs) throws SQLException {
        return new Product(
                rs.getInt("product_id"),
                rs.getString("product_name"),
                rs.getString("category"),
                rs.getDouble("price"),
                rs.getInt("stock_quantity"),
                rs.getString("status"));
    }
}