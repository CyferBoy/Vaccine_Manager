package com.clinic.neochild.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.clinic.neochild.domain.repository.ReminderRepository
import com.clinic.neochild.domain.repository.VaccinationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var vaccinationRepository: VaccinationRepository

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val reminderId = intent.getLongExtra("reminderId", -1L)
        val vaccinationId = intent.getStringExtra("vaccinationId")

        when (action) {
            "ACTION_MARK_VACCINATED" -> {
                if (vaccinationId != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        vaccinationRepository.markAsDone(vaccinationId)
                        if (reminderId != -1L) reminderRepository.markCompleted(reminderId)
                    }
                    Toast.makeText(context, "Marked as vaccinated", Toast.LENGTH_SHORT).show()
                }
            }
            "ACTION_DISMISS" -> {
                if (reminderId != -1L) {
                    CoroutineScope(Dispatchers.IO).launch {
                        reminderRepository.markCompleted(reminderId)
                    }
                }
            }
        }
    }
}
