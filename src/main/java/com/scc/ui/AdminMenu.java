package com.scc.ui;

import com.scc.dao.UserDao;
import com.scc.model.User;

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
                String choice = scanner.nextLine().trim();
            }
        }
    }