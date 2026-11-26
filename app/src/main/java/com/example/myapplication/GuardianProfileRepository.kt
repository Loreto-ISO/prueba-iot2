package com.example.myapplication

import android.content.Context
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class GuardianProfileRepository(private val database: FirebaseDatabase = FirebaseDatabase.getInstance()) {
    fun loadOrCreate(uid: String, name: String, email: String, callback: (String?, String?, Exception?) -> Unit) {
        val ref = database.reference.child("users").child(uid)
        ref.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                val profile = mapOf("nombre" to name, "email" to email, "rol" to GuardianSessionRepository.ROLE_TUTOR, "hogarId" to "casa-01")
                ref.setValue(profile).addOnSuccessListener { callback(GuardianSessionRepository.ROLE_TUTOR, name, null) }
                    .addOnFailureListener { callback(null, null, it) }
            } else {
                val role = snapshot.child("rol").getValue(String::class.java)?.lowercase()
                val profileName = snapshot.child("nombre").getValue(String::class.java).orEmpty().ifBlank { name }
                if (role != GuardianSessionRepository.ROLE_TUTOR && role != GuardianSessionRepository.ROLE_ADMIN) callback(null, null, IllegalStateException("El perfil no tiene un rol válido"))
                else callback(role, profileName, null)
            }
        }.addOnFailureListener { callback(null, null, it) }
    }
    companion object {
        fun selectedDoor(context: Context) = context.getSharedPreferences("guardian_preferences", Context.MODE_PRIVATE).getString("selectedDoorId", GuardianSessionRepository.DOOR_ENTRY) ?: GuardianSessionRepository.DOOR_ENTRY
    }
}
