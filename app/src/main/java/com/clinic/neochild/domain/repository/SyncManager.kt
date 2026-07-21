package com.clinic.neochild.domain.repository

interface SyncManager {
    fun scheduleSync()
    fun scheduleImmediateSync()
    fun cancelAllSync()
}
