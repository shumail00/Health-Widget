package com.shumail.healthwidget.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.shumail.healthwidget.alarm.TimerAlarmManager
import com.shumail.healthwidget.data.MedicationRepository
import com.shumail.healthwidget.model.ActiveTimerState
import com.shumail.healthwidget.model.ActiveTimerStatus
import com.shumail.healthwidget.model.DoseItem
import com.shumail.healthwidget.model.MedicationCatalog
import com.shumail.healthwidget.model.MedicationId
import com.shumail.healthwidget.model.MedicationType
import com.shumail.healthwidget.ui.theme.AquawellPrimary
import com.shumail.healthwidget.ui.theme.KetogatePrimary
import com.shumail.healthwidget.ui.theme.MintAccent
import com.shumail.healthwidget.ui.theme.MulminPrimary
import com.shumail.healthwidget.ui.theme.SuccessGreen
import com.shumail.healthwidget.ui.theme.WarmGold
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayScreen(
    repository: MedicationRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val doses = remember(repository.refreshTrigger.value) { repository.getDosesForDate(today) }
    val timer = remember(repository.refreshTrigger.value) { repository.getActiveTimer() }
    val dayOfCourse = remember(repository.refreshTrigger.value) { repository.getDayOfCourse(today) }
    val adherence = remember(repository.refreshTrigger.value) { repository.getTodayAdherence() }

    // Live clock ticker every 500ms to drive smooth timer countdowns
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timer.status) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(500)
        }
    }

    // Permission checks
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    val effectiveTimerStatus = timer.getEffectiveStatus(currentTimeMillis)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("today_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Greeting & Day Badge
                HeaderSection(dayOfCourse = dayOfCourse, today = today)

                // Today's Adherence Progress Card
                TodayProgressCard(adherence = adherence)

                // 10-Minute Timer Hero Card (Prominent when running or finished waiting)
                if (effectiveTimerStatus != ActiveTimerStatus.IDLE) {
                    ActiveTimerHeroCard(
                        timer = timer,
                        now = currentTimeMillis,
                        onMarkAquawellTaken = {
                            repository.markDoseTaken(
                                medicationId = MedicationId.AQUAWELL,
                                doseIndex = timer.aquawellDoseIndex
                            )
                        },
                        onCancelTimer = {
                            repository.cancelActiveTimer()
                        }
                    )
                } else if (adherence.nextDoseItem != null && !adherence.nextDoseItem.isTaken) {
                    // Quick Next Dose Card
                    NextDoseCard(
                        nextDose = adherence.nextDoseItem,
                        onMarkTaken = {
                            repository.markDoseTaken(
                                medicationId = adherence.nextDoseItem.medicationId,
                                doseIndex = adherence.nextDoseItem.doseIndex
                            )
                        }
                    )
                }

                // Missing Permission Banner
                if (!hasNotificationPermission) {
                    PermissionBanner(
                        onRequest = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                }

                // Section Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Today's Schedule",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${adherence.completedDoses} of ${adherence.totalDoses} taken",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Schedule Items List
        items(
            items = doses,
            key = { it.doseKey }
        ) { doseItem ->
            Box(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 5.dp)
            ) {
                DoseScheduleCard(
                    dose = doseItem,
                    onToggle = {
                        if (doseItem.isTaken) {
                            repository.markDoseUntaken(doseItem.medicationId, doseItem.doseIndex)
                        } else {
                            repository.markDoseTaken(doseItem.medicationId, doseItem.doseIndex)
                        }
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun HeaderSection(dayOfCourse: Int, today: LocalDate) {
    val greeting = remember {
        val hour = LocalTime.now().hour
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    val formattedDate = remember {
        today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = greeting,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            color = if (dayOfCourse in 1..60) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (dayOfCourse in 1..60) "Day $dayOfCourse of 60" else if (dayOfCourse < 1) "Not started" else "Course Complete",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (dayOfCourse in 1..60) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun TodayProgressCard(adherence: com.shumail.healthwidget.data.TodayAdherence) {
    val animatedProgress by animateFloatAsState(
        targetValue = adherence.progressFraction,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "progress_anim"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Daily Regimen Progress",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (adherence.completedDoses == adherence.totalDoses && adherence.totalDoses > 0)
                            "All scheduled doses completed! ✨"
                        else
                            "${adherence.totalDoses - adherence.completedDoses} doses remaining today",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = if (adherence.progressFraction >= 1f) SuccessGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = CircleShape
                ) {
                    Text(
                        text = adherence.percentString,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (adherence.progressFraction >= 1f) SuccessGreen else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = if (adherence.progressFraction >= 1f) SuccessGreen else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun ActiveTimerHeroCard(
    timer: ActiveTimerState,
    now: Long,
    onMarkAquawellTaken: () -> Unit,
    onCancelTimer: () -> Unit
) {
    val effective = timer.getEffectiveStatus(now)
    val remainingFormatted = timer.formattedRemaining(now)
    val progress = timer.progressFraction(now)

    val isRunning = effective == ActiveTimerStatus.RUNNING

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_timer_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) Color(0xFF0F172A) else Color(0xFF0C2A38)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Timer else Icons.Default.WaterDrop,
                        contentDescription = null,
                        tint = if (isRunning) AquawellPrimary else SuccessGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRunning) "10-MINUTE DROP INTERVAL" else "INTERVAL COMPLETE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isRunning) Color(0xFF38BDF8) else SuccessGreen
                    )
                }

                Surface(
                    color = Color(0xFF1E293B),
                    shape = CircleShape
                ) {
                    Text(
                        text = if (isRunning) "Wait 10m" else "Ready",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE2E8F0),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (isRunning) {
                // Circular countdown
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(160.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 8.dp,
                        color = AquawellPrimary,
                        trackColor = Color(0xFF334155)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = remainingFormatted,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "before AQUAWELL",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Text(
                    text = "Allow KETOGATE eye drops to fully absorb into your eye before administering AQUAWELL.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancelTimer,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel", color = Color(0xFF94A3B8))
                    }
                    Button(
                        onClick = onMarkAquawellTaken,
                        modifier = Modifier.weight(1.4f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AquawellPrimary
                        )
                    ) {
                        Text("Take Early")
                    }
                }
            } else {
                // FINISHED_AQUAWELL_DUE
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "💧 AQUAWELL is Due Now!",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "The 10-minute absorption countdown has completed. Apply your AQUAWELL eye drops now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                Button(
                    onClick = onMarkAquawellTaken,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("mark_aquawell_hero_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AquawellPrimary
                    )
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mark AQUAWELL Taken",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun NextDoseCard(
    nextDose: DoseItem,
    onMarkTaken: () -> Unit
) {
    val def = remember(nextDose.medicationId) { MedicationCatalog.get(nextDose.medicationId) }
    val accentColor = remember(nextDose.medicationId) {
        when (nextDose.medicationId) {
            MedicationId.MULMIN -> MulminPrimary
            MedicationId.KETOGATE -> KetogatePrimary
            MedicationId.AQUAWELL -> AquawellPrimary
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("next_dose_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = accentColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "NEXT DUE DOSE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = nextDose.timeFormatted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = def.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    Text(
                        text = nextDose.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onMarkTaken,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor
                    ),
                    modifier = Modifier.testTag("mark_next_dose_button")
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Mark Taken")
                }
            }
        }
    }
}

@Composable
private fun DoseScheduleCard(
    dose: DoseItem,
    onToggle: () -> Unit
) {
    val def = remember(dose.medicationId) { MedicationCatalog.get(dose.medicationId) }
    val accentColor = remember(dose.medicationId) {
        when (dose.medicationId) {
            MedicationId.MULMIN -> MulminPrimary
            MedicationId.KETOGATE -> KetogatePrimary
            MedicationId.AQUAWELL -> AquawellPrimary
        }
    }

    val cardColor by animateColorAsState(
        targetValue = if (dose.isTaken)
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else
            MaterialTheme.colorScheme.surface,
        label = "card_color"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dose_card_${dose.medicationId.name}_${dose.doseIndex}")
            .clickable { onToggle() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (dose.isTaken) 0.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkmark button with bouncy click
            IconButton(
                onClick = onToggle,
                modifier = Modifier
                    .size(42.dp)
                    .testTag("check_btn_${dose.medicationId.name}_${dose.doseIndex}")
            ) {
                if (dose.isTaken) {
                    Surface(
                        shape = CircleShape,
                        color = SuccessGreen,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Taken",
                            tint = Color.White,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(2.dp, accentColor),
                        modifier = Modifier.size(32.dp)
                    ) {}
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = def.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (dose.isTaken) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else accentColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = accentColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (def.type == MedicationType.TABLET) "Tablet" else "Eye Drop",
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = dose.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dose.isTaken) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (dose.isTaken && dose.takenTimeFormatted != null) {
                    Text(
                        text = "✓ Taken at ${dose.takenTimeFormatted}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SuccessGreen
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = dose.timeFormatted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (dose.isTaken) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                )
                if (def.hasNotifications) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Reminder Active",
                        tint = if (dose.isTaken) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(
                        text = "Checkmark only",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionBanner(onRequest: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NotificationsOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Notifications disabled",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Enable to get KETOGATE & AQUAWELL alerts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }
            }
            Button(
                onClick = onRequest,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Allow", fontSize = 12.sp)
            }
        }
    }
}
