package com.clinic.neochild.domain.model

enum class InventoryTransactionType {
    PURCHASE,
    VACCINATION,
    RETURN,
    EXPIRED,
    DAMAGED,
    COLD_CHAIN_FAILURE,
    CONTAMINATED,
    OTHER,
    MANUAL_ADJUSTMENT
}

enum class BatchStatus {
    ACTIVE,
    EXPIRED,
    USED,
    DELETED
}
