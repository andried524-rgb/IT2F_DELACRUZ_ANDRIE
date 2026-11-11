package com.yourdomain.ui;

import com.yourdomain.model.User;

import static com.yourdomain.ui.ConsoleInput.readIntInRange;
import static com.yourdomain.ui.ConsoleInput.safely;

/** Menu for Pharmacy / Inventory Staff and Nurses. */
public final class StaffMenu {

    private StaffMenu() {
    }

    public static void show(User user) {
        boolean pharmacy = "PHARMACY_STAFF".equals(user.role());
        while (true) {
            System.out.println("\n=== " + (pharmacy ? "PHARMACY STAFF" : "NURSE") + " MENU ===");
            System.out.println("1. Dispense medicine to patient");
            System.out.println("2. View medicines & stock levels");
            System.out.println("3. View stock batches");
            if (pharmacy) {
                System.out.println("4. Receive medicine batch");
                System.out.println("5. Dispose expired / damaged batch");
            }
            System.out.println("0. Logout");

            int choice = readIntInRange("Choose: ", 0, pharmacy ? 5 : 3);
            switch (choice) {
                case 1 -> safely(() -> MedicineActions.dispense(user));
                case 2 -> safely(MedicineActions::viewMedicines);
                case 3 -> safely(MedicineActions::viewBatches);
                case 4 -> safely(() -> MedicineActions.receiveBatch(user));
                case 5 -> safely(() -> MedicineActions.disposeBatch(user));
                default -> {
                    return;
                }
            }
        }
    }
}
