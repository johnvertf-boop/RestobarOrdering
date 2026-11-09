package com.scc.ui;

import com.scc.dao.*;
import com.scc.model.DiningTable;
import com.scc.model.Order;
import com.scc.model.OrderItem;
import com.scc.model.Product;
import com.scc.model.User;

import java.time.LocalDate;
import java.util.List;

/**
 * Order screens shared by Admin, Cashier and Waiter. What each role can do:
 *  - WAITER : create orders, edit / cancel their own open orders
 *  - CASHIER: create orders, edit / cancel any open order, take payment
 *  - ADMIN  : view orders and history, cancel open orders, remove cancelled orders
 */
public class OrderUi {
    private final InputHelper in;
    private final OrderDao orderDao;
    private final ProductDao productDao;
    private final TableDao tableDao;
    private final ActivityLogDao logDao;
    private final User user;

    public OrderUi(InputHelper in, OrderDao orderDao, ProductDao productDao,
                   TableDao tableDao, ActivityLogDao logDao, User user) {
        this.in = in;
        this.orderDao = orderDao;
        this.productDao = productDao;
        this.tableDao = tableDao;
        this.logDao = logDao;
        this.user = user;
    }

    private boolean isCashier() {
        return "CASHIER".equals(user.roles());
    }

    private boolean isWaiter() {
        return "WAITER".equals(user.roles());
    }

    private boolean isAdmin() {
        return "ADMIN".equals(user.roles());
    }

    private boolean canEdit(Order o) {
        return isCashier() || (isWaiter() && o.userId() == user.id());
    }

    private boolean canCancel(Order o) {
        return canEdit(o) || isAdmin();
    }

    // ------------------------------------------------------------ CREATE

    public void createOrder() {
        if (isAdmin()) {
            System.out.println("[ERROR] Admin accounts cannot create orders.");
            return;
        }
        List<DiningTable> free = tableDao.getAll("AVAILABLE");
        TableUi.printTables(free, "AVAILABLE TABLES");
        if (free.isEmpty()) {
            return;
        }
        int tableId = in.readInt("Enter Table ID for the customer (or 0 to cancel): ");
        if (tableId == 0) {
            System.out.println("-> Operation Cancelled by the User");
            return;
        }
        int orderId = orderDao.createOrder(user.id(), tableId);
        if (orderId > 0) {
            DiningTable table = tableDao.getById(tableId);
            System.out.println(" -> Order #" + orderId + " created for table " + table.tableNumber() + ".");
            logDao.logAction(user.id(), "ORDER_CREATED",
                    user.username() + " created order #" + orderId + " for table " + table.tableNumber(), orderId);
            manageOrder(orderId);
        }
    }

    // -------------------------------------------------------------- READ

    public void viewActiveOrders() {
        printOrders(orderDao.getOrders("OPEN", null), "ACTIVE (OPEN) ORDERS");
    }

    /** Orders that are already paid or cancelled, optionally for one date. */
    public void history() {
        String date = askDate("Enter date yyyy-MM-dd (press Enter for all dates): ", true);
        if ("INVALID".equals(date)) {
            return;
        }
        List<Order> all = orderDao.getOrders(null, date);
        List<Order> closed = all.stream().filter(o -> !"OPEN".equals(o.status())).toList();
        printOrders(closed, "TRANSACTION HISTORY" + (date == null ? "" : " - " + date));
        double paid = closed.stream().filter(o -> "PAID".equals(o.status())).mapToDouble(Order::total).sum();
        System.out.printf("Total of paid transactions listed: PHP %,.2f%n", paid);

        int id = in.readInt("Enter Order ID to view details (or 0 to go back): ");
        if (id != 0) {
            Order o = orderDao.getOrder(id);
            if (o == null) {
                System.out.println("[ERROR] Order not found.");
            } else {
                showOrder(o);
            }
        }
    }

    private void printOrders(List<Order> orders, String title) {
        System.out.println("\n=================== " + title + " ===================");
        if (orders.isEmpty()) {
            System.out.println("-> No Records Found");
            return;
        }
        String border = "+-------+---------------------+-------+-----------------+-----------+------------+";
        System.out.println(border);
        System.out.printf("| %-5s | %-19s | %-5s | %-15s | %-9s | %10s |%n",
                "Order", "Date/Time", "Table", "Staff", "Status", "Total");
        System.out.println(border);
        for (Order o : orders) {
            System.out.printf("| %-5d | %-19s | %-5d | %-15s | %-9s | %10.2f |%n",
                    o.orderId(), o.orderDate(), o.tableNumber(), cut(o.staffName(), 15), o.status(), o.total());
        }
        System.out.println(border);
    }

