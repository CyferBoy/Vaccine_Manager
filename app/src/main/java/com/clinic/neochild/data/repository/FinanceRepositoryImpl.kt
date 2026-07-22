package com.clinic.neochild.data.repository

import com.clinic.neochild.data.local.dao.FinanceDao
import com.clinic.neochild.data.local.entity.FinanceEntity
import com.clinic.neochild.domain.repository.FinanceRepository
import com.clinic.neochild.domain.repository.SyncRepository
import com.clinic.neochild.core.model.SyncOperation
import com.clinic.neochild.core.model.SyncPriority
import com.clinic.neochild.core.logger.AuditLogger
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinanceRepositoryImpl @Inject constructor(
    private val financeDao: FinanceDao,
    private val syncRepository: SyncRepository,
    private val auditLogger: AuditLogger
) : FinanceRepository {

    override fun getAllTransactions(): Flow<List<FinanceEntity>> {
        return financeDao.getAllTransactions()
    }

    override fun getTransactionsForPatient(patientId: String): Flow<List<FinanceEntity>> {
        return financeDao.getTransactionsForPatient(patientId)
    }

    override fun getDailyIncome(start: Long): Flow<Double?> {
        return financeDao.getDailyIncome(start)
    }

    override suspend fun recordIncome(
        amount: Double,
        category: String,
        patientId: String?,
        visitId: String?,
        remarks: String?,
        recordedBy: String
    ) {
        val transaction = FinanceEntity(
            type = "INCOME",
            category = category,
            amount = amount,
            paymentMethod = "MIXED", // Simplified for now
            patientId = patientId,
            visitId = visitId,
            remarks = remarks,
            recordedBy = recordedBy,
            isSynced = false
        )
        val id = financeDao.insertTransaction(transaction)
        syncRepository.enqueue("FINANCE", id.toString(), SyncOperation.CREATE, SyncPriority.MEDIUM)
        
        auditLogger.log(
            module = "FINANCE",
            entityType = "TRANSACTION",
            entityId = id.toString(),
            action = "INCOME_RECORDED",
            patientId = patientId,
            newValue = amount.toString(),
            remarks = "Income of $amount recorded in $category"
        )
    }

    override suspend fun recordExpense(
        amount: Double,
        category: String,
        remarks: String?,
        recordedBy: String
    ) {
        val transaction = FinanceEntity(
            type = "EXPENSE",
            category = category,
            amount = amount,
            paymentMethod = "CASH",
            remarks = remarks,
            recordedBy = recordedBy,
            isSynced = false
        )
        val id = financeDao.insertTransaction(transaction)
        syncRepository.enqueue("FINANCE", id.toString(), SyncOperation.CREATE, SyncPriority.MEDIUM)

        auditLogger.log(
            module = "FINANCE",
            entityType = "TRANSACTION",
            entityId = id.toString(),
            action = "EXPENSE_RECORDED",
            newValue = amount.toString(),
            remarks = "Expense of $amount recorded in $category"
        )
    }
}
