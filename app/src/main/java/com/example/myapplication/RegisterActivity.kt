package com.example.myapplication

import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.data.FirebaseAuthRepository

class RegisterActivity : AppCompatActivity() {
    private val authRepository = FirebaseAuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val emailInput = findViewById<EditText>(R.id.etRegisterUsername)
        val passwordInput = findViewById<EditText>(R.id.etRegisterPassword)
        val confirmInput = findViewById<EditText>(R.id.etConfirmPassword)
        val registerButton = findViewById<Button>(R.id.btnRegisterSubmit)

        registerButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()
            val confirmation = confirmInput.text.toString()

            when {
                !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> emailInput.error = getString(R.string.invalid_email)
                password.length < 8 -> passwordInput.error = getString(R.string.password_requirements)
                password != confirmation -> confirmInput.error = getString(R.string.passwords_do_not_match)
                else -> {
                    registerButton.isEnabled = false
                    authRepository.register(email, password)
                        .addOnSuccessListener {
                            Toast.makeText(this, R.string.account_created, Toast.LENGTH_LONG).show()
                            finish()
                        }
                        .addOnFailureListener { error ->
                            registerButton.isEnabled = true
                            Toast.makeText(this, error.localizedMessage ?: getString(R.string.register_error), Toast.LENGTH_LONG).show()
                        }
                }
            }
        }
    }
}
