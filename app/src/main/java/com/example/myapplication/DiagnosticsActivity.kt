package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build

class DiagnosticsActivity : AppCompatActivity(), (GuardianSessionRepository.Snapshot) -> Unit {
    private var observerAdded = false
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) GuardianSessionRepository.setError(getString(R.string.bluetooth_permission))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GuardianSessionRepository.initialize(this)
        setContentView(R.layout.activity_diagnostics)
        val service = runCatching { BluetoothConnectionManager.get(this) }.getOrElse { error ->
            Log.e(LOG_TAG, "Bluetooth service unavailable in diagnostics", error)
            GuardianSessionRepository.setError(error.localizedMessage ?: getString(R.string.bluetooth_not_available))
            null
        }
        findViewById<Button>(R.id.btnDemoMode).apply {
            visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
            setOnClickListener { GuardianSessionRepository.setDemo(true); findViewById<View>(R.id.demoControls).visibility = View.VISIBLE; GuardianSessionRepository.updateDemo(ArduinoTelemetry.SAFE) }
        }
        findViewById<Button>(R.id.btnDemoSafe).setOnClickListener { GuardianSessionRepository.updateDemo(ArduinoTelemetry.SAFE) }
        findViewById<Button>(R.id.btnDemoWarning).setOnClickListener { GuardianSessionRepository.updateDemo(ArduinoTelemetry.WARNING) }
        findViewById<Button>(R.id.btnDemoCritical).setOnClickListener { GuardianSessionRepository.updateDemo(ArduinoTelemetry.CRITICAL) }
        findViewById<Button>(R.id.btnDemoSensorError).setOnClickListener { GuardianSessionRepository.updateDemo("SENSOR_ERROR") }
        findViewById<Button>(R.id.btnDemoOffline).setOnClickListener { GuardianSessionRepository.updateDemo("OFFLINE") }
        findViewById<Button>(R.id.btnReconnect).setOnClickListener {
            try {
                val device = service?.findPairedHC05()
                if (device == null) GuardianSessionRepository.setError(getString(R.string.hc05_not_paired)) else service.connect(device)
            } catch (error: SecurityException) { GuardianSessionRepository.setError(error.localizedMessage ?: getString(R.string.bluetooth_permission)); Log.w(LOG_TAG, "Bluetooth permission unavailable", error) }
        }
        findViewById<Button>(R.id.btnDisconnect).setOnClickListener { service?.disconnect() }
        findViewById<Button>(R.id.btnStatusRequest).apply { isEnabled = false; contentDescription = getString(R.string.available_with_firmware) }
        findViewById<Button>(R.id.btnClearDiagnostics).setOnClickListener { GuardianSessionRepository.setError("") }
        findViewById<Button>(R.id.navHome).setOnClickListener { startActivity(Intent(this, homeActivity()).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)); finish() }
        findViewById<Button>(R.id.navSearch).setOnClickListener { startActivity(Intent(this, DoorSearchActivity::class.java)); finish() }
        findViewById<Button>(R.id.navProfile).setOnClickListener { startActivity(Intent(this, if (GuardianSessionRepository.snapshot.role == GuardianSessionRepository.ROLE_ADMIN) AdminProfileActivity::class.java else TutorProfileActivity::class.java)); finish() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }
    private fun homeActivity() = if (GuardianSessionRepository.snapshot.role == GuardianSessionRepository.ROLE_ADMIN) AdminHomeActivity::class.java else TutorHomeActivity::class.java
    override fun onStart() { super.onStart(); GuardianSessionRepository.observe(this); observerAdded = true }
    override fun onStop() { if (observerAdded) GuardianSessionRepository.removeObserver(this); observerAdded = false; super.onStop() }
    override fun invoke(s: GuardianSessionRepository.Snapshot) {
        runOnUiThread {
            val service = try { BluetoothConnectionManager.get(this) } catch (error: RuntimeException) { Log.e(LOG_TAG, "Bluetooth diagnostics unavailable", error); null }
            val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            findViewById<TextView>(R.id.tvDiagnostics).text = getString(R.string.diagnostics_details, service?.isBluetoothAvailable == true, service?.isBluetoothEnabled() == true, permissionGranted, s.bluetoothState.name, s.lastMessage.ifBlank { getString(R.string.no_data) }, s.lastAck.ifBlank { getString(R.string.none) }, s.lastError.ifBlank { getString(R.string.none) }, s.telemetry?.sensorValido ?: false, s.telemetry?.distanciaCm ?: -1, s.telemetry?.puertaAbierta ?: false, s.telemetry?.ledOn ?: false, s.telemetry?.buzzerOn ?: false, s.validMessages, s.invalidMessages, formatGuardianTime(s.lastValidAt))
            findViewById<View>(R.id.demoControls).visibility = if (s.demoMode && BuildConfig.DEBUG) View.VISIBLE else View.GONE
        }
    }
    companion object { private const val LOG_TAG = "GuardianApp" }
}
