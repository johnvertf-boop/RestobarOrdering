package com.scc.ui;

import com.scc.dao.ActivityLogDao;
import com.scc.dao.TableDao;
import com.scc.model.DiningTable;
import com.scc.model.User;

import java.util.List;

public class TableUi {
    private final InputHelper in;
    private final TableDao tableDao;
    private final ActivityLogDao logDao;

    public TableUi(InputHelper in, TableDao tableDao, ActivityLogDao logDao) {
        this.in = in;
        this.tableDao = tableDao;
        this.logDao = logDao;
    }

    public static void printTables(List<DiningTable> tables, String title) {
        System.out.println("\n============ " + title + " ============");
        if (tables.isEmpty()) {
            System.out.println("-> No Records Found");
            return;
        }
        String border = "+-----+--------+----------+------------+";
        System.out.println(border);
        System.out.printf("| %-3s | %-6s | %-8s | %-10s |%n", "ID", "Table", "Capacity", "Status");
        System.out.println(border);
        for (DiningTable t : tables) {
            System.out.printf("| %-3d | %-6d | %-8d | %-10s |%n",
                    t.tableId(), t.tableNumber(), t.capacity(), t.status());
        }
        System.out.println(border);
    }

    /** Available and occupied tables. */
    public void viewTables() {
        List<DiningTable> all = tableDao.getAll(null);
        printTables(all, "TABLE STATUS");
        long free = all.stream().filter(t -> "AVAILABLE".equals(t.status())).count();
        System.out.println("Available: " + free + " | Occupied: " + (all.size() - free));
    }

    /** Update a table's status (only when it has no open order). Used by Waiter and Admin. */
    public void updateStatus(User user) {
        viewTables();
        DiningTable t = pickTable();
        if (t == null) {
            return;
        }
        if (tableDao.hasOpenOrder(t.tableId())) {
            System.out.println("[ERROR] Table has an open order. Pay or cancel the order first.");
            return;
        }
        String choice = in.readLine("New status [1] AVAILABLE [2] OCCUPIED: ");
        String status = switch (choice) {
            case "1" -> "AVAILABLE";
            case "2" -> "OCCUPIED";
            default -> null;
        };
        if (status == null) {
            System.out.println("[ERROR] Invalid choice.");
            return;
        }
        if (tableDao.updateStatus(t.tableId(), status)) {
            System.out.println(" -> Table " + t.tableNumber() + " is now " + status + ".");
            logDao.logAction(user.id(), "TABLE_UPDATED", "Table " + t.tableNumber() + " set to " + status);
        }
    }

    /** Full CRUD screen for the Admin. */
    public void manage(User admin) {
        boolean back = false;
        while (!back) {
            System.out.println("\n----------------------------------");
            System.out.println("      >>> TABLE MANAGEMENT <<<    ");
            System.out.println("----------------------------------");
            System.out.println("1. View Tables");
            System.out.println("2. Add New Table");
            System.out.println("3. Update Table Capacity");
            System.out.println("4. Update Table Status");
            System.out.println("5. Delete Table");
            System.out.println("0. Return to Main Dashboard");
            String choice = in.readLine("Choose an Option: ");
            switch (choice) {
                case "1" -> viewTables();
                case "2" -> addTable(admin);
                case "3" -> updateCapacity(admin);
                case "4" -> updateStatus(admin);
                case "5" -> deleteTable(admin);
                case "0" -> back = true;
                default -> System.out.println("-> [ERROR] Invalid Option.");
            }
        }
    }

    private DiningTable pickTable() {
        int id = in.readInt("Enter Table ID (or 0 to cancel): ");
        if (id == 0) {
            System.out.println("-> Operation Cancelled by the User");
            return null;
        }
        DiningTable t = tableDao.getById(id);
        if (t == null) {
            System.out.println("[ERROR] Table ID not found.");
        }
        return t;
    }

    private void addTable(User admin) {
        int number = in.readInt("Table number: ", 1);
        int capacity = in.readInt("Seating capacity: ", 1);
        if (tableDao.addTable(number, capacity)) {
            System.out.println(" -> Table added successfully!");
            logDao.logAction(admin.id(), "TABLE_ADDED", "Added table " + number + " (capacity " + capacity + ")");
        }
    }

    private void updateCapacity(User admin) {
        viewTables();
        DiningTable t = pickTable();
        if (t == null) {
            return;
        }
        int capacity = in.readInt("New capacity for table " + t.tableNumber() + ": ", 1);
        if (tableDao.updateCapacity(t.tableId(), capacity)) {
            System.out.println(" -> Capacity updated.");
            logDao.logAction(admin.id(), "TABLE_UPDATED", "Table " + t.tableNumber() + " capacity set to " + capacity);
        }
    }

    private void deleteTable(User admin) {
        viewTables();
        DiningTable t = pickTable();
        if (t == null) {
            return;
        }
        if (!in.confirm("Delete table " + t.tableNumber() + "?")) {
            System.out.println("-> Delete cancelled.");
            return;
        }
        if (tableDao.deleteTable(t.tableId())) {
            System.out.println(" -> Table deleted.");
            logDao.logAction(admin.id(), "TABLE_REMOVED", "Deleted table " + t.tableNumber());
        }
    }
}