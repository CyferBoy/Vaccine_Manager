package com.clinic.neochild.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.clinic.neochild.data.local.database.AppDatabase
import com.clinic.neochild.data.local.entity.WidgetDueEntity
import com.clinic.neochild.domain.repository.ReminderRepository
import com.clinic.neochild.domain.repository.PatientRepository
import com.clinic.neochild.core.utils.PatientUtils
import com.clinic.neochild.core.utils.DateClassifier
import com.clinic.neochild.core.utils.DateCategory
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.*

/**
 * Background worker to pre-calculate widget data.
 * Offloads heavy business logic from the Widget's provideGlance.
 */
@HiltWorker
class WidgetWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reminderRepository: ReminderRepository,
    private val patientRepository: PatientRepository,
    private val database: AppDatabase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val dueList = reminderRepository.getDueList().first()
                .sortedBy { DateClassifier.getSortWeight(it.nextDueDate) }
                .take(20)
            
            val patients = patientRepository.allPatients.first().associateBy { it.id }
            
            val widgetItems = dueList.map { vacc ->
                val patient = patients[vacc.patientId]
                val category = DateClassifier.classify(vacc.nextDueDate)
                
                WidgetDueEntity(
                    patientName = patient?.name ?: "Unknown",
                    vaccineName = vacc.nxtVaccineNames.joinToString(", "),
                    dueDate = DateClassifier.formatDisplay(vacc.nextDueDate),
                    isOverdue = category is DateCategory.Overdue || category is DateCategory.Yesterday
                )
            }

            database.widgetDueDao().refreshCache(widgetItems)
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
