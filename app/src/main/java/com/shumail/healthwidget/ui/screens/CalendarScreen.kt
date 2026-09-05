package com.shumail.healthwidget.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shumail.healthwidget.data.DaySummary
import com.shumail.healthwidget.data.MedicationRepository
import com.shumail.healthwidget.model.DoseItem
import com.shumail.healthwidget.model.MedicationCatalog
import com.shumail.healthwidget.model.MedicationDef
import com.shumail.healthwidget.model.MedicationId
import com.shumail.healthwidget.ui.theme.AquawellPrimary
import com.shumail.healthwidget.ui.theme.DangerRed
import com.shumail.healthwidget.ui.theme.KetogatePrimary
import com.shumail.healthwidget.ui.theme.MulminPrimary
import com.shumail.healthwidget.ui.theme.SuccessGreen
import com.shumail.healthwidget.ui.theme.WarmGold
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarScreen(
    repository: MedicationRepository,
    modifier: Modifier = Modifier
) {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    val today = remember { LocalDate.now() }
    val courseStartDate = remember(repository.refreshTrigger.value) { repository.getCourseStartDate() }
    val dayOfCourse = remember(selectedDate, courseStartDate) {
        MedicationCatalog.calculateDayOfCourse(courseStartDate, selectedDate)
    }

    val selectedDoses = remember(selectedDate, repository.refreshTrigger.value) {
        repository.getDosesForDate(selectedDate)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("calendar_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header & Month Navigator
                MonthHeader(
                    currentMonth = currentMonth,
                    onPrevMonth = { currentMonth = currentMonth.minusMonths(1) },
                    onNextMonth = { currentMonth = currentMonth.plusMonths(1) }
                )

                // Calendar Grid Card
                CalendarGridCard(
                    month = currentMonth,
                    selectedDate = selectedDate,
                    today = today,
                    repository = repository,
                    onDateSelected = { selectedDate = it }
                )

                // Legend
                CalendarLegend()

                // Selected Date Detail Section
                SelectedDateSummaryHeader(
                    selectedDate = selectedDate,
                    dayOfCourse = dayOfCourse,
                    totalDoses = selectedDoses.size,
                    completedDoses = selectedDoses.count { it.isTaken }
                )

                // Course status breakdown for this date (Shows expiration notices!)
                CourseStatusBreakdownCard(dayOfCourse = dayOfCourse)
            }
        }

        // Scheduled Doses for Selected Date
        if (selectedDoses.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Scheduled Doses on this Day",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            items(selectedDoses, key = { it.doseKey }) { dose ->
                Box(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    CalendarDoseCard(
                        dose = dose,
                        onToggle = {
                            if (dose.isTaken) {
                                repository.markDoseUntaken(dose.medicationId, dose.doseIndex, dose.date)
                            } else {
                                repository.markDoseTaken(dose.medicationId, dose.doseIndex, dose.date)
                            }
                        }
                    )
                }
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = if (dayOfCourse < 1) "Selected date is before the medication course started." else "Medication course completed before or on this day.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun MonthHeader(
    currentMonth: YearMonth,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Course Adherence Calendar",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row {
            IconButton(onClick = onPrevMonth) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month")
            }
            IconButton(onClick = onNextMonth) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month")
            }
        }
    }
}

