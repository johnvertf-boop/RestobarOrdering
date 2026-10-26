package com.scc;

import com.scc.dao.UserDao;
import com.scc.model.User;

import java.util.Scanner;

public class Main {
    private static final Scanner sc = new Scanner(System.in);
    private static final UserDao userDao = new UserDao();

    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println(" >  RESTOBAR ORDERING AND TABLE SYSTEM  < ");
        System.out.println("==========================================");
        System.out.println("1. Login");
        System.out.println("2. Register");
        System.out.println("3. Exit");
        System.out.print("Select: ");
        String select = sc.nextLine();
        System.out.println("==========================================");

        switch (select) {
            case "1":
                break;
            case "2": System.out.print("Enter new Username: ");
                String user = sc.nextLine().trim();
                System.out.print("Enter Password: ");
                String pass = sc.nextLine();

                System.out.print("Select Role: [1] Admin [2] Cashier [3] Waiter: ");
                String roleChoice = sc.nextLine().trim();
                String role;

                switch (roleChoice) {
                    case "1":
                        role = "ADMIN";
                        break;
                    case "2":
                        role = "CASHIER";
                        break;
                    case "3":
                        role = "WAITER";
                        break;
                    default:
                        role = null;
                        break;
                }
                if (role != null) {
                    userDao.registerUser(user, pass, role);
                } else {
                    System.out.println("Invalid Role Selection!");
                }
                break;
            case "3":
                System.out.println("Exiting System. THANK YOU!");
                break;
            default:
                System.out.println("Invalid selection. Please try again.");
                break;
        }
    }
}