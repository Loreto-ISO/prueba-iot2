package com.example.myapplication

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class AdminActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); startActivity(android.content.Intent(this, AdminProfileActivity::class.java)); finish() }
}
