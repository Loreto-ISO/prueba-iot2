package com.example.myapplication.data

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun signIn(email: String, password: String): Task<AuthResult> =
        auth.signInWithEmailAndPassword(email.trim(), password)

    fun register(email: String, password: String): Task<AuthResult> =
        auth.createUserWithEmailAndPassword(email.trim(), password)

    fun sendPasswordReset(email: String) =
        auth.sendPasswordResetEmail(email.trim())

    fun signOut() = auth.signOut()
}