    private void showOrder(Order o) {
        System.out.println("\n----------------------------------------------------------");
        System.out.printf("ORDER #%d | Table %d | Staff: %s | Status: %s%n",
                o.orderId(), o.tableNumber(), o.staffName(), o.status());
        System.out.println("Date: " + o.orderDate());
        System.out.println("----------------------------------------------------------");
        List<OrderItem> items = orderDao.getItems(o.orderId());
        if (items.isEmpty()) {
            System.out.println("(no items yet)");
        } else {
            System.out.printf("%-5s %-26s %4s %10s %11s%n", "Item", "Product", "Qty", "Price", "Subtotal");
            for (OrderItem i : items) {
                System.out.printf("%-5d %-26s %4d %10.2f %11.2f%n",
                        i.itemId(), cut(i.productName(), 26), i.quantity(), i.unitPrice(), i.subtotal());
            }
        }
        System.out.println("----------------------------------------------------------");
        System.out.printf("TOTAL: PHP %,.2f%n", o.total());
        if ("PAID".equals(o.status())) {
            System.out.printf("Paid : PHP %,.2f | Change: PHP %,.2f | Paid on: %s%n",
                    o.paidAmount(), o.paidAmount() - o.total(), o.paidDate());
        }
        System.out.println("----------------------------------------------------------");
    }

    // ------------------------------------------------- UPDATE / DELETE

    /** Asks for an order id and opens the order screen. */
    public void manageOrder() {
        viewActiveOrders();
        int id = in.readInt("Enter Order ID (or 0 to cancel): ");
        if (id == 0) {
            System.out.println("-> Operation Cancelled by the User");
            return;
        }
        manageOrder(id);
    }

    private void manageOrder(int orderId) {
        boolean back = false;
        while (!back) {
            Order o = orderDao.getOrder(orderId);
            if (o == null) {
                System.out.println("[ERROR] Order not found.");
                return;
            }
            showOrder(o);
            if (!"OPEN".equals(o.status())) {
                System.out.println("This order is " + o.status() + " and can no longer be changed.");
                return;
            }

            boolean edit = canEdit(o);
            boolean cancel = canCancel(o);
            if (!edit && !cancel) {
                System.out.println("You can only view this order (it belongs to another staff member).");
                return;
            }
            if (edit) {
                System.out.println("1. Add Item");
                System.out.println("2. Update Item Quantity");
                System.out.println("3. Remove Item");
                System.out.println("4. Change Table");
            }
            if (cancel) {
                System.out.println("5. Cancel Order");
            }
            if (isCashier()) {
                System.out.println("6. Process Payment");
            }
            System.out.println("0. Back");
            String choice = in.readLine("Choose an Option: ");

            switch (choice) {
                case "1" -> { if (edit) addItem(o); else denied(); }
                case "2" -> { if (edit) updateQuantity(o); else denied(); }
                case "3" -> { if (edit) removeItem(o); else denied(); }
                case "4" -> { if (edit) changeTable(o); else denied(); }
                case "5" -> {
                    if (cancel && cancelOrder(o)) {
                        back = true;
                    } else if (!cancel) {
                        denied();
                    }
                }
                case "6" -> {
                    if (isCashier()) {
                        if (pay(o)) {
                            back = true;
                        }
                    } else {
                        denied();
                    }
                }
                case "0" -> back = true;
                default -> System.out.println("-> [ERROR] Invalid Option.");
            }
        }
    }

    private void denied() {
        System.out.println("[ERROR] You do not have permission for that option.");
    }

    private void addItem(Order o) {
        List<Product> available = productDao.getAll(false).stream()
                .filter(p -> "AVAILABLE".equals(p.status()) && p.stock() > 0).toList();
        ProductUi.printProducts(available, "ORDERABLE PRODUCTS");
        if (available.isEmpty()) {
            return;
        }
        int productId = in.readInt("Enter Product ID (or 0 to cancel): ");
        if (productId == 0) {
            return;
        }
        int qty = in.readInt("Quantity: ", 1);
        if (orderDao.addItem(o.orderId(), productId, qty)) {
            Product p = productDao.getById(productId);
            System.out.println(" -> Added " + qty + " x " + p.name());
            logDao.logAction(user.id(), "ORDER_UPDATED",
                    "Added " + qty + " x " + p.name() + " to order #" + o.orderId(), o.orderId());
        }
    }

