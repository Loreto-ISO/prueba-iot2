package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HistoryActivity : AppCompatActivity(), (GuardianSessionRepository.Snapshot) -> Unit {
    private var watching = false
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContentView(R.layout.activity_history); findViewById<TextView>(R.id.tvHistoryEmpty).text = getString(R.string.waiting_telemetry); findViewById<Button>(R.id.btnHistoryBluetooth).setOnClickListener { startActivity(Intent(this, BluetoothConnectionActivity::class.java)) } }
    override fun onStart() { super.onStart(); GuardianSessionRepository.observe(this); watching = true }
    override fun onStop() { if (watching) GuardianSessionRepository.removeObserver(this); watching = false; super.onStop() }
    override fun invoke(s: GuardianSessionRepository.Snapshot) { runOnUiThread { val text = buildString { if (s.lastValidAt > 0) append("${formatGuardianTime(s.lastValidAt)} — ${s.telemetry?.estado ?: getString(R.string.no_data)}\n"); if (s.bluetoothState == BluetoothConnectionState.CONNECTED) append(getString(R.string.bluetooth_connected) + "\n"); if (s.lastError.isNotBlank()) append(getString(R.string.error_entry, s.lastError)); if (isEmpty()) append(getString(R.string.no_history)) }; findViewById<TextView>(R.id.tvHistoryEmpty).text = text } }
}
