package com.clinic.neochild.features.widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.clinic.neochild.data.local.AppDatabase
import com.clinic.neochild.data.local.entity.WidgetDueEntity
import com.clinic.neochild.domain.repository.ReminderRepository
import com.clinic.neochild.domain.repository.PatientRepository
import com.clinic.neochild.core.utils.PatientUtils
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
            val dueList = reminderRepository.getDueList().first().take(20)
            val patients = patientRepository.allPatients.first().associateBy { it.id }
            
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.time

            val widgetItems = dueList.map { vacc ->
                val patient = patients[vacc.patientId]
                val dueDate = PatientUtils.parseDate(vacc.nextDueDate) ?: Date()
                WidgetDueEntity(
                    patientName = patient?.name ?: "Unknown",
                    vaccineName = vacc.nxtVaccineNames.joinToString(", "),
                    dueDate = vacc.nextDueDate,
                    isOverdue = dueDate.before(today)
                )
            }

            database.widgetDueDao().refreshCache(widgetItems)
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
