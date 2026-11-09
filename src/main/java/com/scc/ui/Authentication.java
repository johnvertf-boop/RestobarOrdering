package com.scc.ui;

import com.scc.dao.ActivityLogDao;
import com.scc.dao.UserDao;
import com.scc.model.User;

import java.util.Scanner;

public class Authentication {
    private final UserDao userDao;
    private final ActivityLogDao activityLogDao;
    private final Scanner sc;

    public Authentication(UserDao userDao, Scanner scanner) {
        this.userDao = userDao;
        this.activityLogDao = new ActivityLogDao();
        this.sc = scanner;
    }

    public void start() {
        boolean running = true;

        while (running) {
            System.out.println("==========================================");
            System.out.println(" >  RESTOBAR ORDERING AND TABLE SYSTEM  < ");
            System.out.println("==========================================");
            System.out.println("1. Login");
            System.out.println("2. Register");
            System.out.println("3. Exit");
            System.out.print("Select: ");
            String select = sc.nextLine().trim();
            System.out.println("==========================================");

            switch (select) {
                case "1" -> handleLogin();
                case "2" -> handleRegister();
                case "3" -> {
                    System.out.println("Exiting System. THANK YOU!");
                    running = false;
                }
                default -> System.out.println("Invalid selection. Please try again.");
            }
            System.out.println(); // Add a blank space between menu loops for readability
        }
    }

    private void handleRegister() {
        System.out.print("Enter new Username: ");
        String user = sc.nextLine().trim();
        System.out.print("Enter Password: ");
        String pass = sc.nextLine();

        if (user.isEmpty() || pass.isEmpty()) {
            System.out.println("Username and password cannot be empty!");
            return;
        }

        System.out.print("Select Role: [1] Admin [2] Cashier [3] Waiter: ");
        String roleChoice = sc.nextLine().trim();
        String role = switch (roleChoice) {
            case "1" -> "ADMIN";
            case "2" -> "CASHIER";
            case "3" -> "WAITER";
            default -> null;
        };

        if (role != null) {
            userDao.registerUser(user, pass, role);
        } else {
            System.out.println("Invalid Role Selection!");
        }
    }

    private void handleLogin() {
        System.out.print("Enter Username: ");
        String username = sc.nextLine().trim();
        System.out.print("Enter Password: ");
        String password = sc.nextLine();

        User user = userDao.login(username, password);

        if (user != null) {
            System.out.println(" > Welcome " + user.username() + " | ROLE: " + user.roles());

            // Auto-Log User Login Event
            activityLogDao.logAction(user.id(), "USER_LOGIN", "User " + user.username() + " logged into the system.");

            switch (user.roles()) {
                case "ADMIN" -> new AdminMenu(userDao, activityLogDao, sc, user).display(user);
                case "CASHIER" -> new CashierMenu(activityLogDao, sc, user).display(user);
                default -> System.out.println("Account roles not found!");
            }
        } else {
            System.out.println(" > Login failed. Returning to main menu.");
        }
    }
}