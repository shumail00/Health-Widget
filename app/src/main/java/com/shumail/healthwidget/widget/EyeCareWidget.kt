package com.shumail.healthwidget.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.shumail.healthwidget.R
import com.shumail.healthwidget.data.MedicationRepository
import com.shumail.healthwidget.model.ActiveTimerState
import com.shumail.healthwidget.model.ActiveTimerStatus
import com.shumail.healthwidget.model.DoseItem
import com.shumail.healthwidget.model.MedicationId
import com.shumail.healthwidget.ui.MainActivity
import java.time.LocalDate

class EyeCareWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = MedicationRepository.getInstance(context)
        val today = LocalDate.now()
        val doses = repository.getDosesForDate(today)
        val timer = repository.getActiveTimer()
        val dayOfCourse = repository.getDayOfCourse(today)

        provideContent {
            GlanceTheme {
                WidgetCard(context, doses, timer, dayOfCourse)
            }
        }
    }
}

private val KEY_MEDICATION = ActionParameters.Key<String>("medication")
private val KEY_DOSE_INDEX = ActionParameters.Key<Int>("doseIndex")

@androidx.compose.runtime.Composable
private fun WidgetCard(
    context: Context,
    todayDoses: List<DoseItem>,
    timer: ActiveTimerState,
    dayOfCourse: Int
) {
    val bgDark = Color(0xFF0F172A)      // Deep slate / midnight navy
    val cardSurface = Color(0xFF1E293B) // Card surface
    val textPrimary = Color(0xFFF8FAFC)
    val textSecondary = Color(0xFF94A3B8)

    // Determine current widget state
    val timerStatus = timer.getEffectiveStatus()

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgDark)
            .cornerRadius(20.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center
    ) {
        when {
            // STATE 3: ⏱ 10-minute countdown active for AQUAWELL
            timerStatus == ActiveTimerStatus.RUNNING -> {
                AquawellCountdownState(
                    timer = timer,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )
            }

            // STATE 4: 💧 Timer finished, AQUAWELL is due
            timerStatus == ActiveTimerStatus.FINISHED_AQUAWELL_DUE -> {
                AquawellDueState(
                    doseIndex = timer.aquawellDoseIndex,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )
            }

            // Otherwise, check scheduled doses for today
            else -> {
                // Find next pending dose
                val nextDose = todayDoses.firstOrNull { !it.isTaken }
                if (nextDose != null) {
                    when (nextDose.medicationId) {
                        // STATE 1: 💊 MULMIN Due
                        MedicationId.MULMIN -> {
                            MulminDueState(
                                dose = nextDose,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary
                            )
                        }
                        // STATE 2: 🔴 KETOGATE Due
                        MedicationId.KETOGATE -> {
                            KetogateDueState(
                                dose = nextDose,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary
                            )
                        }
                        // AQUAWELL Scheduled / Due
                        MedicationId.AQUAWELL -> {
                            AquawellScheduledState(
                                dose = nextDose,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary
                            )
                        }
                    }
                } else {
                    // All doses completed today or course not active
                    AllDosesDoneState(
                        dayOfCourse = dayOfCourse,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------
// STATE 1: MULMIN DUE
// ----------------------------------------------------
@androidx.compose.runtime.Composable
private fun MulminDueState(
    dose: DoseItem,
    textPrimary: Color,
    textSecondary: Color
) {
    val amberAccent = Color(0xFFF59E0B)

    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_pill),
                contentDescription = "Tablet",
                modifier = GlanceModifier.size(20.dp)
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = "MULMIN",
                style = TextStyle(
                    color = ColorProvider(amberAccent),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = "Morning Tablet",
            style = TextStyle(
                color = ColorProvider(textSecondary),
                fontSize = 11.sp
            )
        )
        Spacer(modifier = GlanceModifier.height(10.dp))
        Button(
            text = "✓ MARK TAKEN",
            onClick = actionRunCallback<MarkDoseTakenGlanceActionCallback>(
                actionParametersOf(
                    KEY_MEDICATION to MedicationId.MULMIN.name,
                    KEY_DOSE_INDEX to dose.doseIndex
                )
            ),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = ColorProvider(amberAccent),
                contentColor = ColorProvider(Color(0xFF261800))
            ),
            modifier = GlanceModifier.fillMaxWidth().height(42.dp)
        )
    }
}

// ----------------------------------------------------
// STATE 2: KETOGATE DUE
// ----------------------------------------------------
@androidx.compose.runtime.Composable
private fun KetogateDueState(
    dose: DoseItem,
    textPrimary: Color,
    textSecondary: Color
) {
    val roseAccent = Color(0xFFF43F5E)

    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_eye_care),
                contentDescription = "Eye drops",
                modifier = GlanceModifier.size(20.dp)
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = "KETOGATE",
                style = TextStyle(
                    color = ColorProvider(roseAccent),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = dose.label,
            style = TextStyle(
                color = ColorProvider(textSecondary),
                fontSize = 11.sp
            )
        )
        Spacer(modifier = GlanceModifier.height(10.dp))
        Button(
            text = "✓ TAKEN (Start 10m)",
            onClick = actionRunCallback<MarkDoseTakenGlanceActionCallback>(
                actionParametersOf(
                    KEY_MEDICATION to MedicationId.KETOGATE.name,
                    KEY_DOSE_INDEX to dose.doseIndex
                )
            ),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = ColorProvider(roseAccent),
                contentColor = ColorProvider(Color(0xFFFFFFFF))
            ),
            modifier = GlanceModifier.fillMaxWidth().height(42.dp)
        )
    }
}

