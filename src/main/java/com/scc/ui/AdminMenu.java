package com.scc.ui;

import com.scc.dao.*;
import com.scc.model.ActivityLog;
import com.scc.model.User;

import java.util.List;
import java.util.Scanner;

public class AdminMenu {
    private final UserDao userDao;
    private final ActivityLogDao activityLogDao;
    private final InputHelper in;
    private final ProductUi productUi;
    private final TableUi tableUi;
    private final OrderUi orderUi;

    public AdminMenu(UserDao userDao, ActivityLogDao activityLogDao, Scanner scanner, User admin) {
        this.userDao = userDao;
        this.activityLogDao = activityLogDao;
        this.in = new InputHelper(scanner);
        ProductDao productDao = new ProductDao();
        TableDao tableDao = new TableDao();
        this.productUi = new ProductUi(in, productDao, activityLogDao);
        this.tableUi = new TableUi(in, tableDao, activityLogDao);
        this.orderUi = new OrderUi(in, new OrderDao(), productDao, tableDao, activityLogDao, admin);
    }

    public void display(User admin) {
        boolean loggedIn = true;
        while (loggedIn) {
            System.out.println("==================================");
            System.out.println("    >>>   ADMIN DASHBOARD  <<<    ");
            System.out.println("==================================");
            System.out.println("1. Manage Users");
            System.out.println("2. Manage Products");
            System.out.println("3. Manage Tables");
            System.out.println("4. View Orders (Active)");
            System.out.println("5. Transaction History");
            System.out.println("6. Cancel Order / Remove Cancelled Orders");
            System.out.println("7. View System Audit Logs");
            System.out.println("8. Logout");
            String select = in.readLine("Choose an Option (1-8): ");
            switch (select) {
                case "1" -> manageUsers(admin);
                case "2" -> productUi.manage(admin);
                case "3" -> tableUi.manage(admin);
                case "4" -> orderUi.viewActiveOrders();
                case "5" -> orderUi.history();
                case "6" -> adminOrderTools();
                case "7" -> viewAuditLogsMenu();
                case "8" -> {
                    activityLogDao.logAction(admin.id(), "USER_LOGOUT", "Admin " + admin.username() + " logged out.");
                    loggedIn = false;
                }
                default -> System.out.println("-> [ERROR] Invalid Option.");
            }
        }
    }

    // --- Manage Users Sub-Menu ---

    private void manageUsers(User admin) {
        boolean inUserMenu = true;
        while (inUserMenu) {
            System.out.println("\n----------------------------------");
            System.out.println("      >>> MANAGE USERS <<<        ");
            System.out.println("----------------------------------");
            System.out.println("1. View all Users");
            System.out.println("2. View Pending User Approvals");
            System.out.println("3. Approve User");
            System.out.println("4. Add New User");
            System.out.println("5. Update User Role / Status");
            System.out.println("6. Delete / Deactivate User");
            System.out.println("0. Return to Main Dashboard");

            String choice = in.readLine("Choose an Option: ");
            switch (choice) {
                case "1" -> viewAllUser();
                case "2" -> viewPendingUser();
                case "3" -> approveUser(admin);
                case "4" -> addUser(admin);
                case "5" -> updateUserRoleAndStatus(admin);
                case "6" -> deleteUser(admin);
                case "0" -> inUserMenu = false;
                default -> System.out.println("-> [ERROR] Invalid Option.");
            }
        }
    }

    private void displayUserTable(List<User> users, String title) {
        System.out.println("===================================" + title + "===================================");
        if (users.isEmpty()) {
            System.out.println("-> No Records Found");
            return;
        }
        String border = "+-----+-----------------+---------------------------+------------+";
        System.out.println(border);
        System.out.printf("| %-3s | %-15s | %-25s | %-10s |\n", "ID", "Username", "Role", "Status");
        System.out.println(border);
        for (User u : users) {
            System.out.printf("| %-3s | %-15s | %-25s | %-10s |\n",
                    u.id(), u.username(), u.roles(), u.status());
        }
        System.out.println(border);
    }

