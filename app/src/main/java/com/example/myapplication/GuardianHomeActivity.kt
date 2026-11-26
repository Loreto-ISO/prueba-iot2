package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

abstract class GuardianHomeActivity : AppCompatActivity(), (GuardianSessionRepository.Snapshot) -> Unit {
    protected abstract val isAdmin: Boolean
    protected abstract val layoutId: Int
    private var observing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GuardianSessionRepository.initialize(this)
        setContentView(layoutId)
        findViewById<Button>(R.id.btnOpenBluetooth).setOnClickListener { startActivity(Intent(this, BluetoothConnectionActivity::class.java)) }
        findViewById<Button>(R.id.btnFindDoors).setOnClickListener { startActivity(Intent(this, DoorSearchActivity::class.java)) }
        findViewById<Button>(R.id.btnOpenProfile).setOnClickListener { openProfile() }
        findViewById<Button>(R.id.navHome).setOnClickListener { /* Already on the home screen. */ }
        findViewById<Button>(R.id.navSearch).setOnClickListener { startActivity(Intent(this, DoorSearchActivity::class.java)) }
        findViewById<Button>(R.id.navProfile).setOnClickListener { openProfile() }
        findViewById<Button>(R.id.btnDiagnostics).apply {
            visibility = if (isAdmin) android.view.View.VISIBLE else android.view.View.GONE
            setOnClickListener { startActivity(Intent(this@GuardianHomeActivity, DiagnosticsActivity::class.java)) }
        }
    }

    private fun openProfile() {
        startActivity(Intent(this, if (isAdmin) AdminProfileActivity::class.java else TutorProfileActivity::class.java))
    }

    override fun onStart() { super.onStart(); GuardianSessionRepository.observe(this); observing = true }
    override fun onStop() { if (observing) GuardianSessionRepository.removeObserver(this); observing = false; super.onStop() }

    override fun invoke(snapshot: GuardianSessionRepository.Snapshot) {
        runOnUiThread {
            val door = GuardianSessionRepository.doors.firstOrNull { it.id == snapshot.selectedDoorId }
                ?: GuardianSessionRepository.doors.firstOrNull()
                ?: return@runOnUiThread
            val realConnected = door.connected && snapshot.bluetoothState == BluetoothConnectionState.CONNECTED
            val telemetry = when {
                snapshot.demoMode && snapshot.lastMessage.startsWith("Modo demostración:") && snapshot.lastMessage.contains("desconectado", true) -> null
                snapshot.demoMode && snapshot.lastMessage.startsWith("Modo demostración:") -> snapshot.telemetry
                realConnected -> snapshot.telemetry
                else -> null
            }
            findViewById<TextView>(R.id.tvScreenTitle).text = if (isAdmin) getString(R.string.admin_home_title) else getString(R.string.tutor_welcome, snapshot.userName.ifBlank { getString(R.string.welcome_default) })
            findViewById<TextView>(R.id.tvHomeName).text = getString(R.string.home_name, snapshot.homeName.ifBlank { "Casa 01" })
            findViewById<TextView>(R.id.tvSelectedDoor).text = getString(R.string.selected_door, door.name)
            findViewById<TextView>(R.id.tvBluetoothState).text = if (snapshot.demoMode) getString(R.string.demo_bluetooth_state, snapshot.bluetoothState.name) else getString(R.string.bluetooth_state, snapshot.bluetoothState.name)
            val stateText = when {
                snapshot.demoMode && telemetry == null -> getString(R.string.demo_offline_label)
                telemetry == null && !realConnected -> getString(R.string.device_offline)
                telemetry == null -> getString(R.string.waiting_telemetry)
                !telemetry.sensorValido -> getString(R.string.sensor_no_reading)
                telemetry.estado == ArduinoTelemetry.SAFE -> getString(R.string.state_safe)
                telemetry.estado == ArduinoTelemetry.WARNING -> getString(R.string.state_warning)
                else -> getString(R.string.state_critical)
            }
            findViewById<TextView>(R.id.tvOverallState).text = if (snapshot.demoMode) getString(R.string.demo_state, stateText) else stateText
            findViewById<TextView>(R.id.tvDoorState).text = if (telemetry == null) getString(R.string.no_data) else if (telemetry.puertaAbierta) getString(R.string.door_open) else getString(R.string.door_closed)
            findViewById<TextView>(R.id.tvDistance).text = if (telemetry == null || !telemetry.sensorValido) getString(R.string.no_data) else getString(R.string.distance_value, telemetry.distanciaCm)
            findViewById<TextView>(R.id.tvLed).text = if (telemetry == null) getString(R.string.no_data) else if (telemetry.ledOn) getString(R.string.on) else getString(R.string.off)
            findViewById<TextView>(R.id.tvBuzzer).text = if (telemetry == null) getString(R.string.no_data) else if (telemetry.buzzerOn) getString(R.string.active) else getString(R.string.inactive)
            findViewById<TextView>(R.id.tvLastRead).text = getString(R.string.last_read, formatGuardianTime(snapshot.lastValidAt))
            findViewById<TextView>(R.id.tvSensor).text = if (telemetry == null) getString(R.string.sensor_pending) else if (telemetry.sensorValido) getString(R.string.sensor_valid) else getString(R.string.sensor_no_reading)
            findViewById<TextView>(R.id.tvTechnicalDetails).apply {
                visibility = if (isAdmin) android.view.View.VISIBLE else android.view.View.GONE
                if (isAdmin) text = getString(R.string.admin_details, door.id, snapshot.lastMessage.ifBlank { getString(R.string.no_data) }, snapshot.validMessages, snapshot.invalidMessages, snapshot.lastError.ifBlank { getString(R.string.none) })
            }
        }
    }
}

class TutorHomeActivity : GuardianHomeActivity() { override val isAdmin = false; override val layoutId = R.layout.activity_tutor_home }
class AdminHomeActivity : GuardianHomeActivity() { override val isAdmin = true; override val layoutId = R.layout.activity_admin_home }
