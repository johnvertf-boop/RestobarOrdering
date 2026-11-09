package com.scc.model;

public record OrderItem(int itemId, int orderId, int productId, String productName,
                        int quantity, double unitPrice, double subtotal) {
}