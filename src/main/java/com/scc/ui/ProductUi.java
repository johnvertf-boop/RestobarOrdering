package com.scc.ui;

import com.scc.dao.ActivityLogDao;
import com.scc.dao.ProductDao;
import com.scc.model.Product;
import com.scc.model.User;

import java.util.List;

public class ProductUi {
    private final InputHelper in;
    private final ProductDao productDao;
    private final ActivityLogDao logDao;

    public ProductUi(InputHelper in, ProductDao productDao, ActivityLogDao logDao) {
        this.in = in;
        this.productDao = productDao;
        this.logDao = logDao;
    }

    public static void printProducts(List<Product> products, String title) {
        System.out.println("\n=================== " + title + " ===================");
        if (products.isEmpty()) {
            System.out.println("-> No Records Found");
            return;
        }
        String border = "+-----+------------------------------+------------+------------+-------+--------------+";
        System.out.println(border);
        System.out.printf("| %-3s | %-28s | %-10s | %10s | %5s | %-12s |%n",
                "ID", "Product", "Category", "Price", "Stock", "Status");
        System.out.println(border);
        for (Product p : products) {
            System.out.printf("| %-3d | %-28s | %-10s | %10.2f | %5d | %-12s |%n",
                    p.productId(), cut(p.name(), 28), cut(p.category(), 10), p.price(), p.stock(), p.status());
        }
        System.out.println(border);
    }

    /** Read-only menu list (used by Cashier and Waiter). */
    public void viewMenu() {
        printProducts(productDao.getAll(false), "FOOD AND BEVERAGE MENU");
    }

    /** Full CRUD screen for the Admin. */
    public void manage(User admin) {
        boolean back = false;
        while (!back) {
            System.out.println("\n----------------------------------");
            System.out.println("     >>> PRODUCT MANAGEMENT <<<   ");
            System.out.println("----------------------------------");
            System.out.println("1. View All Products");
            System.out.println("2. Add New Product");
            System.out.println("3. Update Product Price");
            System.out.println("4. Update Product Stock");
            System.out.println("5. Set Product Status (Available / Unavailable)");
            System.out.println("6. Remove Product");
            System.out.println("0. Return to Main Dashboard");
            String choice = in.readLine("Choose an Option: ");
            switch (choice) {
                case "1" -> printProducts(productDao.getAll(true), "ALL PRODUCTS");
                case "2" -> addProduct(admin);
                case "3" -> updatePrice(admin);
                case "4" -> updateStock(admin);
                case "5" -> updateStatus(admin);
                case "6" -> removeProduct(admin);
                case "0" -> back = true;
                default -> System.out.println("-> [ERROR] Invalid Option.");
            }
        }
    }

    private void addProduct(User admin) {
        String name = in.readRequired("Product name: ");
        String category = chooseCategory();
        double price = in.readPositiveDouble("Price: ");
        int stock = in.readInt("Starting stock quantity: ", 0);
        if (productDao.addProduct(name, category, price, stock)) {
            System.out.println(" -> Product added successfully!");
            logDao.logAction(admin.id(), "PRODUCT_ADDED", "Added product '" + name + "' at " + price);
        }
    }

    private String chooseCategory() {
        while (true) {
            String c = in.readLine("Category [1] Food [2] Beverage [3] Other: ");
            switch (c) {
                case "1" -> { return "Food"; }
                case "2" -> { return "Beverage"; }
                case "3" -> { return in.readRequired("Enter category name: "); }
                default -> System.out.println("[ERROR] Choose 1, 2 or 3.");
            }
        }
    }

    private Product pickProduct() {
        printProducts(productDao.getAll(true), "ALL PRODUCTS");
        int id = in.readInt("Enter Product ID (or 0 to cancel): ");
        if (id == 0) {
            System.out.println("-> Operation Cancelled by the User");
            return null;
        }
        Product p = productDao.getById(id);
        if (p == null) {
            System.out.println("[ERROR] Product ID not found.");
        }
        return p;
    }

    private void updatePrice(User admin) {
        Product p = pickProduct();
        if (p == null) {
            return;
        }
        double price = in.readPositiveDouble("New price for " + p.name() + " (current " + p.price() + "): ");
        if (productDao.updatePrice(p.productId(), price)) {
            System.out.println(" -> Price updated.");
            logDao.logAction(admin.id(), "PRODUCT_UPDATED",
                    "Price of '" + p.name() + "' changed from " + p.price() + " to " + price);
        }
    }

    private void updateStock(User admin) {
        Product p = pickProduct();
        if (p == null) {
            return;
        }
        int stock = in.readInt("New stock quantity for " + p.name() + " (current " + p.stock() + "): ", 0);
        if (productDao.updateStock(p.productId(), stock)) {
            System.out.println(" -> Stock updated.");
            logDao.logAction(admin.id(), "PRODUCT_UPDATED",
                    "Stock of '" + p.name() + "' changed from " + p.stock() + " to " + stock);
        }
    }

    private void updateStatus(User admin) {
        Product p = pickProduct();
        if (p == null) {
            return;
        }
        String choice = in.readLine("New status [1] AVAILABLE [2] UNAVAILABLE: ");
        String status = switch (choice) {
            case "1" -> "AVAILABLE";
            case "2" -> "UNAVAILABLE";
            default -> null;
        };
        if (status == null) {
            System.out.println("[ERROR] Invalid choice.");
            return;
        }
        if (productDao.updateStatus(p.productId(), status)) {
            System.out.println(" -> Status updated.");
            logDao.logAction(admin.id(), "PRODUCT_UPDATED", "Status of '" + p.name() + "' set to " + status);
        }
    }

    private void removeProduct(User admin) {
        Product p = pickProduct();
        if (p == null) {
            return;
        }
        if (!in.confirm("Remove '" + p.name() + "'?")) {
            System.out.println("-> Remove cancelled.");
            return;
        }
        String result = productDao.deleteProduct(p.productId());
        if ("DELETED".equals(result)) {
            System.out.println(" -> Product deleted.");
        } else if ("DISCONTINUED".equals(result)) {
            System.out.println(" -> Product already used in past orders, so it was marked DISCONTINUED instead.");
        } else {
            return;
        }
        logDao.logAction(admin.id(), "PRODUCT_REMOVED", "Product '" + p.name() + "' " + result.toLowerCase());
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "." : s;
    }
}