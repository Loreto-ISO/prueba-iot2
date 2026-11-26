package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.data.FirebaseAuthRepository

class HistoryActivity : AppCompatActivity(), (GuardianSessionRepository.Snapshot) -> Unit {
    private var watching = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GuardianSessionRepository.initialize(this)
        setContentView(R.layout.activity_history)
        findViewById<TextView>(R.id.tvHistoryEmpty).text = getString(R.string.history_empty)
        findViewById<Button>(R.id.btnHistoryBluetooth).setOnClickListener { startActivity(Intent(this, BluetoothConnectionActivity::class.java)) }
        findViewById<Button>(R.id.navHome).setOnClickListener { startActivity(Intent(this, homeActivity()).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)); finish() }
        findViewById<Button>(R.id.navSearch).setOnClickListener { startActivity(Intent(this, DoorSearchActivity::class.java)); finish() }
        findViewById<Button>(R.id.navProfile).setOnClickListener { startActivity(Intent(this, if (isAdmin()) AdminProfileActivity::class.java else TutorProfileActivity::class.java)); finish() }
    }
    private fun isAdmin() = GuardianSessionRepository.snapshot.role == GuardianSessionRepository.ROLE_ADMIN
    private fun homeActivity() = if (isAdmin()) AdminHomeActivity::class.java else TutorHomeActivity::class.java
    override fun onStart() { super.onStart(); GuardianSessionRepository.observe(this); watching = true }
    override fun onStop() { if (watching) GuardianSessionRepository.removeObserver(this); watching = false; super.onStop() }
    override fun invoke(s: GuardianSessionRepository.Snapshot) {
        runOnUiThread {
            val entries = mutableListOf<String>()
            if (s.lastValidAt > 0) entries.add("${formatGuardianTime(s.lastValidAt)} — ${s.telemetry?.estado ?: getString(R.string.sensor_no_reading)}")
            if (s.bluetoothState == BluetoothConnectionState.CONNECTED) entries.add(getString(R.string.bluetooth_connected))
            if (s.lastAck.isNotBlank()) entries.add(s.lastAck)
            if (s.lastError.isNotBlank()) entries.add(getString(R.string.error_entry, s.lastError))
            findViewById<TextView>(R.id.tvHistoryEmpty).text = entries.joinToString("\n").ifBlank { getString(R.string.no_history) }
        }
    }
}
