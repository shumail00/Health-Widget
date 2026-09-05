package com.shumail.healthwidget.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class MedicationDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "eye_medication.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_DOSES = "dose_records"
        const val COL_DATE = "date"
        const val COL_MEDICATION = "medication"
        const val COL_DOSE_INDEX = "dose_index"
        const val COL_TAKEN_TIME = "taken_time"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_DOSES (
                $COL_DATE TEXT NOT NULL,
                $COL_MEDICATION TEXT NOT NULL,
                $COL_DOSE_INDEX INTEGER NOT NULL,
                $COL_TAKEN_TIME INTEGER NOT NULL,
                PRIMARY KEY ($COL_DATE, $COL_MEDICATION, $COL_DOSE_INDEX)
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_DOSES")
        onCreate(db)
    }

    fun markDoseTaken(date: String, medication: String, doseIndex: Int, takenTime: Long) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_DATE, date)
            put(COL_MEDICATION, medication)
            put(COL_DOSE_INDEX, doseIndex)
            put(COL_TAKEN_TIME, takenTime)
        }
        db.insertWithOnConflict(TABLE_DOSES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun markDoseUntaken(date: String, medication: String, doseIndex: Int) {
        val db = writableDatabase
        db.delete(
            TABLE_DOSES,
            "$COL_DATE = ? AND $COL_MEDICATION = ? AND $COL_DOSE_INDEX = ?",
            arrayOf(date, medication, doseIndex.toString())
        )
    }

    fun getTakenDosesForDate(date: String): Map<Pair<String, Int>, Long> {
        val db = readableDatabase
        val result = mutableMapOf<Pair<String, Int>, Long>()
        val cursor = db.query(
            TABLE_DOSES,
            arrayOf(COL_MEDICATION, COL_DOSE_INDEX, COL_TAKEN_TIME),
            "$COL_DATE = ?",
            arrayOf(date),
            null,
            null,
            null
        )
        cursor.use {
            val medIdx = it.getColumnIndexOrThrow(COL_MEDICATION)
            val doseIdx = it.getColumnIndexOrThrow(COL_DOSE_INDEX)
            val timeIdx = it.getColumnIndexOrThrow(COL_TAKEN_TIME)
            while (it.moveToNext()) {
                val med = it.getString(medIdx)
                val idx = it.getInt(doseIdx)
                val time = it.getLong(timeIdx)
                result[Pair(med, idx)] = time
            }
        }
        return result
    }

    fun getAllTakenCountsByDate(): Map<String, Int> {
        val db = readableDatabase
        val map = mutableMapOf<String, Int>()
        val cursor = db.rawQuery(
            "SELECT $COL_DATE, COUNT(*) FROM $TABLE_DOSES GROUP BY $COL_DATE",
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                val date = it.getString(0)
                val count = it.getInt(1)
                map[date] = count
            }
        }
        return map
    }

    fun getTotalTakenCountForMedication(medication: String): Int {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_DOSES WHERE $COL_MEDICATION = ?",
            arrayOf(medication)
        )
        return cursor.use {
            if (it.moveToNext()) it.getInt(0) else 0
        }
    }

    fun getTotalTakenCountAll(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_DOSES", null)
        return cursor.use {
            if (it.moveToNext()) it.getInt(0) else 0
        }
    }

    fun clearDate(date: String) {
        val db = writableDatabase
        db.delete(TABLE_DOSES, "$COL_DATE = ?", arrayOf(date))
    }

    fun clearAll() {
        val db = writableDatabase
        db.delete(TABLE_DOSES, null, null)
    }
}
