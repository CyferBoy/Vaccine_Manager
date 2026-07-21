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

enum class InventoryFilter { ALL, LOW_STOCK, NEAR_EXPIRY, EXPIRED, OUT_OF_STOCK, HIDDEN, AVAILABLE }
enum class InventorySort { ALPHABETICAL, HIGHEST_STOCK, LOWEST_STOCK, EXPIRY, MANUFACTURER, NEWEST, OLDEST }
