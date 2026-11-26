package com.example.myapplication

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class DashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); val user = com.example.myapplication.data.FirebaseAuthRepository().currentUser; if (user == null) { startActivity(android.content.Intent(this, LoginActivity::class.java)); finish(); return }; GuardianProfileRepository().loadOrCreate(user.uid, user.displayName.orEmpty(), user.email.orEmpty()) { role, name, error -> runOnUiThread { if (error != null || role == null) { startActivity(android.content.Intent(this, LoginActivity::class.java)); finish() } else { GuardianSessionRepository.setProfile(role, name.orEmpty()); startActivity(android.content.Intent(this, if (role == GuardianSessionRepository.ROLE_ADMIN) AdminHomeActivity::class.java else TutorHomeActivity::class.java)); finish() } } } }
}
