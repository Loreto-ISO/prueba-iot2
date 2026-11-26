package com.example.myapplication

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ControlActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContentView(R.layout.activity_control); val connected = BluetoothConnectionManager.connected(); findViewById<TextView>(R.id.tvControlAvailability)?.text = getString(if (connected) R.string.firmware_ack_required else R.string.control_unavailable); listOf(R.id.btnLedOn, R.id.btnLedOff, R.id.btnBuzzerOn, R.id.btnBuzzerOff).forEach { id -> findViewById<Button>(id).isEnabled = false } }
}
