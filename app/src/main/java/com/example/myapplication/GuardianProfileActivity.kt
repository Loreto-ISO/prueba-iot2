package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.data.FirebaseAuthRepository

abstract class GuardianProfileActivity : AppCompatActivity() {
    protected abstract val admin: Boolean
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); GuardianSessionRepository.initialize(this); setContentView(R.layout.activity_guardian_profile); val user = FirebaseAuthRepository().currentUser; findViewById<TextView>(R.id.tvProfileName).text = GuardianSessionRepository.snapshot.userName.ifBlank { getString(R.string.welcome_default) }; findViewById<TextView>(R.id.tvProfileEmail).text = user?.email.orEmpty(); findViewById<TextView>(R.id.tvProfileRole).text = getString(if (admin) R.string.role_admin else R.string.role_tutor); findViewById<TextView>(R.id.tvProfileHome).text = getString(R.string.home_name, GuardianSessionRepository.snapshot.homeName); findViewById<TextView>(R.id.tvProfileDoor).text = getString(R.string.selected_door, GuardianSessionRepository.doors.first { it.id == GuardianSessionRepository.snapshot.selectedDoorId }.name); findViewById<Button>(R.id.btnResetProfilePassword).setOnClickListener { val email = user?.email.orEmpty(); if (email.isBlank()) Toast.makeText(this, R.string.profile_error, Toast.LENGTH_LONG).show() else FirebaseAuthRepository().sendPasswordReset(email).addOnSuccessListener { Toast.makeText(this, R.string.reset_sent, Toast.LENGTH_LONG).show() }.addOnFailureListener { Toast.makeText(this, it.localizedMessage ?: getString(R.string.reset_error), Toast.LENGTH_LONG).show() } }; findViewById<Button>(R.id.btnSupport).setOnClickListener { val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + getString(R.string.support_email))).putExtra(Intent.EXTRA_SUBJECT, getString(R.string.support_subject)); try { startActivity(mail) } catch (_: android.content.ActivityNotFoundException) { Toast.makeText(this, R.string.no_mail_client, Toast.LENGTH_LONG).show() } }; findViewById<Button>(R.id.btnCloseSession).setOnClickListener { BluetoothConnectionManager.close(); FirebaseAuthRepository().signOut(); startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)); finish() }; findViewById<Button>(R.id.btnProfileDiagnostics).setOnClickListener { startActivity(Intent(this, DiagnosticsActivity::class.java)) } }
}
class TutorProfileActivity : GuardianProfileActivity() { override val admin = false }
class AdminProfileActivity : GuardianProfileActivity() { override val admin = true }
