package com.yourdomain.ui;

import com.yourdomain.dao.MedicineDao;
import com.yourdomain.model.Medicine;
import com.yourdomain.model.StockBatch;
import com.yourdomain.model.User;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static com.yourdomain.ui.ConsoleInput.readDate;
import static com.yourdomain.ui.ConsoleInput.readInt;
import static com.yourdomain.ui.ConsoleInput.readLine;
import static com.yourdomain.ui.ConsoleInput.readNonEmpty;
import static com.yourdomain.ui.ConsoleInput.readPositiveInt;

/** Console screens for inventory work, shared by AdminMenu and StaffMenu. UI only - no SQL here. */
public final class MedicineActions {

    private static final MedicineDao MEDICINE_DAO = new MedicineDao();

    private MedicineActions() {
    }

    public static void addMedicine(User user) throws SQLException {
        System.out.println("\n--- ADD MEDICINE ---");
        String generic = readNonEmpty("Generic name: ");
        String brand = readLine("Brand name (optional): ");
        String form = readNonEmpty("Dosage form (tablet/syrup/capsule...): ");
        String strength = readNonEmpty("Strength (e.g. 500mg): ");
        String unit = readNonEmpty("Unit (e.g. tablet, bottle): ");
        int reorder = readInt("Reorder level: ");
        int id = MEDICINE_DAO.addMedicine(user.id(), generic, brand, form, strength, unit, Math.max(reorder, 0));
        System.out.println("  Medicine added with ID " + id + ".");
    }

    public static void receiveBatch(User user) throws SQLException {
        System.out.println("\n--- RECEIVE MEDICINE BATCH ---");
        viewMedicines();
        int medicineId = readInt("Medicine ID: ");
        String batchNo = readNonEmpty("Batch number: ");
        int qty = readPositiveInt("Quantity received: ");
        LocalDate expiry = readDate("Expiry date (YYYY-MM-DD): ");
        if (!expiry.isAfter(LocalDate.now())) {
            System.out.println("  Cannot receive a batch that is already expired.");
            return;
        }
        String source = readNonEmpty("Source (e.g. Provincial Health Office): ");
        int batchId = MEDICINE_DAO.receiveBatch(user.id(), medicineId, batchNo, qty, expiry.toString(), source);
        System.out.println("  Batch recorded with ID " + batchId + ".");
    }

    public static void dispense(User user) throws SQLException {
        System.out.println("\n--- DISPENSE MEDICINE ---");
        viewMedicines();
        int medicineId = readInt("Medicine ID: ");
        String patient = readNonEmpty("Patient name: ");
        int age = readInt("Patient age: ");
        int qty = readPositiveInt("Quantity to dispense: ");
        MEDICINE_DAO.dispenseMedicine(user.id(), medicineId, patient, Math.max(age, 0), qty);
        System.out.println("  Dispensed successfully (earliest-expiry batches used first).");
    }

    public static void disposeBatch(User user) throws SQLException {
        System.out.println("\n--- DISPOSE EXPIRED / DAMAGED BATCH ---");
        viewBatches();
        int batchId = readInt("Batch ID to dispose: ");
        String reason = readNonEmpty("Reason (expired / damaged / recalled): ");
        MEDICINE_DAO.updateBatchStatus(user.id(), batchId, "DISPOSED", reason);
        System.out.println("  Batch #" + batchId + " marked as DISPOSED.");
    }

    public static void viewMedicines() throws SQLException {
        List<Medicine> list = MEDICINE_DAO.getAllMedicines();
        String fmt = "%-5s | %-20s | %-10s | %-10s | %-8s | %-7s | %-8s%n";
        System.out.println();
        System.out.printf(fmt, "ID", "Generic Name", "Strength", "Form", "In Stock", "Reorder", "Status");
        System.out.println("-".repeat(86));
        if (list.isEmpty()) {
            System.out.println("(no medicines yet)");
        }
        for (Medicine m : list) {
            String flag = m.totalStock() <= m.reorderLevel() ? " LOW" : "";
            System.out.printf(fmt, m.id(), cut(m.genericName(), 20), cut(m.strength(), 10),
                    cut(m.dosageForm(), 10), m.totalStock() + flag, m.reorderLevel(), m.status());
        }
    }

    public static void viewBatches() throws SQLException {
        List<StockBatch> list = MEDICINE_DAO.getAllBatches();
        String fmt = "%-6s | %-20s | %-10s | %-6s | %-10s | %-10s%n";
        System.out.println();
        System.out.printf(fmt, "Batch", "Medicine", "Batch No", "Left", "Expiry", "Status");
        System.out.println("-".repeat(78));
        if (list.isEmpty()) {
            System.out.println("(no batches yet)");
        }
        for (StockBatch b : list) {
            System.out.printf(fmt, b.batchId(), cut(b.medicineName(), 20), cut(b.batchNo(), 10),
                    b.quantityRemaining(), b.expiryDate(), b.status());
        }
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
