package com.shumail.healthwidget.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shumail.healthwidget.data.CourseStats
import com.shumail.healthwidget.data.DaySummary
import com.shumail.healthwidget.data.MedicationRepository
import com.shumail.healthwidget.model.MedicationCatalog
import com.shumail.healthwidget.model.MedicationId
import com.shumail.healthwidget.ui.theme.AquawellPrimary
import com.shumail.healthwidget.ui.theme.KetogatePrimary
import com.shumail.healthwidget.ui.theme.MulminPrimary
import com.shumail.healthwidget.ui.theme.SuccessGreen
import com.shumail.healthwidget.ui.theme.WarmGold
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ProgressScreen(
    repository: MedicationRepository,
    modifier: Modifier = Modifier
) {
    val stats = remember(repository.refreshTrigger.value) { repository.getCourseOverallStats() }
    val today = remember { LocalDate.now() }
    val courseStartDate = remember(repository.refreshTrigger.value) { repository.getCourseStartDate() }

    // Last 7 days summary
    val past7Days = remember(repository.refreshTrigger.value) {
        (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            repository.getDaySummary(date)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("progress_screen"),
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
                Text(
                    text = "Regimen Adherence",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Hero Adherence Metric Card
                AdherenceHeroCard(stats = stats)

                // Past 7 Days Adherence Chart
                Past7DaysChartCard(past7Days = past7Days)

                // Medication-Specific Progress Cards
                Text(
                    text = "Medication Breakdown",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                MedicationProgressCard(
                    name = "MULMIN",
                    subtitle = "Tablet • 1/day • 30 days",
                    taken = stats.mulminTaken,
                    target = 30 * 1,
                    accentColor = MulminPrimary,
                    icon = Icons.Default.Medication
                )

                MedicationProgressCard(
                    name = "KETOGATE",
                    subtitle = "Eye Drop • 3/day • 14 days",
                    taken = stats.ketogateTaken,
                    target = 14 * 3,
                    accentColor = KetogatePrimary,
                    icon = Icons.Default.WaterDrop
                )

                MedicationProgressCard(
                    name = "AQUAWELL",
                    subtitle = "Eye Drop • 4/day • 60 days",
                    taken = stats.aquawellTaken,
                    target = 60 * 4,
                    accentColor = AquawellPrimary,
                    icon = Icons.Default.WaterDrop
                )

                // Course Milestones
                MilestonesCard(currentDay = stats.currentDayOfCourse)

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AdherenceHeroCard(stats: CourseStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (stats.currentDayOfCourse in 1..60) "DAY ${stats.currentDayOfCourse} OF 60" else "COURSE COMPLETED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = "${stats.totalDosesTaken} Doses Taken",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val missed = (stats.totalDosesScheduledToDate - stats.totalDosesTaken).coerceAtLeast(0)
                Text(
                    text = "$missed doses missed to date\nTarget: ${stats.totalCourseDoses} total course doses",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(110.dp)
            ) {
                CircularProgressIndicator(
                    progress = { stats.overallAdherenceFraction },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 10.dp,
                    color = if (stats.overallAdherenceFraction >= 0.85f) SuccessGreen else WarmGold,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stats.overallAdherencePercent,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Adherence",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun Past7DaysChartCard(past7Days: List<DaySummary>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Past 7 Days Adherence",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Daily completion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.Bottom
            ) {
                past7Days.forEach { daySummary ->
                    val fraction = if (daySummary.totalScheduled > 0) {
                        (daySummary.totalCompleted.toFloat() / daySummary.totalScheduled.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    val dayName = daySummary.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.US)

                    val barColor = when {
                        daySummary.isFullyCompleted -> SuccessGreen
                        daySummary.isPartiallyCompleted -> WarmGold
                        daySummary.isMissed -> Color(0xFFE11D48)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Ratio text
                        Text(
                            text = if (daySummary.totalScheduled > 0) "${daySummary.totalCompleted}/${daySummary.totalScheduled}" else "-",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Vertical bar container
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height(70.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((70 * fraction).dp.coerceAtLeast(if (fraction > 0f) 6.dp else 0.dp))
                                    .background(barColor)
                            )
                        }

                        // Day label
                        Text(
                            text = dayName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (daySummary.isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (daySummary.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MedicationProgressCard(
    name: String,
    subtitle: String,
    taken: Int,
    target: Int,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val fraction = if (target > 0) (taken.toFloat() / target.toFloat()).coerceIn(0f, 1f) else 0f

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = accentColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "$taken / $target",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun MilestonesCard(currentDay: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Regimen Milestones",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            MilestoneRow(
                dayNumber = 14,
                title = "KETOGATE Eye Drops End",
                desc = "14 days completed. 10-minute interval timer turns off.",
                isReached = currentDay > 14
            )

            MilestoneRow(
                dayNumber = 30,
                title = "MULMIN Tablets End",
                desc = "30-day oral tablet course completed.",
                isReached = currentDay > 30
            )

            MilestoneRow(
                dayNumber = 60,
                title = "AQUAWELL Eye Drops End",
                desc = "Full 60-day eye treatment completed.",
                isReached = currentDay > 60
            )
        }
    }
}

@Composable
private fun MilestoneRow(
    dayNumber: Int,
    title: String,
    desc: String,
    isReached: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = CircleShape,
            color = if (isReached) SuccessGreen else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(26.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isReached) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(
                        text = "$dayNumber",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isReached) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
