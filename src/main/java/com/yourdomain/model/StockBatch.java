package com.yourdomain.model;

/** A received batch of a medicine. */
public record StockBatch(int batchId, int medicineId, String medicineName, String batchNo,
                         int quantityReceived, int quantityRemaining, String expiryDate,
                         String dateReceived, String source, String status) {
}
