package com.shumail.healthwidget

import com.shumail.healthwidget.model.ActiveTimerState
import com.shumail.healthwidget.model.ActiveTimerStatus
import com.shumail.healthwidget.model.MedicationCatalog
import com.shumail.healthwidget.model.MedicationId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MedicationCatalogTest {

    @Test
    fun testMedicationCatalogDurationsAndDoses() {
        val mulmin = MedicationCatalog.get(MedicationId.MULMIN)
        assertEquals(30, mulmin.durationDays)
        assertEquals(1, mulmin.dailyDoseCount)
        assertFalse(mulmin.hasNotifications)

        val ketogate = MedicationCatalog.get(MedicationId.KETOGATE)
        assertEquals(14, ketogate.durationDays)
        assertEquals(3, ketogate.dailyDoseCount)
        assertTrue(ketogate.hasNotifications)

        val aquawell = MedicationCatalog.get(MedicationId.AQUAWELL)
        assertEquals(60, aquawell.durationDays)
        assertEquals(4, aquawell.dailyDoseCount)
        assertTrue(aquawell.hasNotifications)
    }

    @Test
    fun testDayOfCourseCalculations() {
        val start = LocalDate.of(2026, 9, 1)
        val day1 = LocalDate.of(2026, 9, 1)
        val day14 = LocalDate.of(2026, 9, 14)
        val day15 = LocalDate.of(2026, 9, 15)
        val day30 = LocalDate.of(2026, 9, 30)
        val day60 = LocalDate.of(2026, 10, 30)

        assertEquals(1, MedicationCatalog.calculateDayOfCourse(start, day1))
        assertEquals(14, MedicationCatalog.calculateDayOfCourse(start, day14))
        assertEquals(15, MedicationCatalog.calculateDayOfCourse(start, day15))
        assertEquals(30, MedicationCatalog.calculateDayOfCourse(start, day30))
        assertEquals(60, MedicationCatalog.calculateDayOfCourse(start, day60))
    }

    @Test
    fun testExpirationRules() {
        // Day 1: all three active
        assertTrue(MedicationCatalog.isMedicationActive(MedicationId.KETOGATE, 1))
        assertTrue(MedicationCatalog.isMedicationActive(MedicationId.MULMIN, 1))
        assertTrue(MedicationCatalog.isMedicationActive(MedicationId.AQUAWELL, 1))

        // Day 14: all three active
        assertTrue(MedicationCatalog.isMedicationActive(MedicationId.KETOGATE, 14))
        assertTrue(MedicationCatalog.isMedicationActive(MedicationId.MULMIN, 14))
        assertTrue(MedicationCatalog.isMedicationActive(MedicationId.AQUAWELL, 14))

        // Day 15: KETOGATE expired! Mulmin & Aquawell active
        assertFalse(MedicationCatalog.isMedicationActive(MedicationId.KETOGATE, 15))
        assertTrue(MedicationCatalog.isMedicationActive(MedicationId.MULMIN, 15))
        assertTrue(MedicationCatalog.isMedicationActive(MedicationId.AQUAWELL, 15))

        // Day 30: MULMIN active, KETOGATE expired, AQUAWELL active
        assertFalse(MedicationCatalog.isMedicationActive(MedicationId.KETOGATE, 30))
        assertTrue(MedicationCatalog.isMedicationActive(MedicationId.MULMIN, 30))
        assertTrue(MedicationCatalog.isMedicationActive(MedicationId.AQUAWELL, 30))

        // Day 31: MULMIN expired! KETOGATE expired! Only AQUAWELL active
        assertFalse(MedicationCatalog.isMedicationActive(MedicationId.KETOGATE, 31))
        assertFalse(MedicationCatalog.isMedicationActive(MedicationId.MULMIN, 31))
        assertTrue(MedicationCatalog.isMedicationActive(MedicationId.AQUAWELL, 31))

        // Day 60: AQUAWELL active
        assertTrue(MedicationCatalog.isMedicationActive(MedicationId.AQUAWELL, 60))

        // Day 61: all expired
        assertFalse(MedicationCatalog.isMedicationActive(MedicationId.KETOGATE, 61))
        assertFalse(MedicationCatalog.isMedicationActive(MedicationId.MULMIN, 61))
        assertFalse(MedicationCatalog.isMedicationActive(MedicationId.AQUAWELL, 61))
    }

    @Test
    fun testActiveTimerTransitions() {
        val start = 1_000_000L
        val duration = 600_000L // 10m
        val finish = start + duration

        val timer = ActiveTimerState(
            status = ActiveTimerStatus.RUNNING,
            aquawellDoseIndex = 0,
            ketogateDoseIndex = 0,
            startTimeMillis = start,
            finishTimeMillis = finish,
            durationMillis = duration
        )

        // Halfway
        val mid = start + 300_000L
        assertEquals(ActiveTimerStatus.RUNNING, timer.getEffectiveStatus(mid))
        assertEquals("05:00", timer.formattedRemaining(mid))
        assertEquals(0.5f, timer.progressFraction(mid), 0.01f)

        // After 10 mins finish
        val after = finish + 10_000L
        assertEquals(ActiveTimerStatus.FINISHED_AQUAWELL_DUE, timer.getEffectiveStatus(after))
        assertEquals(0L, timer.remainingMillis(after))
        assertEquals("00:00", timer.formattedRemaining(after))
        assertEquals(1.0f, timer.progressFraction(after), 0.001f)
    }
}
