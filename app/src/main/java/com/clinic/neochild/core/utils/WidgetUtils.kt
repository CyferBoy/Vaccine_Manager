package com.clinic.neochild.core.utils

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.clinic.neochild.features.widget.VaccineWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object WidgetUtils {
    fun updateWidget(context: Context) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val manager = GlanceAppWidgetManager(context)
                val ids = manager.getGlanceIds(VaccineWidget::class.java)
                ids.forEach { id ->
                    VaccineWidget().update(context, id)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
