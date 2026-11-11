package com.yourdomain.dao;

import com.yourdomain.model.Medicine;
import com.yourdomain.model.StockBatch;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** All SQL for medicines, stock batches and dispensing. Business events are audit-logged from here. */
public class MedicineDao {

    private final ActivityLogDao logDao = new ActivityLogDao();

    // ------------------------------------------------------------------ CREATE

    public int addMedicine(int userId, String genericName, String brandName, String dosageForm,
                           String strength, String unit, int reorderLevel) throws SQLException {
        String sql = "INSERT INTO medicines (generic_name, brand_name, dosage_form, strength, unit, reorder_level) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        int id;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, genericName);
            ps.setString(2, brandName);
            ps.setString(3, dosageForm);
            ps.setString(4, strength);
            ps.setString(5, unit);
            ps.setInt(6, reorderLevel);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                id = keys.next() ? keys.getInt(1) : -1;
            }
        }
        logDao.logAction(userId, "MEDICINE_ADDED",
                "Added medicine #" + id + " " + genericName + " " + strength + " (" + dosageForm + ")");
        return id;
    }

    /** Intake of a new batch -> logs BATCH_RECEIVED. */
    public int receiveBatch(int userId, int medicineId, String batchNo, int quantity,
                            String expiryDate, String source) throws SQLException {
        String medName = findActiveMedicineName(medicineId);
        if (medName == null) {
            throw new IllegalStateException("Medicine ID " + medicineId + " not found or inactive.");
        }
        String sql = "INSERT INTO stock_batches (medicine_id, batch_no, quantity_received, quantity_remaining, "
                + "expiry_date, source) VALUES (?, ?, ?, ?, ?, ?)";
        int batchId;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, medicineId);
            ps.setString(2, batchNo);
            ps.setInt(3, quantity);
            ps.setInt(4, quantity);
            ps.setString(5, expiryDate);
            ps.setString(6, source);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                batchId = keys.next() ? keys.getInt(1) : -1;
            }
        }
        logDao.logAction(userId, "BATCH_RECEIVED",
                "Received Batch #" + batchId + " (" + batchNo + ") of " + medName
                        + " - Qty: " + quantity + ", Expiry: " + expiryDate);
        return batchId;
    }

    /**
     * CORE BUSINESS PROCESS: dispense a medicine to a patient.
     * Stock is deducted earliest-expiry-first (FEFO) inside ONE transaction, then logs MEDICINE_DISPENSED.
     */
    public void dispenseMedicine(int userId, int medicineId, String patientName, int patientAge, int quantity)
            throws SQLException {
        String medLabel;
        StringBuilder batchSummary = new StringBuilder();

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                medLabel = null;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT generic_name, strength FROM medicines WHERE medicine_id = ? AND status = 'ACTIVE'")) {
                    ps.setInt(1, medicineId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            medLabel = rs.getString("generic_name") + " " + rs.getString("strength");
                        }
                    }
                }
                if (medLabel == null) {
                    throw new IllegalStateException("Medicine ID " + medicineId + " not found or inactive.");
                }

                // Pick usable batches, earliest expiry first.
                List<int[]> picks = new ArrayList<>(); // {batchId, quantityTaken}
                int remaining = quantity;
                String pickSql = "SELECT batch_id, quantity_remaining FROM stock_batches "
                        + "WHERE medicine_id = ? AND status = 'ACTIVE' AND quantity_remaining > 0 "
                        + "AND expiry_date >= date('now','localtime') ORDER BY expiry_date ASC, batch_id ASC";
                try (PreparedStatement ps = conn.prepareStatement(pickSql)) {
                    ps.setInt(1, medicineId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next() && remaining > 0) {
                            int take = Math.min(remaining, rs.getInt("quantity_remaining"));
                            picks.add(new int[]{rs.getInt("batch_id"), take});
                            remaining -= take;
                        }
                    }
                }
                if (remaining > 0) {
                    throw new IllegalStateException("Insufficient unexpired stock. Available: "
                            + (quantity - remaining) + ", requested: " + quantity);
                }

                for (int[] pick : picks) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE stock_batches SET quantity_remaining = quantity_remaining - ? WHERE batch_id = ?")) {
                        ps.setInt(1, pick[1]);
                        ps.setInt(2, pick[0]);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO dispensing_records (medicine_id, batch_id, patient_name, patient_age, "
                                    + "quantity, dispensed_by) VALUES (?, ?, ?, ?, ?, ?)")) {
                        ps.setInt(1, medicineId);
                        ps.setInt(2, pick[0]);
                        ps.setString(3, patientName);
                        ps.setInt(4, patientAge);
                        ps.setInt(5, pick[1]);
                        ps.setInt(6, userId);
                        ps.executeUpdate();
                    }
                    if (batchSummary.length() > 0) {
                        batchSummary.append(", ");
                    }
                    batchSummary.append("#").append(pick[0]).append("(").append(pick[1]).append(")");
                }
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        }

        // Logged only AFTER a successful commit, so the log never describes something that was rolled back.
        logDao.logAction(userId, "MEDICINE_DISPENSED",
                "Dispensed " + quantity + " x " + medLabel + " to patient " + patientName
                        + " - Batches: " + batchSummary);
    }

    // ------------------------------------------------------------------ UPDATE

    /** LIFECYCLE / STATE CHANGE: e.g. ACTIVE -> DISPOSED. Logs BATCH_STATUS_UPDATED. */
    public void updateBatchStatus(int userId, int batchId, String newStatus, String reason) throws SQLException {
        String oldStatus = null;
        String batchNo = null;
        String medName = null;
        String select = "SELECT b.status, b.batch_no, m.generic_name FROM stock_batches b "
                + "JOIN medicines m ON m.medicine_id = b.medicine_id WHERE b.batch_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(select)) {
            ps.setInt(1, batchId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    oldStatus = rs.getString("status");
                    batchNo = rs.getString("batch_no");
                    medName = rs.getString("generic_name");
                }
            }
        }
        if (oldStatus == null) {
            throw new IllegalStateException("Batch ID " + batchId + " not found.");
        }
        if (oldStatus.equals(newStatus)) {
            throw new IllegalStateException("Batch #" + batchId + " is already " + newStatus + ".");
        }

        String update = "UPDATE stock_batches SET status = ?, disposal_reason = ? WHERE batch_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(update)) {
            ps.setString(1, newStatus);
            ps.setString(2, reason);
            ps.setInt(3, batchId);
            ps.executeUpdate();
        }
        logDao.logAction(userId, "BATCH_STATUS_UPDATED",
                "Updated Batch #" + batchId + " (" + medName + ", " + batchNo + ") status from "
                        + oldStatus + " to " + newStatus + " - Reason: " + reason);
    }

    // -------------------------------------------------------------------- READ

    public List<Medicine> getAllMedicines() throws SQLException {
        String sql = "SELECT m.medicine_id, m.generic_name, m.brand_name, m.dosage_form, m.strength, m.unit, "
                + "m.reorder_level, m.status, "
                + "COALESCE((SELECT SUM(b.quantity_remaining) FROM stock_batches b "
                + "          WHERE b.medicine_id = m.medicine_id AND b.status = 'ACTIVE'), 0) AS total_stock "
                + "FROM medicines m ORDER BY m.medicine_id";
        List<Medicine> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Medicine(rs.getInt("medicine_id"), rs.getString("generic_name"),
                        rs.getString("brand_name"), rs.getString("dosage_form"), rs.getString("strength"),
                        rs.getString("unit"), rs.getInt("reorder_level"), rs.getString("status"),
                        rs.getInt("total_stock")));
            }
        }
        return list;
    }

    public List<StockBatch> getAllBatches() throws SQLException {
        String sql = "SELECT b.batch_id, b.medicine_id, m.generic_name, b.batch_no, b.quantity_received, "
                + "b.quantity_remaining, b.expiry_date, b.date_received, b.source, b.status "
                + "FROM stock_batches b JOIN medicines m ON m.medicine_id = b.medicine_id "
                + "ORDER BY b.expiry_date, b.batch_id";
        List<StockBatch> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new StockBatch(rs.getInt("batch_id"), rs.getInt("medicine_id"),
                        rs.getString("generic_name"), rs.getString("batch_no"), rs.getInt("quantity_received"),
                        rs.getInt("quantity_remaining"), rs.getString("expiry_date"),
                        rs.getString("date_received"), rs.getString("source"), rs.getString("status")));
            }
        }
        return list;
    }

    private String findActiveMedicineName(int medicineId) throws SQLException {
        String sql = "SELECT generic_name, strength FROM medicines WHERE medicine_id = ? AND status = 'ACTIVE'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, medicineId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("generic_name") + " " + rs.getString("strength") : null;
            }
        }
    }
}
