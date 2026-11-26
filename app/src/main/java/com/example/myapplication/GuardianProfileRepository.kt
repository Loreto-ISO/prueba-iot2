package com.example.myapplication

import android.content.Context
import android.util.Log
import com.google.firebase.database.FirebaseDatabase

class GuardianProfileRepository(private val database: FirebaseDatabase = FirebaseDatabase.getInstance()) {
    data class Profile(val role: String, val name: String, val homeName: String)

    fun loadOrCreate(uid: String, name: String, email: String, callback: (Profile?, Exception?) -> Unit) {
        if (uid.isBlank()) { callback(null, IllegalArgumentException("Identificador de usuario vacío")); return }
        val ref = database.reference.child("users").child(uid)
        ref.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                val profileName = name.trim()
                val values = mapOf(
                    "nombre" to profileName,
                    "email" to email,
                    "rol" to GuardianSessionRepository.ROLE_TUTOR,
                    "hogarId" to "casa-01"
                )
                ref.setValue(values)
                    .addOnSuccessListener { callback(Profile(GuardianSessionRepository.ROLE_TUTOR, profileName, "Casa 01"), null) }
                    .addOnFailureListener { error -> logAndReturn(callback, error) }
                return@addOnSuccessListener
            }
            val rawRole = snapshot.child("rol").getValue(String::class.java)?.trim()?.lowercase()
            if (rawRole != GuardianSessionRepository.ROLE_TUTOR && rawRole != GuardianSessionRepository.ROLE_ADMIN) {
                callback(null, IllegalStateException("El perfil no contiene un rol válido")); return@addOnSuccessListener
            }
            val profileName = snapshot.child("nombre").getValue(String::class.java)?.trim().orEmpty().ifBlank { name.trim() }
            val homeId = snapshot.child("hogarId").getValue(String::class.java)?.trim().orEmpty()
            val homeName = when (homeId.lowercase()) {
                "casa-01", "casa 01", "" -> "Casa 01"
                else -> homeId
            }
            callback(Profile(rawRole, profileName, homeName), null)
        }.addOnFailureListener { error -> logAndReturn(callback, error) }
    }

    private fun logAndReturn(callback: (Profile?, Exception?) -> Unit, error: Exception) {
        Log.e(LOG_TAG, "Could not load or create user profile", error)
        callback(null, error)
    }

    companion object {
        private const val LOG_TAG = "GuardianApp"
        fun selectedDoor(context: Context) = context.getSharedPreferences("guardian_preferences", Context.MODE_PRIVATE)
            .getString("selectedDoorId", GuardianSessionRepository.DOOR_ENTRY)
            ?.takeIf { id -> GuardianSessionRepository.doors.any { it.id == id } }
            ?: GuardianSessionRepository.DOOR_ENTRY
    }
}
