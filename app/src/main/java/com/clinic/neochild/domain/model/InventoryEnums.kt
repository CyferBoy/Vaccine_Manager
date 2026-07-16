package com.clinic.neochild.domain.model

enum class InventoryTransactionType {
    PURCHASE,
    VACCINATION,
    RETURN,
    EXPIRED,
    DAMAGED,
    MANUAL_ADJUSTMENT
}

enum class BatchStatus {
    ACTIVE,
    EXPIRED,
    USED,
    DELETED
}
