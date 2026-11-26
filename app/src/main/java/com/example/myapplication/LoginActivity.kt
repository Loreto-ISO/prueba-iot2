package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.data.FirebaseAuthRepository

class LoginActivity : AppCompatActivity() {
    private val authRepository = FirebaseAuthRepository()
    private val profileRepository = GuardianProfileRepository()
    private var resolvingProfile = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GuardianSessionRepository.initialize(this)
        setContentView(R.layout.activity_login)
        val email = findViewById<EditText>(R.id.etUsername)
        val password = findViewById<EditText>(R.id.etPassword)
        val login = findViewById<Button>(R.id.btnLogin)
        val progress = findViewById<ProgressBar>(R.id.loginProgress)
        findViewById<Button>(R.id.btnGoToRegister).setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
        findViewById<Button>(R.id.btnResetPassword).setOnClickListener {
            val value = email.text.toString().trim()
            if (!Patterns.EMAIL_ADDRESS.matcher(value).matches()) { email.error = getString(R.string.invalid_email); return@setOnClickListener }
            authRepository.sendPasswordReset(value).addOnSuccessListener { toast(R.string.reset_sent) }.addOnFailureListener { error -> Log.e(LOG_TAG, "Password reset failed", error); toastText(error.localizedMessage ?: getString(R.string.reset_error)) }
        }
        authRepository.currentUser?.let { resolveRole(it.uid, it.displayName.orEmpty(), it.email.orEmpty(), login, progress); return }
        login.setOnClickListener {
            val address = email.text.toString().trim(); val secret = password.text.toString()
            if (!Patterns.EMAIL_ADDRESS.matcher(address).matches()) { email.error = getString(R.string.invalid_email); return@setOnClickListener }
            if (secret.isBlank()) { password.error = getString(R.string.required_password); return@setOnClickListener }
            login.isEnabled = false; progress.visibility = View.VISIBLE
            authRepository.signIn(address, secret).addOnSuccessListener { result ->
                val user = result.user
                if (user == null) { restoreLogin(login, progress, getString(R.string.login_error)); return@addOnSuccessListener }
                resolveRole(user.uid, user.displayName.orEmpty(), user.email.orEmpty(), login, progress)
            }.addOnFailureListener { error -> Log.e(LOG_TAG, "Firebase Authentication sign-in failed", error); restoreLogin(login, progress, error.localizedMessage ?: getString(R.string.login_error)) }
        }
    }
    private fun resolveRole(uid: String, displayName: String, email: String, login: Button, progress: ProgressBar) {
        if (resolvingProfile) return
        if (uid.isBlank()) { restoreLogin(login, progress, getString(R.string.profile_error)); return }
        resolvingProfile = true
        login.isEnabled = false; progress.visibility = View.VISIBLE
        profileRepository.loadOrCreate(uid, displayName, email) { profile, error -> runOnUiThread {
            resolvingProfile = false
            if (isFinishing || isDestroyed) return@runOnUiThread
            if (error != null || profile == null) {
                Log.e(LOG_TAG, "Authenticated user profile could not be resolved", error)
                authRepository.signOut()
                restoreLogin(login, progress, getString(R.string.profile_login_error))
                return@runOnUiThread
            }
            GuardianSessionRepository.setProfile(profile.role, profile.name, profile.homeName)
            val destination = if (profile.role == GuardianSessionRepository.ROLE_ADMIN) AdminHomeActivity::class.java else TutorHomeActivity::class.java
            try {
                startActivity(Intent(this, destination).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            } catch (failure: RuntimeException) {
                Log.e(LOG_TAG, "Could not navigate to role home", failure)
                authRepository.signOut()
                restoreLogin(login, progress, getString(R.string.navigation_error))
            }
        } }
    }
    private fun restoreLogin(login: Button, progress: ProgressBar, message: String) { login.isEnabled = true; progress.visibility = View.GONE; toastText(message) }
    private fun toast(id: Int) = Toast.makeText(this, id, Toast.LENGTH_LONG).show()
    private fun toastText(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    companion object { private const val LOG_TAG = "GuardianApp" }
}
