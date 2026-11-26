package com.example.myapplication

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

private data class DoorUiState(
    val title: String,
    val description: String,
    val color: Int,
    val cardBackground: Int,
    val icon: Int,
    val iconDescription: Int,
    val statusTextSizeSp: Float = 36f
)

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
        findViewById<Button>(R.id.navHome).setOnClickListener { /* Already on home. */ }
        findViewById<Button>(R.id.navSearch).setOnClickListener { startActivity(Intent(this, DoorSearchActivity::class.java)) }
        findViewById<Button>(R.id.navProfile).setOnClickListener { openProfile() }
        findViewById<Button>(R.id.btnDiagnostics).apply {
            visibility = if (isAdmin) View.VISIBLE else View.GONE
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
            val isDemoOffline = snapshot.demoMode && snapshot.lastMessage == getString(R.string.demo_offline_marker)
            val connected = snapshot.bluetoothState == BluetoothConnectionState.CONNECTED && door.connected
            val telemetry = when {
                isDemoOffline -> null
                snapshot.demoMode && snapshot.lastMessage.startsWith(getString(R.string.demo_message_prefix)) -> snapshot.telemetry
                connected -> snapshot.telemetry
                else -> null
            }

            findViewById<TextView>(R.id.tvScreenTitle).text = when {
                isAdmin -> getString(R.string.home_header_admin)
                snapshot.userName.isBlank() -> getString(R.string.home_header_fallback)
                else -> getString(R.string.home_header_named, snapshot.userName.trim())
            }
            findViewById<TextView>(R.id.tvHomeName).text = getString(R.string.home_house_short)
            findViewById<TextView>(R.id.tvSelectedDoor).text = getString(R.string.selected_door, door.name)
            findViewById<View>(R.id.tvDemoBadge).visibility = if (snapshot.demoMode) View.VISIBLE else View.GONE
            findViewById<TextView>(R.id.tvBluetoothState).text = getString(R.string.status_bluetooth_value, snapshot.bluetoothState.name)
            findViewById<TextView>(R.id.tvDoorState).text = getString(R.string.status_door_value, telemetry?.let { if (it.puertaAbierta) getString(R.string.door_open) else getString(R.string.door_closed) } ?: getString(R.string.no_data))
            findViewById<TextView>(R.id.tvDistance).text = getString(R.string.status_distance_value, telemetry?.takeIf { it.sensorValido }?.let { getString(R.string.distance_value, it.distanciaCm) } ?: getString(R.string.no_data))
            findViewById<TextView>(R.id.tvSensor).text = getString(R.string.status_sensor_value, telemetry?.let { if (it.sensorValido) getString(R.string.sensor_valid) else getString(R.string.sensor_no_reading) } ?: getString(R.string.sensor_pending))
            findViewById<TextView>(R.id.tvLastRead).text = getString(R.string.status_last_update_value, formatGuardianTime(snapshot.lastValidAt))
            findViewById<TextView>(R.id.tvLed).apply { visibility = if (isAdmin) View.VISIBLE else View.GONE; text = getString(R.string.status_led_value, telemetry?.let { if (it.ledOn) getString(R.string.on) else getString(R.string.off) } ?: getString(R.string.no_data)) }
            findViewById<TextView>(R.id.tvBuzzer).apply { visibility = if (isAdmin) View.VISIBLE else View.GONE; text = getString(R.string.status_buzzer_value, telemetry?.let { if (it.buzzerOn) getString(R.string.active) else getString(R.string.inactive) } ?: getString(R.string.no_data)) }
            val ui = buildDoorUiState(telemetry, connected, isDemoOffline, door.name)
            findViewById<TextView>(R.id.tvOverallState).apply {
                text = ui.title
                setTextColor(ContextCompat.getColor(this@GuardianHomeActivity, ui.color))
                textSize = ui.statusTextSizeSp
            }
            findViewById<TextView>(R.id.tvStatusDescription).apply {
                text = ui.description
                setTextColor(ContextCompat.getColor(this@GuardianHomeActivity, ui.color))
            }
            findViewById<View>(R.id.statusCard).background = ContextCompat.getDrawable(this, ui.cardBackground)
            findViewById<ImageView>(R.id.ivDoorStatus).apply {
                setImageResource(ui.icon)
                contentDescription = getString(ui.iconDescription)
            }
            findViewById<TextView>(R.id.tvTechnicalDetails).apply {
                visibility = if (isAdmin) View.VISIBLE else View.GONE
                if (isAdmin) text = getString(R.string.admin_details, door.id, snapshot.lastMessage.ifBlank { getString(R.string.no_data) }, snapshot.validMessages, snapshot.invalidMessages, snapshot.lastError.ifBlank { getString(R.string.none) })
            }
        }
    }

    private fun buildDoorUiState(telemetry: ArduinoTelemetry?, connected: Boolean, demoOffline: Boolean, doorName: String): DoorUiState {
        if (demoOffline) return DoorUiState(getString(R.string.status_title_disconnected), getString(R.string.status_description_disconnected), R.color.guardian_disconnected, R.drawable.bg_status_neutral, R.drawable.ic_state_bluetooth_off, R.string.state_icon_disconnected)
        if (telemetry == null) {
            return if (connected) DoorUiState(getString(R.string.status_title_connected), getString(R.string.status_description_waiting), R.color.guardian_header_blue, R.drawable.bg_status_neutral, R.drawable.ic_action_bluetooth, R.string.state_icon_connected)
            else DoorUiState(getString(R.string.status_title_disconnected), getString(R.string.status_description_disconnected), R.color.guardian_disconnected, R.drawable.bg_status_neutral, R.drawable.ic_state_bluetooth_off, R.string.state_icon_disconnected)
        }
        if (!telemetry.sensorValido) return DoorUiState(getString(R.string.status_title_sensor_error), getString(R.string.status_description_sensor_error), R.color.guardian_warning_text, R.drawable.bg_status_warning, R.drawable.ic_state_sensor_error, R.string.state_icon_sensor_error)
        return when (telemetry.estado) {
            ArduinoTelemetry.SAFE -> DoorUiState(getString(R.string.status_title_closed), getString(R.string.status_description_closed), R.color.guardian_safe, R.drawable.bg_status_safe, R.drawable.ic_state_door_closed, R.string.state_icon_closed)
            ArduinoTelemetry.WARNING -> DoorUiState(getString(R.string.status_title_warning), getString(R.string.status_description_warning, doorName), R.color.guardian_warning_text, R.drawable.bg_status_warning, R.drawable.ic_state_warning, R.string.state_icon_warning, 33f)
            ArduinoTelemetry.CRITICAL -> DoorUiState(getString(R.string.status_title_open), getString(R.string.status_description_open), R.color.guardian_critical, R.drawable.bg_status_critical, R.drawable.ic_state_door_open, R.string.state_icon_open)
            else -> DoorUiState(getString(R.string.status_title_sensor_error), getString(R.string.status_description_sensor_error), R.color.guardian_warning_text, R.drawable.bg_status_warning, R.drawable.ic_state_sensor_error, R.string.state_icon_sensor_error)
        }
    }
}

class TutorHomeActivity : GuardianHomeActivity() { override val isAdmin = false; override val layoutId = R.layout.activity_tutor_home }
class AdminHomeActivity : GuardianHomeActivity() { override val isAdmin = true; override val layoutId = R.layout.activity_admin_home }