    private void viewAllUser() {
        displayUserTable(userDao.getAllUsers(), "ALL REGISTERED USER");
    }

    private void viewPendingUser() {
        displayUserTable(userDao.getPendingUser(), "PENDING APPROVALS");
    }

    private void approveUser(User currentAdmin) {
        List<User> pendingUsers = userDao.getPendingUser();
        displayUserTable(pendingUsers, "PENDING APPROVALS");

        if (pendingUsers.isEmpty()) {
            return;
        }

        int userID = in.readInt("Enter User ID to Approve (or 0 to cancel): ");
        if (userID == 0) {
            System.out.println("-> Operation Cancelled by the User");
            return;
        }

        boolean exists = pendingUsers.stream().anyMatch(u -> u.id() == userID);
        if (!exists) {
            System.out.println("[ERROR] User ID not found.");
            return;
        }

        if (userDao.approveUser(userID)) {
            activityLogDao.logAction(
                    currentAdmin.id(),
                    "USER_APPROVED",
                    "Admin [" + currentAdmin.id() + "] approved account for User ID " + userID
            );
        }
    }

    private void addUser(User currentAdmin) {
        String username = in.readRequired("Enter new Username (or 0 to cancel): ");
        if (username.equals("0")) {
            System.out.println("-> Operation Cancelled by the User");
            return;
        }
        String password = in.readRequired("Enter Password: ");
        String role = readValidRole();
        if (role.equals("0")) {
            System.out.println("-> Operation Cancelled by the User");
            return;
        }
        if (userDao.addUser(username, password, role)) {
            System.out.println(" -> User '" + username + "' (" + role + ") created and approved.");
            activityLogDao.logAction(currentAdmin.id(), "USER_CREATED",
                    "Admin [" + currentAdmin.id() + "] created " + role + " account '" + username + "'");
        }
    }

    private void updateUserRoleAndStatus(User currentAdmin) {
        viewAllUser();
        List<User> users = userDao.getAllUsers();
        if (users.isEmpty()) {
            return;
        }

        int targetUserId = in.readInt("Enter User ID to Update (or 0 to cancel): ");
        if (targetUserId == 0) {
            System.out.println("-> Operation Cancelled by the User");
            return;
        }

        if (targetUserId == currentAdmin.id()) {
            System.out.println("WARNING: Modifying the currently active admin account is restricted.");
            return;
        }

        boolean exists = users.stream().anyMatch(u -> u.id() == targetUserId);
        if (!exists) {
            System.out.println("[ERROR] User ID not found.");
            return;
        }

        String newRole = readValidRole();
        if (newRole.equals("0")) {
            System.out.println("-> Operation Cancelled by the User");
            return;
        }

        String newStatus = readValidStatus();
        if (newStatus.equals("0")) {
            System.out.println("-> Operation Cancelled by the User");
            return;
        }

        if (userDao.updateUserRoleAndStatus(targetUserId, newRole, newStatus)) {
            activityLogDao.logAction(
                    currentAdmin.id(),
                    "USER_UPDATED",
                    "Admin [" + currentAdmin.id() + "] changed role of User " + targetUserId + " to " + newRole + " and status to " + newStatus
            );
        }
    }

    private void deleteUser(User currentAdmin) {
        viewAllUser();
        int targetUserId = in.readInt("Enter User ID to Delete (or 0 to cancel): ");
        if (targetUserId == 0) {
            System.out.println("-> Operation Cancelled by the User");
            return;
        }
        if (targetUserId == currentAdmin.id()) {
            System.out.println("WARNING: You cannot delete the account you are logged in with.");
            return;
        }
        boolean exists = userDao.getAllUsers().stream().anyMatch(u -> u.id() == targetUserId);
        if (!exists) {
            System.out.println("[ERROR] User ID not found.");
            return;
        }
        if (!in.confirm("Delete user ID " + targetUserId + "?")) {
            System.out.println("-> Delete cancelled.");
            return;
        }
        String result = userDao.deleteUser(targetUserId);
        if ("DELETED".equals(result)) {
            System.out.println(" -> User deleted.");
        } else if ("DEACTIVATED".equals(result)) {
            System.out.println(" -> User has orders/logs on record, so the account was DEACTIVATED instead.");
        } else {
            return;
        }
        activityLogDao.logAction(currentAdmin.id(), "USER_REMOVED",
                "Admin [" + currentAdmin.id() + "] " + result.toLowerCase() + " User " + targetUserId);
    }

