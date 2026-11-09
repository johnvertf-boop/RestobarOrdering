package com.scc.ui;

import com.scc.dao.*;
import com.scc.model.User;

import java.util.Scanner;

public class CashierMenu {
    private final ActivityLogDao logDao;
    private final InputHelper in;
    private final TableUi tableUi;
    private final ProductUi productUi;
    private final OrderUi orderUi;

    public CashierMenu(ActivityLogDao logDao, Scanner scanner, User cashier) {
        this.logDao = logDao;
        this.in = new InputHelper(scanner);
        ProductDao productDao = new ProductDao();
        TableDao tableDao = new TableDao();
        this.tableUi = new TableUi(in, tableDao, logDao);
        this.productUi = new ProductUi(in, productDao, logDao);
        this.orderUi = new OrderUi(in, new OrderDao(), productDao, tableDao, logDao, cashier);
    }

    public void display(User cashier) {
        boolean loggedIn = true;
        while (loggedIn) {
            System.out.println("==================================");
            System.out.println("    >>>  CASHIER DASHBOARD  <<<   ");
            System.out.println("==================================");
            System.out.println("1. View Tables (Available / Occupied)");
            System.out.println("2. View Menu / Products");
            System.out.println("3. Create New Order");
            System.out.println("4. View / Manage Active Orders");
            System.out.println("5. Process Payment");
            System.out.println("6. Transaction History");
            System.out.println("7. Logout");
            String select = in.readLine("Choose an Option (1-7): ");
            switch (select) {
                case "1" -> tableUi.viewTables();
                case "2" -> productUi.viewMenu();
                case "3" -> orderUi.createOrder();
                case "4" -> orderUi.manageOrder();
                case "5" -> orderUi.payOrder();
                case "6" -> orderUi.history();
                case "7" -> {
                    logDao.logAction(cashier.id(), "USER_LOGOUT", "Cashier " + cashier.username() + " logged out.");
                    loggedIn = false;
                }
                default -> System.out.println("-> [ERROR] Invalid Option.");
            }
        }
    }
}