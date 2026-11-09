package com.scc.model;

public record Product(int productId, String name, String category, double price, int stock, String status) {
}