    private void adminOrderTools() {
        System.out.println("1. Cancel an Active Order");
        System.out.println("2. Remove a Cancelled Order Record");
        System.out.println("0. Back");
        switch (in.readLine("Choose an Option: ")) {
            case "1" -> orderUi.manageOrder();
            case "2" -> orderUi.removeCancelledOrder();
            default -> { }
        }
    }

    // --- Audit Log Sub-Menu & Display ---

    private void viewAuditLogsMenu() {
        boolean inAuditMenu = true;
        while (inAuditMenu) {
            System.out.println("\n----------------------------------");
            System.out.println("     >>> AUDIT LOG MENU <<<       ");
            System.out.println("----------------------------------");
            System.out.println("1. View All System Logs");
            System.out.println("2. Filter Logs by User ID");
            System.out.println("3. Clear Historical Logs");
            System.out.println("0. Return to Main Dashboard");

            String choice = in.readLine("Choose an Option: ");
            switch (choice) {
                case "1" -> displayLogsTable(activityLogDao.getAllLogs(), "ALL SYSTEM AUDIT LOGS");
                case "2" -> {
                    int userId = in.readInt("Enter User ID to filter logs (or 0 to cancel): ");
                    if (userId != 0) {
                        displayLogsTable(activityLogDao.getLogsByUserId(userId), "AUDIT LOGS FOR USER ID " + userId);
                    }
                }
                case "3" -> {
                    if (in.confirm("Are you sure you want to clear all historical logs?")) {
                        activityLogDao.clearLogs();
                    } else {
                        System.out.println("-> Clear log operation cancelled.");
                    }
                }
                case "0" -> inAuditMenu = false;
                default -> System.out.println("-> [ERROR] Invalid Option.");
            }
        }
    }

    private void displayLogsTable(List<ActivityLog> logs, String title) {
        System.out.println("\n===================================" + title + "===================================");
        if (logs.isEmpty()) {
            System.out.println("-> No Activity Log Records Found");
            return;
        }
        String border = "+-------+--------+-----------------+--------+----------------------------------------+---------------------+";
        System.out.println(border);
        System.out.printf("| %-5s | %-6s | %-15s | %-6s | %-38s | %-19s |\n",
                "ID", "User", "Action Type", "Order", "Description", "Timestamp");
        System.out.println(border);
        for (ActivityLog log : logs) {
            String desc = log.description().length() > 38 ? log.description().substring(0, 35) + "..." : log.description();
            System.out.printf("| %-5d | %-6d | %-15s | %-6s | %-38s | %-19s |\n",
                    log.logId(),
                    log.userId(),
                    log.actionType().length() > 15 ? log.actionType().substring(0, 15) : log.actionType(),
                    log.orderId() == null ? "-" : log.orderId().toString(),
                    desc,
                    log.timestamp());
        }
        System.out.println(border);
    }

    // --- Input Validation ---

    private String readValidRole() {
        while (true) {
            String role = in.readLine("Enter Role (ADMIN, CASHIER, WAITER) or 0 to cancel: ").toUpperCase();
            if (role.equals("0")) {
                return "0";
            }
            if (role.equals("ADMIN") || role.equals("CASHIER") || role.equals("WAITER")) {
                return role;
            }
            System.out.println("[ERROR] Invalid role. Please enter a valid role.");
        }
    }

    private String readValidStatus() {
        while (true) {
            String status = in.readLine("Enter new Status (APPROVED, PENDING, INACTIVE) or 0 to cancel: ").toUpperCase();
            if (status.equals("0")) {
                return "0";
            }
            if (status.equals("APPROVED") || status.equals("PENDING") || status.equals("INACTIVE")) {
                return status;
            }
            System.out.println("[ERROR] Invalid status. Please enter APPROVED, PENDING or INACTIVE.");
        }
    }
}