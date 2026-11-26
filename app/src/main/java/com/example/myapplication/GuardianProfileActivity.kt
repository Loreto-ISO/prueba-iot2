package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.data.FirebaseAuthRepository

abstract class GuardianProfileActivity : AppCompatActivity() {
    protected abstract val admin: Boolean
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GuardianSessionRepository.initialize(this)
        setContentView(R.layout.activity_guardian_profile)
        val user = FirebaseAuthRepository().currentUser
        findViewById<TextView>(R.id.tvProfileName).text = GuardianSessionRepository.snapshot.userName.ifBlank { getString(R.string.welcome_default) }
        findViewById<TextView>(R.id.tvProfileEmail).text = user?.email.orEmpty().ifBlank { getString(R.string.no_data) }
        findViewById<TextView>(R.id.tvProfileRole).text = getString(if (admin) R.string.role_admin else R.string.role_tutor)
        findViewById<TextView>(R.id.tvProfileHome).text = getString(R.string.home_name, GuardianSessionRepository.snapshot.homeName.ifBlank { "Casa 01" })
        val selected = GuardianSessionRepository.doors.firstOrNull { it.id == GuardianSessionRepository.snapshot.selectedDoorId } ?: GuardianSessionRepository.doors.firstOrNull()
        findViewById<TextView>(R.id.tvProfileDoor).text = selected?.let { getString(R.string.selected_door, it.name) } ?: getString(R.string.no_data)
        findViewById<Button>(R.id.btnResetProfilePassword).setOnClickListener {
            val email = user?.email.orEmpty()
            if (email.isBlank()) { Toast.makeText(this, R.string.profile_error, Toast.LENGTH_LONG).show(); return@setOnClickListener }
            FirebaseAuthRepository().sendPasswordReset(email).addOnSuccessListener { Toast.makeText(this, R.string.reset_sent, Toast.LENGTH_LONG).show() }
                .addOnFailureListener { error -> Log.e(LOG_TAG, "Password reset request failed", error); Toast.makeText(this, error.localizedMessage ?: getString(R.string.reset_error), Toast.LENGTH_LONG).show() }
        }
        findViewById<Button>(R.id.btnSupport).setOnClickListener {
            val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + getString(R.string.support_email))).putExtra(Intent.EXTRA_SUBJECT, getString(R.string.support_subject))
            try { startActivity(mail) } catch (error: android.content.ActivityNotFoundException) { Log.w(LOG_TAG, "No mail application available", error); Toast.makeText(this, R.string.no_mail_client, Toast.LENGTH_LONG).show() }
        }
        findViewById<Button>(R.id.btnCloseSession).setOnClickListener {
            BluetoothConnectionManager.close()
            FirebaseAuthRepository().signOut()
            startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }
        findViewById<Button>(R.id.btnProfileDiagnostics).setOnClickListener { startActivity(Intent(this, DiagnosticsActivity::class.java)) }
        findViewById<Button>(R.id.navHome).setOnClickListener { startActivity(Intent(this, if (admin) AdminHomeActivity::class.java else TutorHomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)); finish() }
        findViewById<Button>(R.id.navSearch).setOnClickListener { startActivity(Intent(this, DoorSearchActivity::class.java)); finish() }
        findViewById<Button>(R.id.navProfile).setOnClickListener { /* Already on profile. */ }
    }
    companion object { private const val LOG_TAG = "GuardianApp" }
}
class TutorProfileActivity : GuardianProfileActivity() { override val admin = false }
class AdminProfileActivity : GuardianProfileActivity() { override val admin = true }
