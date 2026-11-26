package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import android.bluetooth.BluetoothDevice
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.CopyOnWriteArraySet

/** Application-scoped observable state; callbacks never retain an Activity. */
object GuardianSessionRepository {
    data class Snapshot(
        val role: String = ROLE_TUTOR,
        val userName: String = "",
        val homeName: String = "Casa 01",
        val selectedDoorId: String = DOOR_ENTRY,
        val bluetoothState: BluetoothConnectionState = BluetoothConnectionState.DISCONNECTED,
        val telemetry: ArduinoTelemetry? = null,
        val lastValidAt: Long = 0L,
        val lastError: String = "",
        val lastAck: String = "",
        val lastMessage: String = "",
        val validMessages: Int = 0,
        val invalidMessages: Int = 0,
        val lastDisconnectAt: Long = 0L,
        val demoMode: Boolean = false
    )
    data class Door(val id: String, val name: String, val location: String, val connected: Boolean)
    private val observers = CopyOnWriteArraySet<(Snapshot) -> Unit>()
    @Volatile private var current = Snapshot()
    private var preferences: SharedPreferences? = null
    val snapshot: Snapshot get() = current
    val doors = listOf(Door(DOOR_KITCHEN, "Cocina", "Cocina", false), Door(DOOR_ENTRY, "Entrada", "Entrada", true), Door(DOOR_BATH, "Baño", "Baño", false))

    fun initialize(context: Context) { preferences = context.applicationContext.getSharedPreferences("guardian_preferences", Context.MODE_PRIVATE); val id = preferences?.getString(KEY_SELECTED_DOOR, DOOR_ENTRY) ?: DOOR_ENTRY; update { it.copy(selectedDoorId = id) } }
    fun observe(observer: (Snapshot) -> Unit) { observers.add(observer); observer(current) }
    fun removeObserver(observer: (Snapshot) -> Unit) { observers.remove(observer) }
    fun setProfile(role: String, name: String, home: String = "Casa 01") = update { it.copy(role = if (role == ROLE_ADMIN) ROLE_ADMIN else ROLE_TUTOR, userName = name, homeName = home) }
    fun selectDoor(id: String) { if (doors.any { it.id == id }) { preferences?.edit()?.putString(KEY_SELECTED_DOOR, id)?.apply(); update { it.copy(selectedDoorId = id) } } }
    fun setBluetooth(state: BluetoothConnectionState) = update { it.copy(bluetoothState = state, lastDisconnectAt = if (state == BluetoothConnectionState.DISCONNECTED || state == BluetoothConnectionState.ERROR) System.currentTimeMillis() else it.lastDisconnectAt) }
    fun acceptTelemetry(value: ArduinoTelemetry, raw: String = value.rawJson) = update { it.copy(telemetry = value, lastValidAt = System.currentTimeMillis(), lastMessage = raw, validMessages = it.validMessages + 1, lastError = "") }
    fun invalidMessage(message: String) = update { it.copy(invalidMessages = it.invalidMessages + 1, lastError = message) }
    fun setError(message: String) = update { it.copy(lastError = message) }
    fun setAck(message: String) = update { it.copy(lastAck = message, lastMessage = message) }
    fun setDemo(enabled: Boolean) = update { it.copy(demoMode = enabled) }
    fun updateDemo(state: String) {
        if (!current.demoMode) return
        val telemetry = when (state) {
            ArduinoTelemetry.SAFE -> ArduinoTelemetry(ArduinoTelemetry.SAFE, false, 200, false, false, true, "DEMO")
            ArduinoTelemetry.WARNING -> ArduinoTelemetry(ArduinoTelemetry.WARNING, false, 100, true, false, true, "DEMO")
            ArduinoTelemetry.CRITICAL -> ArduinoTelemetry(ArduinoTelemetry.CRITICAL, true, 80, true, true, true, "DEMO")
            "SENSOR_ERROR" -> ArduinoTelemetry(ArduinoTelemetry.WARNING, false, 999, true, false, false, "DEMO_SENSOR_ERROR")
            else -> { setBluetooth(BluetoothConnectionState.DISCONNECTED); return }
        }
        acceptTelemetry(telemetry, "Modo demostración: $state")
    }
    private fun update(transform: (Snapshot) -> Snapshot) { current = transform(current); observers.forEach { it(current) } }

    const val ROLE_TUTOR = "tutor"
    const val ROLE_ADMIN = "admin"
    const val DOOR_KITCHEN = "puerta-cocina"
    const val DOOR_ENTRY = "puerta-entrada"
    const val DOOR_BATH = "puerta-bano"
    private const val KEY_SELECTED_DOOR = "selectedDoorId"
}

class GuardianBluetoothListener : BluetoothService.Listener {
    override fun onConnectionStateChanged(state: BluetoothConnectionState) = GuardianSessionRepository.setBluetooth(state)
    override fun onJsonMessage(json: String) { /* Valid telemetry is published by onTelemetry. */ }
    override fun onAcknowledgement(message: String) = GuardianSessionRepository.setAck(message)
    override fun onProtocolError(message: String) = GuardianSessionRepository.invalidMessage(message)
    override fun onTelemetry(telemetry: ArduinoTelemetry) = GuardianSessionRepository.acceptTelemetry(telemetry)
}

object BluetoothConnectionManager {
    @Volatile private var service: BluetoothService? = null
    fun get(context: Context): BluetoothService = service ?: synchronized(this) {
        service ?: BluetoothService(context.applicationContext, GuardianBluetoothListener()).also { service = it }
    }
    fun connected() = service?.isConnected == true
    fun pairedHC05(): BluetoothDevice? = service?.findPairedHC05()
    fun close() { service?.close(); service = null; GuardianSessionRepository.setBluetooth(BluetoothConnectionState.DISCONNECTED) }
}

fun formatGuardianTime(timestamp: Long): String = if (timestamp <= 0L) "Sin lectura previa" else DateFormat.getDateTimeInstance().format(Date(timestamp))
