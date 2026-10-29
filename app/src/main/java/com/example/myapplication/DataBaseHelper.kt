package com.example.myapplication

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "IoTApp.db"
        private const val DATABASE_VERSION = 2
        private const val TABLE_USERS = "Users"
        private const val COLUMN_ID = "id"
        private const val COLUMN_USERNAME = "username"
        private const val COLUMN_PASSWORD = "password"
        private const val COLUMN_ROLE = "role"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createUsersTable = ("CREATE TABLE $TABLE_USERS ("
                + "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "$COLUMN_USERNAME TEXT UNIQUE,"
                + "$COLUMN_PASSWORD TEXT,"
                + "$COLUMN_ROLE TEXT)")
        db.execSQL(createUsersTable)

        // Credenciales base de prueba
        insertUser(db, "admin", hashPassword("admin123"), "admin")
        insertUser(db, "padre", hashPassword("padre123"), "tutor")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        onCreate(db)
    }

    fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun insertUser(db: SQLiteDatabase, username: String, hashedPw: String, role: String): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_USERNAME, username)
            put(COLUMN_PASSWORD, hashedPw)
            put(COLUMN_ROLE, role)
        }
        val result = db.insert(TABLE_USERS, null, values)
        return result != -1L
    }

    fun registerUser(username: String, passwordUnhashed: String, role: String = "tutor"): Boolean {
        val db = this.writableDatabase
        val hashedPw = hashPassword(passwordUnhashed)
        return insertUser(db, username, hashedPw, role)
    }

    fun authenticateUser(username: String, passwordUnhashed: String): String? {
        val db = this.readableDatabase
        val hashedInput = hashPassword(passwordUnhashed)
        val cursor = db.rawQuery(
            "SELECT $COLUMN_ROLE FROM $TABLE_USERS WHERE $COLUMN_USERNAME=? AND $COLUMN_PASSWORD=?",
            arrayOf(username, hashedInput)
        )

        var role: String? = null
        if (cursor.moveToFirst()) {
            role = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ROLE))
        }
        cursor.close()
        return role
    }
}