package com.scc.ui;

import com.scc.dao.UserDao;
import com.scc.model.User;

import java.util.List;
import java.util.Scanner;

public class AdminMenu {
    private final  UserDao userDao;
    private final Scanner scanner;

    public AdminMenu(UserDao userDao, Scanner scanner) {
        this.userDao = userDao;
        this.scanner = scanner;
    }

        public void display (User admin){
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
                System.out.println("7. Logout");
                System.out.print("Choose an Option (1-7): ");
                String select = scanner.nextLine().trim();
                switch (select) {
                    case "1" -> viewAllUser();
                    case "2" -> viewPendingUser();
                    case "3" -> approveUser();
                    case "7" -> loggedIn = false;
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

    private void viewPendingUser(){
        displayUserTable(userDao.getPendingUser(), "PENDING APPROVALS");
    }

    private void approveUser(){
        List<User> pendingUsers = userDao.getPendingUser();
        displayUserTable(pendingUsers, "PENDING APPROVALS");

        if (pendingUsers.isEmpty()){
            return;
        }
        System.out.print("Enter User ID to Approve (or 0 to cancel): ");
        int userID = scanner.nextInt();
        if (userID == 0){
            System.out.println("-> Operation Cancelled by the User");
        }
        boolean exits = pendingUsers.stream().anyMatch(u -> u.id() ==userID );
        if (!exits){
            System.out.println("[ERROR] User ID not found.");
            return;
        }
        userDao.approveUser(userID);
    }
            }