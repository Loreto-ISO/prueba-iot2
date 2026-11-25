package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.data.FirebaseAuthRepository

class AdminActivity : AppCompatActivity() {
    private val authRepository = FirebaseAuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentUser = authRepository.currentUser
        if (currentUser == null) {
            goToLogin()
            return
        }

        setContentView(R.layout.activity_admin)

        val tvProfileEmail = findViewById<TextView>(R.id.tvProfileEmail)
        val etNewPassword = findViewById<EditText>(R.id.etNewPassword)
        val etConfirmNewPassword = findViewById<EditText>(R.id.etConfirmNewPassword)
        val btnUpdatePassword = findViewById<Button>(R.id.btnUpdatePassword)
        val btnSendRecovery = findViewById<Button>(R.id.btnSendRecoveryProfile)
        val btnLogout = findViewById<Button>(R.id.btnLogout)

        val email = currentUser.email.orEmpty()
        tvProfileEmail.text = email.ifBlank { "Usuario autenticado" }

        btnUpdatePassword.setOnClickListener {
            val newPass = etNewPassword.text.toString()
            val confirmPass = etConfirmNewPassword.text.toString()

            when {
                newPass.length < 8 -> {
                    etNewPassword.error = "Mínimo 8 caracteres"
                }
                newPass != confirmPass -> {
                    etConfirmNewPassword.error = "Las contraseñas no coinciden"
                }
                else -> {
                    btnUpdatePassword.isEnabled = false
                    currentUser.updatePassword(newPass)
                        .addOnSuccessListener {
                            btnUpdatePassword.isEnabled = true
                            etNewPassword.text.clear()
                            etConfirmNewPassword.text.clear()
                            Toast.makeText(
                                this,
                                "✅ Contraseña actualizada correctamente",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        .addOnFailureListener { error ->
                            btnUpdatePassword.isEnabled = true
                            Toast.makeText(
                                this,
                                "Por seguridad, vuelve a iniciar sesión antes de cambiar la clave (${error.localizedMessage})",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
            }
        }

        btnSendRecovery.setOnClickListener {
            if (email.isBlank()) {
                Toast.makeText(this, "No hay correo asociado a esta cuenta", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            authRepository.sendPasswordReset(email)
                .addOnSuccessListener {
                    Toast.makeText(
                        this,
                        "📧 Enlace de recuperación enviado a $email",
                        Toast.LENGTH_LONG
                    ).show()
                }
                .addOnFailureListener { error ->
                    Toast.makeText(
                        this,
                        "No se pudo enviar el correo: ${error.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }

        btnLogout.setOnClickListener {
            authRepository.signOut()
            goToLogin()
        }

        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        val navPrincipal = findViewById<LinearLayout>(R.id.navPrincipal)
        val navBuscar = findViewById<LinearLayout>(R.id.navBuscar)

        navPrincipal.setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }

        navBuscar.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}