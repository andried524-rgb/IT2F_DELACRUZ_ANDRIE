package com.yourdomain.ui;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

/** Safe console input helpers: bad input re-prompts instead of crashing. */
public final class ConsoleInput {

    private static final Scanner SCANNER = new Scanner(System.in);

    private ConsoleInput() {
    }

    /** Something that may throw (database errors, business-rule errors). */
    @FunctionalInterface
    public interface Action {
        void run() throws Exception;
    }

    /** Runs an action and prints a friendly message instead of a stack trace on failure. */
    public static void safely(Action action) {
        try {
            action.run();
        } catch (java.sql.SQLException e) {
            System.out.println("  [Database error] " + e.getMessage());
        } catch (Exception e) {
            System.out.println("  [Error] " + e.getMessage());
        }
    }

    public static String readLine(String prompt) {
        System.out.print(prompt);
        return SCANNER.hasNextLine() ? SCANNER.nextLine().trim() : "";
    }

    public static String readNonEmpty(String prompt) {
        while (true) {
            String s = readLine(prompt);
            if (!s.isEmpty()) {
                return s;
            }
            System.out.println("  Input cannot be empty. Try again.");
        }
    }

    public static int readInt(String prompt) {
        while (true) {
            String s = readLine(prompt);
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                System.out.println("  Invalid input. Please enter a whole number.");
            }
        }
    }

    public static int readIntInRange(String prompt, int min, int max) {
        while (true) {
            int n = readInt(prompt);
            if (n >= min && n <= max) {
                return n;
            }
            System.out.println("  Please choose a number from " + min + " to " + max + ".");
        }
    }

    public static int readPositiveInt(String prompt) {
        while (true) {
            int n = readInt(prompt);
            if (n > 0) {
                return n;
            }
            System.out.println("  Value must be greater than 0.");
        }
    }

    public static LocalDate readDate(String prompt) {
        while (true) {
            String s = readLine(prompt);
            try {
                return LocalDate.parse(s);
            } catch (DateTimeParseException e) {
                System.out.println("  Invalid date. Use the format YYYY-MM-DD (e.g. 2027-12-31).");
            }
        }
    }
}
