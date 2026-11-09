package com.scc.ui;

import java.util.Scanner;

/** Small helper so every menu reads and validates console input the same way. */
public class InputHelper {
    private final Scanner scanner;

    public InputHelper(Scanner scanner) {
        this.scanner = scanner;
    }

    public String readLine(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    /** Keeps asking until the person types something. */
    public String readRequired(String prompt) {
        while (true) {
            String value = readLine(prompt);
            if (!value.isEmpty()) {
                return value;
            }
            System.out.println("[ERROR] This field cannot be empty.");
        }
    }

    public int readInt(String prompt) {
        while (true) {
            String input = readLine(prompt);
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("[ERROR] Invalid numeric input. Please enter numbers only.");
            }
        }
    }

    /** Whole number that must be at least {@code min}. */
    public int readInt(String prompt, int min) {
        while (true) {
            int value = readInt(prompt);
            if (value >= min) {
                return value;
            }
            System.out.println("[ERROR] Value must be at least " + min + ".");
        }
    }

    /** Decimal number that must be greater than zero. */
    public double readPositiveDouble(String prompt) {
        while (true) {
            String input = readLine(prompt);
            try {
                double value = Double.parseDouble(input);
                if (value > 0) {
                    return value;
                }
                System.out.println("[ERROR] Value must be greater than 0.");
            } catch (NumberFormatException e) {
                System.out.println("[ERROR] Invalid number. Example: 150.50");
            }
        }
    }

    public boolean confirm(String prompt) {
        return readLine(prompt + " (Y/N): ").equalsIgnoreCase("Y");
    }
}