package com.example.myapplication

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "IoTApp.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_USERS = "Users"
        private const val COLUMN_ID = "id"
        private const val COLUMN_USERNAME = "username"
        private const val COLUMN_PASSWORD = "password"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createUsersTable = ("CREATE TABLE $TABLE_USERS ("
                + "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "$COLUMN_USERNAME TEXT UNIQUE,"
                + "$COLUMN_PASSWORD TEXT)")
        db.execSQL(createUsersTable)

        // Crea un usuario por defecto al instalar la app
        // La contraseña es "admin123", pero se guarda de forma segura
        insertUser(db, "admin", hashPassword("admin123"))
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        onCreate(db)
    }

    // Aplica el hash SHA-256 a la contraseña
    private fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun insertUser(db: SQLiteDatabase, username: String, hashedPw: String) {
        val values = ContentValues()
        values.put(COLUMN_USERNAME, username)
        values.put(COLUMN_PASSWORD, hashedPw)
        db.insert(TABLE_USERS, null, values)
    }

    // Verifica si el usuario y la contraseña (convertida a hash) coinciden en la base de datos
    fun checkUser(username: String, passwordUnhashed: String): Boolean {
        val db = this.readableDatabase
        val hashedInput = hashPassword(passwordUnhashed)
        val cursor = db.rawQuery("SELECT * FROM $TABLE_USERS WHERE $COLUMN_USERNAME=? AND $COLUMN_PASSWORD=?", arrayOf(username, hashedInput))
        val result = cursor.count > 0
        cursor.close()
        return result
    }
}