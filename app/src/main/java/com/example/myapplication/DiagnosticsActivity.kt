package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class DiagnosticsActivity : AppCompatActivity(), (GuardianSessionRepository.Snapshot) -> Unit {
    private var observerAdded = false
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContentView(R.layout.activity_diagnostics); val service = BluetoothConnectionManager.get(this); findViewById<Button>(R.id.btnDemoMode).visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE; findViewById<Button>(R.id.btnDemoMode).setOnClickListener { GuardianSessionRepository.setDemo(true); findViewById<View>(R.id.demoControls).visibility = View.VISIBLE }; findViewById<Button>(R.id.btnDemoSafe).setOnClickListener { GuardianSessionRepository.updateDemo(ArduinoTelemetry.SAFE) }; findViewById<Button>(R.id.btnDemoWarning).setOnClickListener { GuardianSessionRepository.updateDemo(ArduinoTelemetry.WARNING) }; findViewById<Button>(R.id.btnDemoCritical).setOnClickListener { GuardianSessionRepository.updateDemo(ArduinoTelemetry.CRITICAL) }; findViewById<Button>(R.id.btnDemoSensorError).setOnClickListener { GuardianSessionRepository.updateDemo("SENSOR_ERROR") }; findViewById<Button>(R.id.btnDemoOffline).setOnClickListener { GuardianSessionRepository.updateDemo("OFFLINE") }; findViewById<Button>(R.id.btnReconnect).setOnClickListener { try { val device = service.findPairedHC05(); if (device == null) GuardianSessionRepository.setError(getString(R.string.hc05_not_paired)) else service.connect(device) } catch (e: SecurityException) { GuardianSessionRepository.setError(e.message ?: getString(R.string.bluetooth_permission)) } }; findViewById<Button>(R.id.btnDisconnect).setOnClickListener { service.disconnect() }; findViewById<Button>(R.id.btnStatusRequest).apply { isEnabled = false; setOnClickListener { service.sendCommand("STATUS") } }; findViewById<Button>(R.id.btnClearDiagnostics).setOnClickListener { GuardianSessionRepository.setError("") }; if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.BLUETOOTH_CONNECT) }
    override fun onStart() { super.onStart(); GuardianSessionRepository.observe(this); observerAdded = true }
    override fun onStop() { if (observerAdded) GuardianSessionRepository.removeObserver(this); observerAdded = false; super.onStop() }
    override fun invoke(s: GuardianSessionRepository.Snapshot) { runOnUiThread { findViewById<TextView>(R.id.tvDiagnostics).text = getString(R.string.diagnostics_details, BluetoothConnectionManager.get(this).isBluetoothAvailable, BluetoothConnectionManager.get(this).isBluetoothEnabled(), ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < 31, s.bluetoothState.name, s.lastMessage.ifBlank { getString(R.string.no_data) }, s.lastAck.ifBlank { getString(R.string.none) }, s.lastError.ifBlank { getString(R.string.none) }, s.telemetry?.sensorValido ?: false, s.telemetry?.distanciaCm ?: -1, s.telemetry?.puertaAbierta ?: false, s.telemetry?.ledOn ?: false, s.telemetry?.buzzerOn ?: false, s.validMessages, s.invalidMessages, formatGuardianTime(s.lastValidAt)) } }
}
