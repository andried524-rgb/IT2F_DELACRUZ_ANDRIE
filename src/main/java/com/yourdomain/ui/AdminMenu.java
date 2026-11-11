package com.yourdomain.ui;

import com.yourdomain.dao.ActivityLogDao;
import com.yourdomain.dao.UserDao;
import com.yourdomain.model.ActivityLog;
import com.yourdomain.model.User;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static com.yourdomain.ui.ConsoleInput.readIntInRange;
import static com.yourdomain.ui.ConsoleInput.readInt;
import static com.yourdomain.ui.ConsoleInput.readLine;
import static com.yourdomain.ui.ConsoleInput.safely;

/** Administrator menu, including the new "View System Audit Logs" option. */
public final class AdminMenu {

    private static final UserDao USER_DAO = new UserDao();
    private static final ActivityLogDao LOG_DAO = new ActivityLogDao();

    /** Audit table layout required by the deliverable. */
    private static final String LOG_FORMAT = "%-5s | %-8s | %-18s | %-35s | %-20s%n";
    private static final int DESC_WIDTH = 35;

    private AdminMenu() {
    }

    public static void show(User admin) {
        while (true) {
            System.out.println("\n=== ADMINISTRATOR MENU ===");
            System.out.println("1. Approve pending accounts");
            System.out.println("2. Update user role / status");
            System.out.println("3. Add medicine");
            System.out.println("4. Receive medicine batch");
            System.out.println("5. Dispense medicine");
            System.out.println("6. Dispose expired / damaged batch");
            System.out.println("7. View medicines & stock levels");
            System.out.println("8. View System Audit Logs");
            System.out.println("0. Logout");

            switch (readIntInRange("Choose: ", 0, 8)) {
                case 1 -> safely(() -> approveAccounts(admin));
                case 2 -> safely(() -> updateUser(admin));
                case 3 -> safely(() -> MedicineActions.addMedicine(admin));
                case 4 -> safely(() -> MedicineActions.receiveBatch(admin));
                case 5 -> safely(() -> MedicineActions.dispense(admin));
                case 6 -> safely(() -> MedicineActions.disposeBatch(admin));
                case 7 -> safely(MedicineActions::viewMedicines);
                case 8 -> auditLogMenu(admin);
                default -> {
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------ account tools

    private static void approveAccounts(User admin) throws SQLException {
        List<User> pending = USER_DAO.getPendingUsers();
        if (pending.isEmpty()) {
            System.out.println("  No accounts are waiting for approval.");
            return;
        }
        printUsers(pending);
        int id = readInt("Enter User ID to approve (0 to cancel): ");
        if (id == 0) {
            return;
        }
        if (USER_DAO.approveUser(admin.id(), id)) {
            System.out.println("  User " + id + " approved.");
        } else {
            System.out.println("  No pending user with ID " + id + ".");
        }
    }

    private static void updateUser(User admin) throws SQLException {
        printUsers(USER_DAO.getAllUsers());
        int id = readInt("Enter User ID to edit (0 to cancel): ");
        if (id == 0) {
            return;
        }
        if (id == admin.id()) {
            System.out.println("  You cannot change your own role/status.");
            return;
        }
        System.out.println("New role:   1. ADMIN  2. PHARMACY_STAFF  3. NURSE  4. keep current");
        String role = switch (readIntInRange("Choose: ", 1, 4)) {
            case 1 -> "ADMIN";
            case 2 -> "PHARMACY_STAFF";
            case 3 -> "NURSE";
            default -> null;
        };
        System.out.println("New status: 1. ACTIVE  2. INACTIVE  3. keep current");
        String status = switch (readIntInRange("Choose: ", 1, 3)) {
            case 1 -> "ACTIVE";
            case 2 -> "INACTIVE";
            default -> null;
        };
        if (USER_DAO.updateUserRoleAndStatus(admin.id(), id, role, status)) {
            System.out.println("  User " + id + " updated.");
        } else {
            System.out.println("  Nothing changed (user not found or values are the same).");
        }
    }

    private static void printUsers(List<User> users) {
        String fmt = "%-5s | %-15s | %-27s | %-15s | %-10s%n";
        System.out.println();
        System.out.printf(fmt, "ID", "Username", "Full Name", "Role", "Status");
        System.out.println("-".repeat(85));
        for (User u : users) {
            System.out.printf(fmt, u.id(), u.username(), u.fullName(), u.role(), u.status());
        }
    }

    // ------------------------------------------------------------ audit logs

    private static void auditLogMenu(User admin) {
        while (true) {
            System.out.println("\n=== SYSTEM AUDIT LOGS ===");
            System.out.println("1. View all system logs");
            System.out.println("2. Filter logs by User ID");
            System.out.println("3. Clear all logs");
            System.out.println("0. Back");

            switch (readIntInRange("Choose: ", 0, 3)) {
                case 1 -> printLogs(LOG_DAO.getAllLogs(), "ALL SYSTEM LOGS");
                case 2 -> {
                    int userId = readInt("Enter User ID to filter: "); // re-prompts on non-numeric input
                    printLogs(LOG_DAO.getLogsByUserId(userId), "LOGS FOR USER ID " + userId);
                }
                case 3 -> clearLogs(admin);
                default -> {
                    return;
                }
            }
        }
    }

    /** Safe clear: needs the exact word CLEAR, and the clearing itself is recorded afterwards. */
    private static void clearLogs(User admin) {
        System.out.println("  WARNING: this permanently deletes ALL audit log entries.");
        String answer = readLine("  Type CLEAR to confirm (anything else cancels): ");
        if (!"CLEAR".equals(answer)) {
            System.out.println("  Cancelled. No logs were deleted.");
            return;
        }
        int deleted = LOG_DAO.clearAllLogs();
        if (deleted >= 0) {
            LOG_DAO.logAction(admin.id(), "LOGS_CLEARED",
                    "Admin " + admin.id() + " cleared " + deleted + " audit log entries.");
            System.out.println("  " + deleted + " log entries deleted.");
        }
    }

    private static void printLogs(List<ActivityLog> logs, String title) {
        int width = 5 + 8 + 18 + DESC_WIDTH + 20 + 4 * 3;
        System.out.println("\n" + title + " (" + logs.size() + " entries)");
        System.out.printf(LOG_FORMAT, "ID", "User ID", "Action Type", "Description", "Timestamp");
        System.out.println("-".repeat(width));
        if (logs.isEmpty()) {
            System.out.println("(no log entries found)");
            return;
        }
        for (ActivityLog log : logs) {
            List<String> lines = wrap(log.description(), DESC_WIDTH);
            System.out.printf(LOG_FORMAT, log.id(), log.userId(), log.actionType(), lines.get(0), log.timestamp());
            // Long descriptions continue on extra lines so the columns stay aligned and nothing is cut off.
            for (int i = 1; i < lines.size(); i++) {
                System.out.printf(LOG_FORMAT, "", "", "", lines.get(i), "");
            }
        }
        System.out.println("-".repeat(width));
    }

    /** Word-wraps text to the given width (hard-breaks words longer than the width). */
    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            while (word.length() > width) {
                if (line.length() > 0) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                lines.add(word.substring(0, width));
                word = word.substring(width);
            }
            if (line.length() + word.length() + (line.length() > 0 ? 1 : 0) > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0 || lines.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }
}