// ----------------------------------------------------
// STATE 3: ⏱ AQUAWELL 10-MINUTE COUNTDOWN
// ----------------------------------------------------
@androidx.compose.runtime.Composable
private fun AquawellCountdownState(
    timer: ActiveTimerState,
    textPrimary: Color,
    textSecondary: Color
) {
    val cyanAccent = Color(0xFF38BDF8)
    val remaining = timer.formattedRemaining()

    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_timer),
                contentDescription = "Timer",
                modifier = GlanceModifier.size(18.dp)
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = "AQUAWELL IN",
                style = TextStyle(
                    color = ColorProvider(cyanAccent),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = remaining,
            style = TextStyle(
                color = ColorProvider(textPrimary),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Button(
            text = "Open Timer",
            onClick = actionStartActivity<MainActivity>(),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = ColorProvider(Color(0xFF334155)),
                contentColor = ColorProvider(cyanAccent)
            ),
            modifier = GlanceModifier.fillMaxWidth().height(38.dp)
        )
    }
}

// ----------------------------------------------------
// STATE 4: 💧 AQUAWELL TIMER FINISHED / DUE
// ----------------------------------------------------
@androidx.compose.runtime.Composable
private fun AquawellDueState(
    doseIndex: Int,
    textPrimary: Color,
    textSecondary: Color
) {
    val cyanAccent = Color(0xFF0284C7)
    val lightCyan = Color(0xFF38BDF8)

    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_drop),
                contentDescription = "Drop",
                modifier = GlanceModifier.size(20.dp)
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = "AQUAWELL DUE",
                style = TextStyle(
                    color = ColorProvider(lightCyan),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = "10-min interval complete",
            style = TextStyle(
                color = ColorProvider(textSecondary),
                fontSize = 11.sp
            )
        )
        Spacer(modifier = GlanceModifier.height(10.dp))
        Button(
            text = "✓ MARK TAKEN",
            onClick = actionRunCallback<MarkDoseTakenGlanceActionCallback>(
                actionParametersOf(
                    KEY_MEDICATION to MedicationId.AQUAWELL.name,
                    KEY_DOSE_INDEX to doseIndex
                )
            ),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = ColorProvider(cyanAccent),
                contentColor = ColorProvider(Color(0xFFFFFFFF))
            ),
            modifier = GlanceModifier.fillMaxWidth().height(42.dp)
        )
    }
}

// ----------------------------------------------------
// STANDALONE / SCHEDULED AQUAWELL
// ----------------------------------------------------
@androidx.compose.runtime.Composable
private fun AquawellScheduledState(
    dose: DoseItem,
    textPrimary: Color,
    textSecondary: Color
) {
    val cyanAccent = Color(0xFF0284C7)
    val lightCyan = Color(0xFF38BDF8)

    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_drop),
                contentDescription = "Drop",
                modifier = GlanceModifier.size(20.dp)
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = "AQUAWELL",
                style = TextStyle(
                    color = ColorProvider(lightCyan),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = dose.label,
            style = TextStyle(
                color = ColorProvider(textSecondary),
                fontSize = 11.sp
            )
        )
        Spacer(modifier = GlanceModifier.height(10.dp))
        Button(
            text = "✓ MARK TAKEN",
            onClick = actionRunCallback<MarkDoseTakenGlanceActionCallback>(
                actionParametersOf(
                    KEY_MEDICATION to MedicationId.AQUAWELL.name,
                    KEY_DOSE_INDEX to dose.doseIndex
                )
            ),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = ColorProvider(cyanAccent),
                contentColor = ColorProvider(Color(0xFFFFFFFF))
            ),
            modifier = GlanceModifier.fillMaxWidth().height(42.dp)
        )
    }
}

// ----------------------------------------------------
// STATE 5: ALL DOSES COMPLETED
// ----------------------------------------------------
@androidx.compose.runtime.Composable
private fun AllDosesDoneState(
    dayOfCourse: Int,
    textPrimary: Color,
    textSecondary: Color
) {
    val greenAccent = Color(0xFF10B981)

    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_check_circle),
            contentDescription = "Completed",
            modifier = GlanceModifier.size(26.dp)
        )
        Spacer(modifier = GlanceModifier.height(6.dp))
        Text(
            text = "All Doses Done",
            style = TextStyle(
                color = ColorProvider(greenAccent),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = if (dayOfCourse in 1..60) "Day $dayOfCourse complete 🎉" else "Course Completed",
            style = TextStyle(
                color = ColorProvider(textSecondary),
                fontSize = 11.sp
            )
        )
        Spacer(modifier = GlanceModifier.height(10.dp))
        Button(
            text = "Open App",
            onClick = actionStartActivity<MainActivity>(),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = ColorProvider(Color(0xFF1E293B)),
                contentColor = ColorProvider(textPrimary)
            ),
            modifier = GlanceModifier.fillMaxWidth().height(38.dp)
        )
    }
}

// ----------------------------------------------------
// ACTION CALLBACK
// ----------------------------------------------------
class MarkDoseTakenGlanceActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val medName = parameters[KEY_MEDICATION] ?: return
        val doseIdx = parameters[KEY_DOSE_INDEX] ?: 0
        val medId = try {
            MedicationId.valueOf(medName)
        } catch (_: Exception) {
            return
        }
        val repository = MedicationRepository.getInstance(context)
        repository.markDoseTaken(medId, doseIdx)
        EyeCareWidget().update(context, glanceId)
    }
}
