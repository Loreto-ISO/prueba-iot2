package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

abstract class GuardianHomeActivity : AppCompatActivity(), (GuardianSessionRepository.Snapshot) -> Unit {
    protected abstract val isAdmin: Boolean
    protected abstract val layoutId: Int
    private var observing = false
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); GuardianSessionRepository.initialize(this); setContentView(layoutId); findViewById<Button>(R.id.btnOpenBluetooth).setOnClickListener { startActivity(Intent(this, BluetoothConnectionActivity::class.java)) }; findViewById<Button>(R.id.btnFindDoors).setOnClickListener { startActivity(Intent(this, DoorSearchActivity::class.java)) }; findViewById<Button>(R.id.btnOpenProfile).setOnClickListener { startActivity(Intent(this, if (isAdmin) AdminProfileActivity::class.java else TutorProfileActivity::class.java)) }; if (isAdmin) findViewById<Button>(R.id.btnDiagnostics).visibility = View.VISIBLE else findViewById<Button>(R.id.btnDiagnostics).visibility = View.GONE }
    override fun onStart() { super.onStart(); GuardianSessionRepository.observe(this); observing = true }
    override fun onStop() { if (observing) GuardianSessionRepository.removeObserver(this); observing = false; super.onStop() }
    override fun invoke(s: GuardianSessionRepository.Snapshot) {
        runOnUiThread {
            val door = GuardianSessionRepository.doors.first { it.id == s.selectedDoorId }
            findViewById<TextView>(R.id.tvScreenTitle).text = if (isAdmin) getString(R.string.admin_home_title) else getString(R.string.tutor_welcome, s.userName.ifBlank { getString(R.string.welcome_default) })
            findViewById<TextView>(R.id.tvHomeName).text = getString(R.string.home_name, s.homeName)
            findViewById<TextView>(R.id.tvSelectedDoor).text = getString(R.string.selected_door, door.name)
            findViewById<TextView>(R.id.tvBluetoothState).text = getString(R.string.bluetooth_state, s.bluetoothState.name)
            val t = if (door.connected && s.bluetoothState == BluetoothConnectionState.CONNECTED) s.telemetry else null
            findViewById<TextView>(R.id.tvOverallState).text = when {
                !door.connected || s.bluetoothState != BluetoothConnectionState.CONNECTED -> getString(R.string.device_offline)
                t == null -> getString(R.string.waiting_telemetry)
                !t.sensorValido -> getString(R.string.sensor_no_reading)
                t.estado == ArduinoTelemetry.SAFE -> getString(R.string.state_safe)
                t.estado == ArduinoTelemetry.WARNING -> getString(R.string.state_warning)
                else -> getString(R.string.state_critical)
            }
            findViewById<TextView>(R.id.tvDoorState).text = if (t == null) getString(R.string.no_data) else if (t.puertaAbierta) getString(R.string.door_open) else getString(R.string.door_closed)
            findViewById<TextView>(R.id.tvDistance).text = if (t == null || !t.sensorValido) getString(R.string.no_data) else getString(R.string.distance_value, t.distanciaCm)
            findViewById<TextView>(R.id.tvLed).text = if (t == null) getString(R.string.no_data) else if (t.ledOn) getString(R.string.on) else getString(R.string.off)
            findViewById<TextView>(R.id.tvBuzzer).text = if (t == null) getString(R.string.no_data) else if (t.buzzerOn) getString(R.string.active) else getString(R.string.inactive)
            findViewById<TextView>(R.id.tvLastRead).text = getString(R.string.last_read, formatGuardianTime(s.lastValidAt))
            findViewById<TextView>(R.id.tvSensor).text = if (t == null) getString(R.string.sensor_pending) else if (t.sensorValido) getString(R.string.sensor_valid) else getString(R.string.sensor_no_reading)
            if (isAdmin) findViewById<TextView>(R.id.tvTechnicalDetails).text = getString(R.string.admin_details, door.id, s.lastMessage.ifBlank { getString(R.string.no_data) }, s.validMessages, s.invalidMessages, s.lastError.ifBlank { getString(R.string.none) })
            findViewById<Button>(R.id.btnDiagnostics).setOnClickListener { startActivity(Intent(this, DiagnosticsActivity::class.java)) }
        }
    }
}
class TutorHomeActivity : GuardianHomeActivity() { override val isAdmin = false; override val layoutId = R.layout.activity_tutor_home }
class AdminHomeActivity : GuardianHomeActivity() { override val isAdmin = true; override val layoutId = R.layout.activity_admin_home }
