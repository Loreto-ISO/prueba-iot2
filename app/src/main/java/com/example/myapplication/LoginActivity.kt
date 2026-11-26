package com.example.myapplication

import android.content.Intent
import android.os.Bundle
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
            authRepository.sendPasswordReset(value).addOnSuccessListener { toast(R.string.reset_sent) }.addOnFailureListener { toastText(it.localizedMessage ?: getString(R.string.reset_error)) }
        }
        authRepository.currentUser?.let { resolveRole(it.uid, it.displayName.orEmpty(), it.email.orEmpty()); return }
        login.setOnClickListener {
            val address = email.text.toString().trim(); val secret = password.text.toString()
            if (!Patterns.EMAIL_ADDRESS.matcher(address).matches()) { email.error = getString(R.string.invalid_email); return@setOnClickListener }
            if (secret.isBlank()) { password.error = getString(R.string.required_password); return@setOnClickListener }
            login.isEnabled = false; progress.visibility = View.VISIBLE
            authRepository.signIn(address, secret).addOnSuccessListener { result ->
                resolveRole(result.user?.uid.orEmpty(), result.user?.displayName.orEmpty(), result.user?.email.orEmpty())
            }.addOnFailureListener { login.isEnabled = true; progress.visibility = View.GONE; toastText(it.localizedMessage ?: getString(R.string.login_error)) }
        }
    }
    private fun resolveRole(uid: String, displayName: String, email: String) {
        if (uid.isBlank()) { toastText(getString(R.string.profile_error)); return }
        profileRepository.loadOrCreate(uid, displayName, email) { role, name, error -> runOnUiThread {
            if (error != null || role == null) { findViewById<Button>(R.id.btnLogin).isEnabled = true; findViewById<ProgressBar>(R.id.loginProgress).visibility = View.GONE; toastText(error?.localizedMessage ?: getString(R.string.profile_error)); return@runOnUiThread }
            GuardianSessionRepository.setProfile(role, name.orEmpty())
            val destination = if (role == GuardianSessionRepository.ROLE_ADMIN) AdminHomeActivity::class.java else TutorHomeActivity::class.java
            startActivity(Intent(this, destination).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        } }
    }
    private fun toast(id: Int) = Toast.makeText(this, id, Toast.LENGTH_LONG).show()
    private fun toastText(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