@Composable
private fun CalendarGridCard(
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    repository: MedicationRepository,
    onDateSelected: (LocalDate) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Day-of-week labels (Mon - Sun)
            Row(modifier = Modifier.fillMaxWidth()) {
                val dayHeaders = listOf("S", "M", "T", "W", "T", "F", "S")
                for (dh in dayHeaders) {
                    Text(
                        text = dh,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Month days
            val firstOfMonth = month.atDay(1)
            val dayOfWeekValue = firstOfMonth.dayOfWeek.value % 7 // Sunday = 0, Monday = 1, ...
            val daysInMonth = month.lengthOfMonth()

            val totalCells = ((dayOfWeekValue + daysInMonth + 6) / 7) * 7

            for (week in 0 until (totalCells / 7)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (dayCol in 0..6) {
                        val cellIndex = (week * 7) + dayCol
                        val dayNumber = cellIndex - dayOfWeekValue + 1

                        if (dayNumber in 1..daysInMonth) {
                            val date = month.atDay(dayNumber)
                            val summary = repository.getDaySummary(date)
                            val isSelected = date == selectedDate
                            val isToday = date == today

                            DayCell(
                                dayNumber = dayNumber,
                                summary = summary,
                                isSelected = isSelected,
                                isToday = isToday,
                                onClick = { onDateSelected(date) },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    dayNumber: Int,
    summary: DaySummary,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val indicatorColor = when {
        summary.isFullyCompleted -> SuccessGreen
        summary.isPartiallyCompleted -> WarmGold
        summary.isMissed -> DangerRed
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else if (isToday) MaterialTheme.colorScheme.surfaceVariant
                else Color.Transparent
            )
            .border(
                width = if (isToday && !isSelected) 1.5.dp else 0.dp,
                color = if (isToday && !isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = dayNumber.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )

            if (indicatorColor != Color.Transparent) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(indicatorColor)
                )
            } else {
                Spacer(modifier = Modifier.size(5.dp))
            }
        }
    }
}

@Composable
private fun CalendarLegend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(color = SuccessGreen, label = "Completed")
        LegendItem(color = WarmGold, label = "Partial")
        LegendItem(color = DangerRed, label = "Missed")
        LegendItem(color = MaterialTheme.colorScheme.surfaceVariant, label = "Upcoming")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SelectedDateSummaryHeader(
    selectedDate: LocalDate,
    dayOfCourse: Int,
    totalDoses: Int,
    completedDoses: Int
) {
    val formatted = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = formatted,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (dayOfCourse in 1..60) "Day $dayOfCourse of 60" else if (dayOfCourse < 1) "Before course start" else "Course ended",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                color = if (totalDoses > 0 && completedDoses >= totalDoses) SuccessGreen.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "$completedDoses / $totalDoses Taken",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (totalDoses > 0 && completedDoses >= totalDoses) SuccessGreen
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun CourseStatusBreakdownCard(dayOfCourse: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Medication Status on this Date",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            MedStatusRow(
                name = "MULMIN (Tablet)",
                duration = 30,
                dayOfCourse = dayOfCourse,
                accentColor = MulminPrimary
            )

            MedStatusRow(
                name = "KETOGATE (Eye Drop)",
                duration = 14,
                dayOfCourse = dayOfCourse,
                accentColor = KetogatePrimary
            )

            MedStatusRow(
                name = "AQUAWELL (Eye Drop)",
                duration = 60,
                dayOfCourse = dayOfCourse,
                accentColor = AquawellPrimary
            )
        }
    }
}

@Composable
private fun MedStatusRow(
    name: String,
    duration: Int,
    dayOfCourse: Int,
    accentColor: Color
) {
    val isActive = dayOfCourse in 1..duration
    val isExpired = dayOfCourse > duration

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isActive) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }

        Surface(
            color = when {
                isActive -> accentColor.copy(alpha = 0.12f)
                isExpired -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                text = when {
                    isActive -> "Active (Day $dayOfCourse of $duration)"
                    isExpired -> "Expired (Day $duration reached)"
                    else -> "Not started yet"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (isActive) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun CalendarDoseCard(
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(36.dp)
            ) {
                if (dose.isTaken) {
                    Surface(
                        shape = CircleShape,
                        color = SuccessGreen,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Taken",
                            tint = Color.White,
                            modifier = Modifier.padding(5.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, accentColor),
                        modifier = Modifier.size(28.dp)
                    ) {}
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = def.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (dose.isTaken) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else accentColor
                )
                Text(
                    text = dose.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = dose.timeFormatted,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
