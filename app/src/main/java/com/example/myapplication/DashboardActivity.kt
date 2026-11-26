package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.data.FirebaseAuthRepository

class DashboardActivity : AppCompatActivity() {

    private val authRepository =
        FirebaseAuthRepository()

    private val profileRepository =
        GuardianProfileRepository()

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        GuardianSessionRepository.initialize(this)

        val user = authRepository.currentUser

        if (user == null) {
            openLogin()
            return
        }

        profileRepository.loadOrCreate(
            uid = user.uid,
            name = user.displayName.orEmpty(),
            email = user.email.orEmpty()
        ) { profile, error ->

            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    return@runOnUiThread
                }

                if (error != null || profile == null) {
                    Log.e(
                        TAG,
                        "No fue posible cargar el perfil",
                        error
                    )

                    Toast.makeText(
                        this,
                        getString(
                            R.string.profile_login_error
                        ),
                        Toast.LENGTH_LONG
                    ).show()

                    authRepository.signOut()
                    openLogin()
                    return@runOnUiThread
                }

                GuardianSessionRepository.setProfile(
                    role = profile.role,
                    name = profile.name,
                    home = profile.homeName
                )

                val destination =
                    if (
                        profile.role ==
                        GuardianSessionRepository.ROLE_ADMIN
                    ) {
                        AdminHomeActivity::class.java
                    } else {
                        TutorHomeActivity::class.java
                    }

                try {
                    val intent =
                        Intent(this, destination).apply {
                            flags =
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }

                    startActivity(intent)
                    finish()
                } catch (exception: RuntimeException) {
                    Log.e(
                        TAG,
                        "No fue posible abrir el menú principal",
                        exception
                    )

                    Toast.makeText(
                        this,
                        getString(R.string.navigation_error),
                        Toast.LENGTH_LONG
                    ).show()

                    authRepository.signOut()
                    openLogin()
                }
            }
        }
    }

    private fun openLogin() {
        val intent =
            Intent(
                this,
                LoginActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

        startActivity(intent)
        finish()
    }

    companion object {
        private const val TAG = "GuardianApp"
    }
}