    private void updateQuantity(Order o) {
        int itemId = in.readInt("Enter Item ID to update (or 0 to cancel): ");
        if (itemId == 0) {
            return;
        }
        int qty = in.readInt("New quantity: ", 1);
        if (orderDao.updateItemQuantity(o.orderId(), itemId, qty)) {
            System.out.println(" -> Quantity updated.");
            logDao.logAction(user.id(), "ORDER_UPDATED",
                    "Changed item " + itemId + " quantity to " + qty + " in order #" + o.orderId(), o.orderId());
        }
    }

    private void removeItem(Order o) {
        int itemId = in.readInt("Enter Item ID to remove (or 0 to cancel): ");
        if (itemId == 0) {
            return;
        }
        if (orderDao.removeItem(o.orderId(), itemId)) {
            System.out.println(" -> Item removed.");
            logDao.logAction(user.id(), "ORDER_UPDATED",
                    "Removed item " + itemId + " from order #" + o.orderId(), o.orderId());
        }
    }

    private void changeTable(Order o) {
        TableUi.printTables(tableDao.getAll("AVAILABLE"), "AVAILABLE TABLES");
        int tableId = in.readInt("Enter new Table ID (or 0 to cancel): ");
        if (tableId == 0) {
            return;
        }
        if (orderDao.changeTable(o.orderId(), tableId)) {
            System.out.println(" -> Order moved to the new table.");
            logDao.logAction(user.id(), "ORDER_UPDATED",
                    "Moved order #" + o.orderId() + " to table ID " + tableId, o.orderId());
        }
    }

    private boolean cancelOrder(Order o) {
        if (!in.confirm("Cancel order #" + o.orderId() + "?")) {
            System.out.println("-> Cancel aborted.");
            return false;
        }
        if (orderDao.cancelOrder(o.orderId())) {
            System.out.println(" -> Order cancelled. Stock returned and table is available again.");
            logDao.logAction(user.id(), "ORDER_CANCELLED",
                    user.username() + " cancelled order #" + o.orderId(), o.orderId());
            return true;
        }
        return false;
    }

    /** Cashier only. */
    public void payOrder() {
        viewActiveOrders();
        int id = in.readInt("Enter Order ID to pay (or 0 to cancel): ");
        if (id == 0) {
            System.out.println("-> Operation Cancelled by the User");
            return;
        }
        Order o = orderDao.getOrder(id);
        if (o == null) {
            System.out.println("[ERROR] Order not found.");
        } else if (!"OPEN".equals(o.status())) {
            System.out.println("[ERROR] Order is already " + o.status() + ".");
        } else {
            showOrder(o);
            pay(o);
        }
    }

    private boolean pay(Order o) {
        if (o.total() <= 0) {
            System.out.println("[ERROR] Cannot pay an empty order. Add items first.");
            return false;
        }
        System.out.printf("Amount due: PHP %,.2f%n", o.total());
        double amount = in.readPositiveDouble("Cash received: ");
        double change = orderDao.payOrder(o.orderId(), amount);
        if (change < 0) {
            return false;
        }
        System.out.println(" -> PAYMENT SUCCESSFUL!");
        System.out.printf("    Paid: PHP %,.2f | Change: PHP %,.2f%n", amount, change);
        logDao.logAction(user.id(), "ORDER_PAID",
                String.format("%s received payment of %.2f for order #%d", user.username(), o.total(), o.orderId()),
                o.orderId());
        showOrder(orderDao.getOrder(o.orderId()));
        return true;
    }

    /** Admin only: permanently remove a cancelled order record. */
    public void removeCancelledOrder() {
        List<Order> cancelled = orderDao.getOrders("CANCELLED", null);
        printOrders(cancelled, "CANCELLED ORDERS");
        if (cancelled.isEmpty()) {
            return;
        }
        int id = in.readInt("Enter Order ID to remove permanently (or 0 to cancel): ");
        if (id == 0) {
            return;
        }
        if (!in.confirm("Permanently remove order #" + id + "?")) {
            System.out.println("-> Remove cancelled.");
            return;
        }
        if (orderDao.deleteCancelledOrder(id)) {
            System.out.println(" -> Cancelled order removed.");
            logDao.logAction(user.id(), "ORDER_REMOVED", "Admin removed cancelled order #" + id);
        }
    }

    // ------------------------------------------------------------ helpers

    /** Returns yyyy-MM-dd, null (blank allowed) or "INVALID". */
    private String askDate(String prompt, boolean allowBlank) {
        String text = in.readLine(prompt);
        if (text.isEmpty() && allowBlank) {
            return null;
        }
        try {
            return LocalDate.parse(text).toString();
        } catch (Exception e) {
            System.out.println("[ERROR] Invalid date. Use the format yyyy-MM-dd, e.g. " + LocalDate.now());
            return "INVALID";
        }
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "." : s;
    }
}