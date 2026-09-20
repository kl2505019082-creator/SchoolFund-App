package com.uptm.schoolfund.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.uptm.schoolfund.model.EventBudget
import com.uptm.schoolfund.model.Tabung
import com.uptm.schoolfund.model.TransactionItem

/**
 * SQLite database helper for the School Fund and Event Budgeting Mobile Application.
 * All data is stored fully offline on the device, as required by FR/NFR in the FYP report.
 */
class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_EVENTS (
                $COL_EVENT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_EVENT_NAME TEXT NOT NULL,
                $COL_EVENT_BUDGET REAL NOT NULL,
                $COL_EVENT_DATE TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_TABUNG (
                $COL_TABUNG_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TABUNG_NAME TEXT NOT NULL,
                $COL_TABUNG_SUBTITLE TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_TRANSACTIONS (
                $COL_TX_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TX_TYPE TEXT NOT NULL,
                $COL_TX_AMOUNT REAL NOT NULL,
                $COL_TX_DESCRIPTION TEXT,
                $COL_TX_DATE TEXT NOT NULL,
                $COL_TX_RECEIPT_PATH TEXT,
                $COL_TX_EVENT_ID INTEGER,
                $COL_TX_TABUNG_ID INTEGER,
                FOREIGN KEY($COL_TX_EVENT_ID) REFERENCES $TABLE_EVENTS($COL_EVENT_ID),
                FOREIGN KEY($COL_TX_TABUNG_ID) REFERENCES $TABLE_TABUNG($COL_TABUNG_ID)
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migrate in place rather than dropping tables, so existing recorded transactions
        // and event budgets survive the app update instead of being wiped.
        if (oldVersion < 2) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_TABUNG (
                    $COL_TABUNG_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_TABUNG_NAME TEXT NOT NULL,
                    $COL_TABUNG_SUBTITLE TEXT
                )
                """.trimIndent()
            )
            db.execSQL("ALTER TABLE $TABLE_TRANSACTIONS ADD COLUMN $COL_TX_TABUNG_ID INTEGER")
        }
    }

    // ---------- Transactions ----------

    fun insertTransaction(tx: TransactionItem): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TX_TYPE, tx.type)
            put(COL_TX_AMOUNT, tx.amount)
            put(COL_TX_DESCRIPTION, tx.description)
            put(COL_TX_DATE, tx.date)
            put(COL_TX_RECEIPT_PATH, tx.receiptPath)
            if (tx.eventId != null) put(COL_TX_EVENT_ID, tx.eventId) else putNull(COL_TX_EVENT_ID)
            if (tx.tabungId != null) put(COL_TX_TABUNG_ID, tx.tabungId) else putNull(COL_TX_TABUNG_ID)
        }
        return db.insert(TABLE_TRANSACTIONS, null, values)
    }

    fun deleteTransaction(id: Long) {
        writableDatabase.delete(TABLE_TRANSACTIONS, "$COL_TX_ID = ?", arrayOf(id.toString()))
    }

    fun getAllTransactions(): List<TransactionItem> {
        val list = mutableListOf<TransactionItem>()
        val db = readableDatabase
        val cursor: Cursor = db.rawQuery(
            "SELECT * FROM $TABLE_TRANSACTIONS ORDER BY $COL_TX_DATE DESC, $COL_TX_ID DESC", null
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToTransaction(it))
            }
        }
        return list
    }

    fun getTransactionsForEvent(eventId: Long): List<TransactionItem> {
        val list = mutableListOf<TransactionItem>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_TRANSACTIONS WHERE $COL_TX_EVENT_ID = ? ORDER BY $COL_TX_DATE DESC",
            arrayOf(eventId.toString())
        )
        cursor.use {
            while (it.moveToNext()) list.add(cursorToTransaction(it))
        }
        return list
    }

    fun getTransactionsForTabung(tabungId: Long): List<TransactionItem> {
        val list = mutableListOf<TransactionItem>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_TRANSACTIONS WHERE $COL_TX_TABUNG_ID = ? ORDER BY $COL_TX_DATE DESC",
            arrayOf(tabungId.toString())
        )
        cursor.use {
            while (it.moveToNext()) list.add(cursorToTransaction(it))
        }
        return list
    }

    /** General class fund balance = income - expense, excluding transactions linked to an event or a Tabung. */
    fun getClassFundBalance(): Double {
        val db = readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT
              COALESCE(SUM(CASE WHEN $COL_TX_TYPE = 'income' THEN $COL_TX_AMOUNT ELSE 0 END), 0) -
              COALESCE(SUM(CASE WHEN $COL_TX_TYPE = 'expense' THEN $COL_TX_AMOUNT ELSE 0 END), 0)
            FROM $TABLE_TRANSACTIONS
            WHERE $COL_TX_EVENT_ID IS NULL AND $COL_TX_TABUNG_ID IS NULL
            """.trimIndent(), null
        )
        var balance = 0.0
        cursor.use {
            if (it.moveToFirst()) balance = it.getDouble(0)
        }
        return balance
    }

    /** Running balance (income - expense) for one Tabung. */
    fun getTabungBalance(tabungId: Long): Double {
        val db = readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT
              COALESCE(SUM(CASE WHEN $COL_TX_TYPE = 'income' THEN $COL_TX_AMOUNT ELSE 0 END), 0) -
              COALESCE(SUM(CASE WHEN $COL_TX_TYPE = 'expense' THEN $COL_TX_AMOUNT ELSE 0 END), 0)
            FROM $TABLE_TRANSACTIONS
            WHERE $COL_TX_TABUNG_ID = ?
            """.trimIndent(), arrayOf(tabungId.toString())
        )
        var balance = 0.0
        cursor.use { if (it.moveToFirst()) balance = it.getDouble(0) }
        return balance
    }

    private fun cursorToTransaction(c: Cursor): TransactionItem {
        return TransactionItem(
            id = c.getLong(c.getColumnIndexOrThrow(COL_TX_ID)),
            type = c.getString(c.getColumnIndexOrThrow(COL_TX_TYPE)),
            amount = c.getDouble(c.getColumnIndexOrThrow(COL_TX_AMOUNT)),
            description = c.getString(c.getColumnIndexOrThrow(COL_TX_DESCRIPTION)) ?: "",
            date = c.getString(c.getColumnIndexOrThrow(COL_TX_DATE)),
            receiptPath = c.getString(c.getColumnIndexOrThrow(COL_TX_RECEIPT_PATH)),
            eventId = if (c.isNull(c.getColumnIndexOrThrow(COL_TX_EVENT_ID))) null
                      else c.getLong(c.getColumnIndexOrThrow(COL_TX_EVENT_ID)),
            tabungId = if (c.isNull(c.getColumnIndexOrThrow(COL_TX_TABUNG_ID))) null
                       else c.getLong(c.getColumnIndexOrThrow(COL_TX_TABUNG_ID))
        )
    }

    // ---------- Event budgets ----------

    fun insertEvent(event: EventBudget): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_EVENT_NAME, event.name)
            put(COL_EVENT_BUDGET, event.budget)
            put(COL_EVENT_DATE, event.date)
        }
        return db.insert(TABLE_EVENTS, null, values)
    }

    fun getAllEvents(): List<EventBudget> {
        val list = mutableListOf<EventBudget>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_EVENTS ORDER BY $COL_EVENT_DATE DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    EventBudget(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_EVENT_ID)),
                        name = it.getString(it.getColumnIndexOrThrow(COL_EVENT_NAME)),
                        budget = it.getDouble(it.getColumnIndexOrThrow(COL_EVENT_BUDGET)),
                        date = it.getString(it.getColumnIndexOrThrow(COL_EVENT_DATE))
                    )
                )
            }
        }
        return list
    }

    /** Deletes an event and every transaction recorded under it. */
    fun deleteEvent(id: Long) {
        val db = writableDatabase
        db.delete(TABLE_TRANSACTIONS, "$COL_TX_EVENT_ID = ?", arrayOf(id.toString()))
        db.delete(TABLE_EVENTS, "$COL_EVENT_ID = ?", arrayOf(id.toString()))
    }

    fun getEventSpent(eventId: Long): Double {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT COALESCE(SUM($COL_TX_AMOUNT),0) FROM $TABLE_TRANSACTIONS WHERE $COL_TX_EVENT_ID = ? AND $COL_TX_TYPE = 'expense'",
            arrayOf(eventId.toString())
        )
        var spent = 0.0
        cursor.use { if (it.moveToFirst()) spent = it.getDouble(0) }
        return spent
    }

    /** Running balance (income - expense) for one event — the teacher records both income
     *  and expense against an event directly, there is no separate pre-set budget cap. */
    fun getEventBalance(eventId: Long): Double {
        val db = readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT
              COALESCE(SUM(CASE WHEN $COL_TX_TYPE = 'income' THEN $COL_TX_AMOUNT ELSE 0 END), 0) -
              COALESCE(SUM(CASE WHEN $COL_TX_TYPE = 'expense' THEN $COL_TX_AMOUNT ELSE 0 END), 0)
            FROM $TABLE_TRANSACTIONS
            WHERE $COL_TX_EVENT_ID = ?
            """.trimIndent(), arrayOf(eventId.toString())
        )
        var balance = 0.0
        cursor.use { if (it.moveToFirst()) balance = it.getDouble(0) }
        return balance
    }

    fun getEventIncome(eventId: Long): Double {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT COALESCE(SUM($COL_TX_AMOUNT),0) FROM $TABLE_TRANSACTIONS WHERE $COL_TX_EVENT_ID = ? AND $COL_TX_TYPE = 'income'",
            arrayOf(eventId.toString())
        )
        var income = 0.0
        cursor.use { if (it.moveToFirst()) income = it.getDouble(0) }
        return income
    }

    // ---------- Tabung (teacher-created named funds) ----------

    fun insertTabung(tabung: Tabung): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TABUNG_NAME, tabung.name)
            put(COL_TABUNG_SUBTITLE, tabung.subtitle)
        }
        return db.insert(TABLE_TABUNG, null, values)
    }

    fun getAllTabung(): List<Tabung> {
        val list = mutableListOf<Tabung>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_TABUNG ORDER BY $COL_TABUNG_ID ASC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    Tabung(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_TABUNG_ID)),
                        name = it.getString(it.getColumnIndexOrThrow(COL_TABUNG_NAME)),
                        subtitle = it.getString(it.getColumnIndexOrThrow(COL_TABUNG_SUBTITLE)) ?: ""
                    )
                )
            }
        }
        return list
    }

    /** Deletes a Tabung and every transaction recorded under it. */
    fun deleteTabung(id: Long) {
        val db = writableDatabase
        db.delete(TABLE_TRANSACTIONS, "$COL_TX_TABUNG_ID = ?", arrayOf(id.toString()))
        db.delete(TABLE_TABUNG, "$COL_TABUNG_ID = ?", arrayOf(id.toString()))
    }

    companion object {
        private const val DATABASE_NAME = "school_fund.db"
        private const val DATABASE_VERSION = 2

        /** Path to the raw SQLite file on disk — used by BackupActivity to export/restore. */
        fun databaseFile(context: Context) = context.getDatabasePath(DATABASE_NAME)

        const val TABLE_TRANSACTIONS = "transactions"
        const val COL_TX_ID = "id"
        const val COL_TX_TYPE = "type" // "income" or "expense"
        const val COL_TX_AMOUNT = "amount"
        const val COL_TX_DESCRIPTION = "description"
        const val COL_TX_DATE = "date"
        const val COL_TX_RECEIPT_PATH = "receipt_path"
        const val COL_TX_EVENT_ID = "event_id"
        const val COL_TX_TABUNG_ID = "tabung_id"

        const val TABLE_EVENTS = "events"
        const val COL_EVENT_ID = "id"
        const val COL_EVENT_NAME = "name"
        const val COL_EVENT_BUDGET = "budget"
        const val COL_EVENT_DATE = "date"

        const val TABLE_TABUNG = "tabung"
        const val COL_TABUNG_ID = "id"
        const val COL_TABUNG_NAME = "name"
        const val COL_TABUNG_SUBTITLE = "subtitle"
    }
}
