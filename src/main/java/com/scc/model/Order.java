package com.scc.model;

public record Order(int orderId, int userId, String staffName, int tableId, int tableNumber,
                    String orderDate, double total, String status, double paidAmount, String paidDate) {
}