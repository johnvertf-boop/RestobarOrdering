package com.scc.ui;

import com.scc.dao.ActivityLogDao;
import com.scc.dao.UserDao;
import com.scc.model.ActivityLog;
import com.scc.model.User;

import java.util.List;
import java.util.Scanner;

public class AdminMenu {
    private final UserDao userDao;
    private final ActivityLogDao activityLogDao;
    private final Scanner scanner;

    public AdminMenu(UserDao userDao, ActivityLogDao activityLogDao, Scanner scanner) {
        this.userDao = userDao;
        this.activityLogDao = activityLogDao;
        this.scanner = scanner;
    }

    public void display(User admin) {
        boolean loggedIn = true;
        while (loggedIn) {
            System.out.println("==================================");
            System.out.println("    >>>   ADMIN DASHBOARD  <<<    ");
            System.out.println("==================================");
            System.out.println("1. View all Users");
            System.out.println("2. View Pending User Approvals");
            System.out.println("3. Approve User");
            System.out.println("4. Add New User");
            System.out.println("5. Update User Role / Status");
            System.out.println("6. Delete User");
            System.out.println("7. View System Audit Logs");
            System.out.println("8. Logout");
            System.out.print("Choose an Option (1-8): ");
            String select = scanner.nextLine().trim();
            switch (select) {
                case "1" -> viewAllUser();
                case "2" -> viewPendingUser();
                case "3" -> approveUser(admin);
                case "4" -> System.out.println("-> [INFO] Add New User feature goes here.");
                case "5" -> updateUserRoleAndStatus(admin);
                case "6" -> System.out.println("-> [INFO] Delete User feature goes here.");
                case "7" -> viewAuditLogsMenu();
                case "8" -> {
                    activityLogDao.logAction(admin.id(), "USER_LOGOUT", "Admin " + admin.username() + " logged out.");
                    loggedIn = false;
                }
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

        int userID = readIntInput("Enter User ID to Approve (or 0 to cancel): ");
        if (userID == 0) {
            System.out.println("-> Operation Cancelled by the User");
            return;
        }

        boolean exists = pendingUsers.stream().anyMatch(u -> u.id() == userID);
        if (!exists) {
            System.out.println("[ERROR] User ID not found.");
            return;
        }

        boolean approved = userDao.approveUser(userID);
        if (approved) {
            // System-wide Auto-Logging for Account Approval
            activityLogDao.logAction(
                    currentAdmin.id(),
                    "USER_APPROVED",
                    "Admin [" + currentAdmin.id() + "] approved account for User ID " + userID
            );
        }
    }

    private void updateUserRoleAndStatus(User currentAdmin) {
        viewAllUser();
        List<User> users = userDao.getAllUsers();
        if (users.isEmpty()) {
            return;
        }

        int targetUserId = readIntInput("Enter User ID to Update (or 0 to cancel): ");
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

        boolean updated = userDao.updateUserRoleAndStatus(targetUserId, newRole, newStatus);
        if (updated) {
            // System-wide Auto-Logging for Role/Status Update
            activityLogDao.logAction(
                    currentAdmin.id(),
                    "USER_UPDATED",
                    "Admin [" + currentAdmin.id() + "] changed role of User " + targetUserId + " to " + newRole + " and status to " + newStatus
            );
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
            System.out.print("Choose an Option: ");

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> displayLogsTable(activityLogDao.getAllLogs(), "ALL SYSTEM AUDIT LOGS");
                case "2" -> {
                    int userId = readIntInput("Enter User ID to filter logs (or 0 to cancel): ");
                    if (userId != 0) {
                        displayLogsTable(activityLogDao.getLogsByUserId(userId), "AUDIT LOGS FOR USER ID " + userId);
                    }
                }
                case "3" -> {
                    System.out.print("Are you sure you want to clear all historical logs? (Y/N): ");
                    String confirm = scanner.nextLine().trim();
                    if (confirm.equalsIgnoreCase("Y")) {
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
        String border = "+-------+----------+--------------------+-------------------------------------+----------------------+";
        System.out.println(border);
        System.out.printf("| %-5s | %-8s | %-18s | %-35s | %-20s |\n", "ID", "User ID", "Action Type", "Description", "Timestamp");
        System.out.println(border);
        for (ActivityLog log : logs) {
            System.out.printf("| %-5d | %-8d | %-18s | %-35s | %-20s |\n",
                    log.logId(),
                    log.userId(),
                    log.actionType(),
                    log.description().length() > 35 ? log.description().substring(0, 32) + "..." : log.description(),
                    log.timestamp().toString());
        }
        System.out.println(border);
    }

    // --- Input Validation Wrappers ---

    private int readIntInput(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("[ERROR] Invalid numeric input. Please enter numbers only.");
            }
        }
    }

    private String readValidRole() {
        while (true) {
            System.out.print("Enter new Role (STUDENT, TEACHER, ADMIN, CASHIER, WAITER) or 0 to cancel: ");
            String role = scanner.nextLine().trim().toUpperCase();
            if (role.equals("0")) {
                return "0";
            }
            if (role.equals("STUDENT") || role.equals("TEACHER") || role.equals("ADMIN") || role.equals("CASHIER") || role.equals("WAITER")) {
                return role;
            }
            System.out.println("[ERROR] Invalid role. Please enter a valid role.");
        }
    }

    private String readValidStatus() {
        while (true) {
            System.out.print("Enter new Status (APPROVED, PENDING) or 0 to cancel: ");
            String status = scanner.nextLine().trim().toUpperCase();
            if (status.equals("0")) {
                return "0";
            }
            if (status.equals("APPROVED") || status.equals("PENDING")) {
                return status;
            }
            System.out.println("[ERROR] Invalid status. Please enter APPROVED or PENDING.");
        }
    }
}