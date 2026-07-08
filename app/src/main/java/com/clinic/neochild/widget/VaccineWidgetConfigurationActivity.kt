package com.clinic.neochild.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class VaccineWidgetConfigurationActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the result to CANCELED. This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        // Find the widget id from the intent.
        intent.extras?.let {
            appWidgetId = it.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        // If this activity was started with an invalid widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    OpacityConfigurationScreen(
                        onConfirm = { opacity ->
                            saveOpacity(opacity)
                        }
                    )
                }
            }
        }
    }

    private fun saveOpacity(opacity: Float) {
        val scope = MainScope()
        scope.launch {
            val glanceId = GlanceAppWidgetManager(this@VaccineWidgetConfigurationActivity)
                .getGlanceIdBy(appWidgetId)
            
            updateAppWidgetState(this@VaccineWidgetConfigurationActivity, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    set(VaccineWidget.OPACITY_KEY, opacity)
                }
            }
            VaccineWidget().update(this@VaccineWidgetConfigurationActivity, glanceId)

            val resultValue = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }
}

@Composable
fun OpacityConfigurationScreen(onConfirm: (Float) -> Unit) {
    var opacity by remember { mutableStateOf(0.8f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Widget Background Opacity",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = "Opacity: ${(opacity * 100).toInt()}%",
            style = MaterialTheme.typography.bodyLarge
        )
        
        Slider(
            value = opacity,
            onValueChange = { opacity = it },
            valueRange = 0.1f..1.0f,
            modifier = Modifier.padding(vertical = 24.dp)
        )
        
        Button(
            onClick = { onConfirm(opacity) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Apply Settings")
        }
    }
}
