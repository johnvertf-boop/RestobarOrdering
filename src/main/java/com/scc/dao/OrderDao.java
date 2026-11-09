package com.scc.dao;

import com.scc.model.Order;
import com.scc.model.OrderItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OrderDao {

    private static final String SELECT_ORDER = """
            SELECT o.order_id, o.user_id, u.username, o.table_id, t.table_number, o.order_date,
                   o.total_amount, o.order_status, o.paid_amount, o.paid_date
            FROM orders o
            JOIN users u ON u.id = o.user_id
            JOIN restaurant_tables t ON t.table_id = o.table_id
            """;

    @FunctionalInterface
    private interface Work<T> {
        T run(Connection conn) throws SQLException;
    }

    /**
     * Runs the work as one database transaction (all or nothing).
     * A broken business rule (IllegalStateException) or a database error rolls everything
     * back, prints the message, and returns failValue.
     */
    private <T> T inTransaction(Work<T> work, T failValue) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = work.run(conn);
                conn.commit();
                return result;
            } catch (IllegalStateException e) {
                conn.rollback();
                System.out.println("[ERROR] " + e.getMessage());
                return failValue;
            } catch (SQLException e) {
                conn.rollback();
                System.out.println("[ERROR] Database error: " + e.getMessage());
                return failValue;
            }
        } catch (SQLException e) {
            System.out.println("[ERROR] Database error: " + e.getMessage());
            return failValue;
        }
    }

    // ---------------------------------------------------------------- CREATE

    /** Creates an OPEN order for the table and marks it OCCUPIED. Returns the order id, or -1 if it failed. */
    public int createOrder(int userId, int tableId) {
        return inTransaction(conn -> {
            String tableStatus = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT table_status FROM restaurant_tables WHERE table_id = ?")) {
                ps.setInt(1, tableId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        tableStatus = rs.getString(1);
                    }
                }
            }
            if (tableStatus == null) {
                throw new IllegalStateException("Table not found.");
            }
            if (!"AVAILABLE".equals(tableStatus)) {
                throw new IllegalStateException("Table is not available.");
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO orders(user_id, table_id) VALUES(?, ?)")) {
                ps.setInt(1, userId);
                ps.setInt(2, tableId);
                ps.executeUpdate();
            }
            int orderId;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                rs.next();
                orderId = rs.getInt(1);
            }
            setTableStatus(conn, tableId, "OCCUPIED");
            return orderId;
        }, -1);
    }

    /** Adds a product to an open order and deducts the stock. */
    public boolean addItem(int orderId, int productId, int qty) {
        if (qty <= 0) {
            System.out.println("[ERROR] Quantity must be at least 1.");
            return false;
        }
        return inTransaction(conn -> {
            requireOpen(conn, orderId);
            String name;
            double price;
            int stock;
            String status;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT product_name, price, stock_quantity, status FROM products WHERE product_id = ?")) {
                ps.setInt(1, productId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Product not found.");
                    }
                    name = rs.getString(1);
                    price = rs.getDouble(2);
                    stock = rs.getInt(3);
                    status = rs.getString(4);
                }
            }
            if (!"AVAILABLE".equals(status)) {
                throw new IllegalStateException(name + " is not available.");
            }
            if (stock < qty) {
                throw new IllegalStateException("Not enough stock for " + name + ". Only " + stock + " left.");
            }

            int existingItem = -1;
            int existingQty = 0;
            double unitPrice = price;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT item_id, quantity, unit_price FROM order_items WHERE order_id = ? AND product_id = ?")) {
                ps.setInt(1, orderId);
                ps.setInt(2, productId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        existingItem = rs.getInt(1);
                        existingQty = rs.getInt(2);
                        unitPrice = rs.getDouble(3);
                    }
                }
            }
            if (existingItem > 0) {
                int newQty = existingQty + qty;
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE order_items SET quantity = ?, subtotal = ? WHERE item_id = ?")) {
                    ps.setInt(1, newQty);
                    ps.setDouble(2, newQty * unitPrice);
                    ps.setInt(3, existingItem);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO order_items(order_id, product_id, quantity, unit_price, subtotal) VALUES(?,?,?,?,?)")) {
                    ps.setInt(1, orderId);
                    ps.setInt(2, productId);
                    ps.setInt(3, qty);
                    ps.setDouble(4, price);
                    ps.setDouble(5, qty * price);
                    ps.executeUpdate();
                }
            }
            adjustStock(conn, productId, -qty);
            recalcTotal(conn, orderId);
            return true;
        }, false);
    }

    // ---------------------------------------------------------------- UPDATE

    public boolean updateItemQuantity(int orderId, int itemId, int newQty) {
        if (newQty <= 0) {
            System.out.println("[ERROR] Quantity must be at least 1. Use remove item instead.");
            return false;
        }
        return inTransaction(conn -> {
            requireOpen(conn, orderId);
            int productId;
            int oldQty;
            double unitPrice;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT product_id, quantity, unit_price FROM order_items WHERE item_id = ? AND order_id = ?")) {
                ps.setInt(1, itemId);
                ps.setInt(2, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Item not found in this order.");
                    }
                    productId = rs.getInt(1);
                    oldQty = rs.getInt(2);
                    unitPrice = rs.getDouble(3);
                }
            }
            int diff = newQty - oldQty;
            if (diff > 0) {
                int stock = currentStock(conn, productId);
                if (stock < diff) {
                    throw new IllegalStateException("Not enough stock. Only " + stock + " more available.");
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE order_items SET quantity = ?, subtotal = ? WHERE item_id = ?")) {
                ps.setInt(1, newQty);
                ps.setDouble(2, newQty * unitPrice);
                ps.setInt(3, itemId);
                ps.executeUpdate();
            }
            adjustStock(conn, productId, -diff);
            recalcTotal(conn, orderId);
            return true;
        }, false);
    }

    public boolean changeTable(int orderId, int newTableId) {
        return inTransaction(conn -> {
            requireOpen(conn, orderId);
            int oldTableId;
            try (PreparedStatement ps = conn.prepareStatement("SELECT table_id FROM orders WHERE order_id = ?")) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    oldTableId = rs.getInt(1);
                }
            }
            if (oldTableId == newTableId) {
                throw new IllegalStateException("The order is already on that table.");
            }
            String status = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT table_status FROM restaurant_tables WHERE table_id = ?")) {
                ps.setInt(1, newTableId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        status = rs.getString(1);
                    }
                }
            }
            if (status == null) {
                throw new IllegalStateException("Table not found.");
            }
            if (!"AVAILABLE".equals(status)) {
                throw new IllegalStateException("The new table is not available.");
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE orders SET table_id = ? WHERE order_id = ?")) {
                ps.setInt(1, newTableId);
                ps.setInt(2, orderId);
                ps.executeUpdate();
            }
            setTableStatus(conn, oldTableId, "AVAILABLE");
            setTableStatus(conn, newTableId, "OCCUPIED");
            return true;
        }, false);
    }

    /** Marks the order PAID and frees the table. Returns the change to give back, or -1 if it failed. */
    public double payOrder(int orderId, double amountPaid) {
        return inTransaction(conn -> {
            requireOpen(conn, orderId);
            double total;
            int tableId;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT total_amount, table_id FROM orders WHERE order_id = ?")) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = rs.getDouble(1);
                    tableId = rs.getInt(2);
                }
            }
            if (total <= 0) {
                throw new IllegalStateException("Cannot pay an empty order.");
            }
            if (amountPaid + 0.0001 < total) {
                throw new IllegalStateException(String.format("Payment is not enough. Total is %.2f.", total));
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE orders SET order_status = 'PAID', paid_amount = ?, "
                            + "paid_date = datetime('now','localtime') WHERE order_id = ?")) {
                ps.setDouble(1, amountPaid);
                ps.setInt(2, orderId);
                ps.executeUpdate();
            }
            setTableStatus(conn, tableId, "AVAILABLE");
            return amountPaid - total;
        }, -1.0);
    }

    // ---------------------------------------------------------------- DELETE

    public boolean removeItem(int orderId, int itemId) {
        return inTransaction(conn -> {
            requireOpen(conn, orderId);
            int productId;
            int qty;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT product_id, quantity FROM order_items WHERE item_id = ? AND order_id = ?")) {
                ps.setInt(1, itemId);
                ps.setInt(2, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Item not found in this order.");
                    }
                    productId = rs.getInt(1);
                    qty = rs.getInt(2);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM order_items WHERE item_id = ?")) {
                ps.setInt(1, itemId);
                ps.executeUpdate();
            }
            adjustStock(conn, productId, qty);
            recalcTotal(conn, orderId);
            return true;
        }, false);
    }

    /** Cancels an open order: stock is returned and the table becomes available. */
    public boolean cancelOrder(int orderId) {
        return inTransaction(conn -> {
            requireOpen(conn, orderId);
            try (PreparedStatement ps = conn.prepareStatement("""
                    UPDATE products
                    SET stock_quantity = stock_quantity +
                        (SELECT COALESCE(SUM(oi.quantity), 0) FROM order_items oi
                         WHERE oi.order_id = ? AND oi.product_id = products.product_id)
                    WHERE product_id IN (SELECT product_id FROM order_items WHERE order_id = ?)
                    """)) {
                ps.setInt(1, orderId);
                ps.setInt(2, orderId);
                ps.executeUpdate();
            }
            int tableId;
            try (PreparedStatement ps = conn.prepareStatement("SELECT table_id FROM orders WHERE order_id = ?")) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    tableId = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE orders SET order_status = 'CANCELLED' WHERE order_id = ?")) {
                ps.setInt(1, orderId);
                ps.executeUpdate();
            }
            setTableStatus(conn, tableId, "AVAILABLE");
            return true;
        }, false);
    }

    /** Permanently removes a CANCELLED order record (Admin). */
    public boolean deleteCancelledOrder(int orderId) {
        return inTransaction(conn -> {
            String status = null;
            try (PreparedStatement ps = conn.prepareStatement("SELECT order_status FROM orders WHERE order_id = ?")) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        status = rs.getString(1);
                    }
                }
            }
            if (status == null) {
                throw new IllegalStateException("Order not found.");
            }
            if (!"CANCELLED".equals(status)) {
                throw new IllegalStateException("Only cancelled orders can be removed.");
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE activity_logs SET order_id = NULL WHERE order_id = ?")) {
                ps.setInt(1, orderId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM order_items WHERE order_id = ?")) {
                ps.setInt(1, orderId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM orders WHERE order_id = ?")) {
                ps.setInt(1, orderId);
                ps.executeUpdate();
            }
            return true;
        }, false);
    }

    // ------------------------------------------------------------------ READ

    /** @param status OPEN / PAID / CANCELLED, or null for any. @param date yyyy-MM-dd or null. */
    public List<Order> getOrders(String status, String date) {
        StringBuilder sql = new StringBuilder(SELECT_ORDER).append(" WHERE 1 = 1");
        if (status != null) {
            sql.append(" AND o.order_status = ?");
        }
        if (date != null) {
            sql.append(" AND date(o.order_date) = ?");
        }
        sql.append(" ORDER BY o.order_id DESC");

        List<Order> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int i = 1;
            if (status != null) {
                ps.setString(i++, status);
            }
            if (date != null) {
                ps.setString(i, date);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching orders: " + e.getMessage());
        }
        return list;
    }

    public Order getOrder(int orderId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ORDER + " WHERE o.order_id = ?")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching order: " + e.getMessage());
        }
        return null;
    }

    public List<OrderItem> getItems(int orderId) {
        String sql = """
                SELECT oi.item_id, oi.order_id, oi.product_id, p.product_name,
                       oi.quantity, oi.unit_price, oi.subtotal
                FROM order_items oi JOIN products p ON p.product_id = oi.product_id
                WHERE oi.order_id = ? ORDER BY oi.item_id
                """;
        List<OrderItem> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new OrderItem(
                            rs.getInt("item_id"), rs.getInt("order_id"), rs.getInt("product_id"),
                            rs.getString("product_name"), rs.getInt("quantity"),
                            rs.getDouble("unit_price"), rs.getDouble("subtotal")));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching order items: " + e.getMessage());
        }
        return list;
    }

    // --------------------------------------------------------------- helpers

    private Order map(ResultSet rs) throws SQLException {
        return new Order(
                rs.getInt("order_id"), rs.getInt("user_id"), rs.getString("username"),
                rs.getInt("table_id"), rs.getInt("table_number"), rs.getString("order_date"),
                rs.getDouble("total_amount"), rs.getString("order_status"),
                rs.getDouble("paid_amount"), rs.getString("paid_date"));
    }

    private void requireOpen(Connection conn, int orderId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT order_status FROM orders WHERE order_id = ?")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Order not found.");
                }
                if (!"OPEN".equals(rs.getString(1))) {
                    throw new IllegalStateException("Order is already " + rs.getString(1) + " and cannot be changed.");
                }
            }
        }
    }

    private void setTableStatus(Connection conn, int tableId, String status) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE restaurant_tables SET table_status = ? WHERE table_id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, tableId);
            ps.executeUpdate();
        }
    }

    private void adjustStock(Connection conn, int productId, int delta) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE products SET stock_quantity = stock_quantity + ? WHERE product_id = ?")) {
            ps.setInt(1, delta);
            ps.setInt(2, productId);
            ps.executeUpdate();
        }
    }

    private int currentStock(Connection conn, int productId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT stock_quantity FROM products WHERE product_id = ?")) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private void recalcTotal(Connection conn, int orderId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE orders SET total_amount = "
                        + "(SELECT COALESCE(SUM(subtotal), 0) FROM order_items WHERE order_id = ?) "
                        + "WHERE order_id = ?")) {
            ps.setInt(1, orderId);
            ps.setInt(2, orderId);
            ps.executeUpdate();
        }
    }
}