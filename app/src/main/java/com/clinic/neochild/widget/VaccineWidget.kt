package com.clinic.neochild.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.glance.*
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.action.actionParametersOf
import androidx.glance.action.ActionParameters
import androidx.glance.layout.*
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.clinic.neochild.data.local.AppDatabase
import com.clinic.neochild.data.local.entity.toPatient
import com.clinic.neochild.data.local.entity.toVaccination
import com.clinic.neochild.data.model.Patient
import com.clinic.neochild.data.model.Vaccination
import com.clinic.neochild.core.common.Constants
import com.clinic.neochild.core.utils.PatientUtils
import com.clinic.neochild.domain.logic.ReminderEngine
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

class VaccineWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    companion object {
        val OPACITY_KEY = floatPreferencesKey("widget_opacity")
        private const val DEFAULT_OPACITY = 0.8f
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = AppDatabase.getDatabase(context)
        val vaccinations = database.vaccinationDao().getAllVaccinations().first().map { it.toVaccination() }
        val patients = database.patientDao().getAllPatients().first().map { it.toPatient() }
        val patientMap = patients.associateBy { it.id }

        val todayCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val today = todayCalendar.time

        // Use the new Requirement-Based Reminder Engine
        val unsatisfied = ReminderEngine.getPotentialRequirements(vaccinations)
        
        // Convert requirements to Vaccination objects for display
        val filteredVaccinations = unsatisfied.groupBy { it.patientId + PatientUtils.formatDate(it.dueDate) }
            .mapNotNull { (_, reqs) ->
                val first = reqs.first()
                vaccinations.find { it.id == first.originalVisitId }?.copy(
                    nxtVaccineNames = reqs.map { it.vaccineName },
                    nextDueDate = PatientUtils.formatDate(first.dueDate)
                )
            }
            .sortedBy { PatientUtils.parseDate(it.nextDueDate) }
            .take(20) // Increased limit to 20 items

        provideContent {
            val prefs = currentState<Preferences>()
            val opacity = prefs[OPACITY_KEY] ?: DEFAULT_OPACITY
            
            GlanceTheme {
                WidgetContent(context, filteredVaccinations, patientMap, today, opacity)
            }
        }
    }

    @Composable
    private fun WidgetContent(
        context: Context,
        vaccinations: List<Vaccination>,
        patientMap: Map<String, Patient>,
        today: Date,
        opacity: Float
    ) {
        // Resolve surface color and apply opacity
        val resolvedBgColor = GlanceTheme.colors.surface.getColor(context).copy(alpha = opacity)
        // Ensure text is readable on the background (usually onSurface)
        val textColorProvider = GlanceTheme.colors.onSurface
        
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(resolvedBgColor)
                .cornerRadius(24.dp)
                .padding(9.dp)
                .clickable(
                    actionStartActivity<com.clinic.neochild.MainActivity>(
                        actionParametersOf(
                            ActionParameters.Key<Boolean>("OPEN_DUE_TAB") to true
                        )
                    )
                )
        ) {
            // Header
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Due Vaccine",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColorProvider
                        )
                    )
                    Text(
                        text = " ▾",
                        style = TextStyle(fontSize = 12.sp, color = textColorProvider)
                    )
                }
            }

            if (vaccinations.isEmpty()) {
                Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No upcoming vaccinations",
                        style = TextStyle(fontSize = 14.sp, color = textColorProvider)
                    )
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(vaccinations) { vacc ->
                        val patient = patientMap[vacc.patientId]
                        VaccineRow(vacc, patient, today, textColorProvider)
                    }
                }
            }
        }
    }

    @Composable
    private fun VaccineRow(vacc: Vaccination, patient: Patient?, today: Date, textColorProvider: ColorProvider) {
        val dateFormat = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH)
        val displayDateFormat = SimpleDateFormat("MMM d", Locale.ENGLISH)
        
        val dueDate = try { dateFormat.parse(vacc.nextDueDate) } catch (_: Exception) { null }
        
        // Use theme colors instead of hardcoded ColorProvider(Color) to avoid restricted API issues
        val overdueColor = GlanceTheme.colors.error
        val otherDateColor = GlanceTheme.colors.onSurfaceVariant

        val (dateText, dateColorProvider) = when {
            dueDate == null -> "" to otherDateColor
            dueDate.before(today) -> displayDateFormat.format(dueDate) to overdueColor // Red-ish from theme
            dueDate == today -> "Today" to otherDateColor
            else -> displayDateFormat.format(dueDate) to otherDateColor
        }

        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = patient?.name ?: "Unknown",
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    fontSize = 12.sp,
                    color = textColorProvider
                ),
                maxLines = 1
            )
            
            Text(
                text = dateText,
                style = TextStyle(
                    fontSize = 11.sp,
                    color = dateColorProvider
                )
            )
        }
    }
}
