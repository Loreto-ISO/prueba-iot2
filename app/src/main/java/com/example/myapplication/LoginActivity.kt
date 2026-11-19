package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.data.FirebaseAuthRepository

class LoginActivity : AppCompatActivity() {
    private val authRepository = FirebaseAuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        if (authRepository.currentUser != null) {
            openDashboard()
            return
        }

        val emailInput = findViewById<EditText>(R.id.etUsername)
        val passwordInput = findViewById<EditText>(R.id.etPassword)
        val loginButton = findViewById<Button>(R.id.btnLogin)
        val registerButton = findViewById<Button>(R.id.btnGoToRegister)
        val resetButton = findViewById<Button>(R.id.btnResetPassword)

        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailInput.error = getString(R.string.invalid_email)
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                passwordInput.error = getString(R.string.required_password)
                return@setOnClickListener
            }

            loginButton.isEnabled = false
            authRepository.signIn(email, password)
                .addOnSuccessListener { openDashboard() }
                .addOnFailureListener { error ->
                    loginButton.isEnabled = true
                    Toast.makeText(this, error.localizedMessage ?: getString(R.string.login_error), Toast.LENGTH_LONG).show()
                }
        }

        resetButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailInput.error = getString(R.string.invalid_email)
                return@setOnClickListener
            }
            authRepository.sendPasswordReset(email)
                .addOnSuccessListener { Toast.makeText(this, R.string.reset_sent, Toast.LENGTH_LONG).show() }
                .addOnFailureListener { error -> Toast.makeText(this, error.localizedMessage ?: getString(R.string.reset_error), Toast.LENGTH_LONG).show() }
        }

        registerButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun openDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }
}
