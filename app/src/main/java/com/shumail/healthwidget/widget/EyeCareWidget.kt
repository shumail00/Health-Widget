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
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.shumail.healthwidget.R
import com.shumail.healthwidget.data.TimerRepository
import com.shumail.healthwidget.model.TimerModel
import com.shumail.healthwidget.model.TimerStatus
import com.shumail.healthwidget.ui.MainActivity

class EyeCareWidget : GlanceAppWidget() {

    companion object {
        private val COMPACT_SIZE = DpSize(180.dp, 100.dp)
        private val MEDIUM_SIZE = DpSize(260.dp, 110.dp)
        private val TABLET_4X2 = DpSize(380.dp, 130.dp)
    }

    override val sizeMode = SizeMode.Responsive(
        setOf(COMPACT_SIZE, MEDIUM_SIZE, TABLET_4X2)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = TimerRepository.getInstance(context)
        val timer = repository.getTimerModel()

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                WidgetContent(context, timer, size)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun WidgetContent(context: Context, timer: TimerModel, size: DpSize) {
    val effectiveStatus = timer.getEffectiveStatus()
    val isTablet = size.width >= 360.dp

    val bgColor = Color(0xFF142220)
    val cardSurface = Color(0xFF1F3230)
    val tealAccent = Color(0xFF5FE5D2)
    val onBg = Color(0xFFE2E9E7)
    val subText = Color(0xFFA0B2AF)
    val warmFinish = Color(0xFFFFD56B)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .cornerRadius(18.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        when (effectiveStatus) {
            TimerStatus.READY -> {
                ReadyLayout(
                    isTablet = isTablet,
                    surfaceColor = cardSurface,
                    accentColor = tealAccent,
                    textColor = onBg,
                    subColor = subText
                )
            }
            TimerStatus.RUNNING -> {
                RunningLayout(
                    timer = timer,
                    isTablet = isTablet,
                    accentColor = tealAccent,
                    textColor = onBg,
                    subColor = subText
                )
            }
            TimerStatus.FINISHED -> {
                FinishedLayout(
                    isTablet = isTablet,
                    accentColor = warmFinish,
                    textColor = onBg,
                    subColor = subText
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ReadyLayout(
    isTablet: Boolean,
    surfaceColor: Color,
    accentColor: Color,
    textColor: Color,
    subColor: Color
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_eye_care),
                    contentDescription = "Eye Care",
                    modifier = GlanceModifier.size(20.dp)
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = "Eye Care Timer",
                    style = TextStyle(
                        color = ColorProvider(accentColor),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "10-Minute Break",
                style = TextStyle(
                    color = ColorProvider(textColor),
                    fontSize = if (isTablet) 20.sp else 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            if (isTablet) {
                Text(
                    text = "Rest your eyes & look 20 ft away",
                    style = TextStyle(
                        color = ColorProvider(subColor),
                        fontSize = 12.sp
                    )
                )
            }
        }

        Spacer(modifier = GlanceModifier.width(8.dp))

        Button(
            text = "Start 10m",
            onClick = actionRunCallback<StartTimerActionCallback>(),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = ColorProvider(accentColor),
                contentColor = ColorProvider(Color(0xFF003731))
            ),
            modifier = GlanceModifier.padding(vertical = 4.dp)
        )
    }
}

@androidx.compose.runtime.Composable
private fun RunningLayout(
    timer: TimerModel,
    isTablet: Boolean,
    accentColor: Color,
    textColor: Color,
    subColor: Color
) {
    val remaining = timer.formattedRemaining()

    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_timer),
                    contentDescription = "Running",
                    modifier = GlanceModifier.size(18.dp)
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = "Timer Running",
                    style = TextStyle(
                        color = ColorProvider(accentColor),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = remaining,
                style = TextStyle(
                    color = ColorProvider(textColor),
                    fontSize = if (isTablet) 28.sp else 22.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            if (isTablet) {
                Text(
                    text = "Eye protection timer in progress",
                    style = TextStyle(
                        color = ColorProvider(subColor),
                        fontSize = 11.sp
                    )
                )
            }
        }

        Spacer(modifier = GlanceModifier.width(8.dp))

        Button(
            text = "Cancel",
            onClick = actionRunCallback<CancelTimerActionCallback>(),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = ColorProvider(Color(0xFF3B4D4A)),
                contentColor = ColorProvider(Color(0xFFFFFFFF))
            ),
            modifier = GlanceModifier.padding(vertical = 4.dp)
        )
    }
}

@androidx.compose.runtime.Composable
private fun FinishedLayout(
    isTablet: Boolean,
    accentColor: Color,
    textColor: Color,
    subColor: Color
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_check_circle),
                    contentDescription = "Finished",
                    modifier = GlanceModifier.size(20.dp)
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = "Time's Up!",
                    style = TextStyle(
                        color = ColorProvider(accentColor),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = "Rest Your Eyes",
                style = TextStyle(
                    color = ColorProvider(textColor),
                    fontSize = if (isTablet) 20.sp else 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = if (isTablet) "Look 20 feet away for 20 seconds now" else "Look away from screen",
                style = TextStyle(
                    color = ColorProvider(subColor),
                    fontSize = 11.sp
                )
            )
        }

        Spacer(modifier = GlanceModifier.width(8.dp))

        Button(
            text = "Reset",
            onClick = actionRunCallback<ResetTimerActionCallback>(),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = ColorProvider(accentColor),
                contentColor = ColorProvider(Color(0xFF332000))
            ),
            modifier = GlanceModifier.padding(vertical = 4.dp)
        )
    }
}

class StartTimerActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val repository = TimerRepository.getInstance(context)
        repository.startTimer()
        EyeCareWidget().update(context, glanceId)
    }
}

class CancelTimerActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val repository = TimerRepository.getInstance(context)
        repository.cancelTimer()
        EyeCareWidget().update(context, glanceId)
    }
}

class ResetTimerActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val repository = TimerRepository.getInstance(context)
        repository.resetTimer()
        EyeCareWidget().update(context, glanceId)
    }
}
