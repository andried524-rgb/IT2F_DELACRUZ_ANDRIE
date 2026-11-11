package com.yourdomain.model;

/** A medicine record together with its current usable stock. */
public record Medicine(int id, String genericName, String brandName, String dosageForm,
                       String strength, String unit, int reorderLevel, String status, int totalStock) {
}
