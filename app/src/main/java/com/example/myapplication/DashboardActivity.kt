package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.data.FirebaseDatabaseRepository
import com.example.myapplication.model.DeviceState
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.text.DateFormat
import java.util.Date

class DashboardActivity : AppCompatActivity() {
    private val databaseRepository = FirebaseDatabaseRepository()
    private var stateListener: ValueEventListener? = null
    private val deviceId = "casa-01"

    private lateinit var tvStatus: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvDoor: TextView
    private lateinit var tvLed: TextView
    private lateinit var tvBuzzer: TextView
    private lateinit var tvWifi: TextView
    private lateinit var tvUpdatedAt: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        tvStatus = findViewById(R.id.tvStatus)
        tvDistance = findViewById(R.id.tvDistance)
        tvDoor = findViewById(R.id.tvDoor)
        tvLed = findViewById(R.id.tvLed)
        tvBuzzer = findViewById(R.id.tvBuzzer)
        tvWifi = findViewById(R.id.tvWifi)
        tvUpdatedAt = findViewById(R.id.tvUpdatedAt)

        findViewById<Button>(R.id.btnOpenControl).setOnClickListener {
            startActivity(Intent(this, ControlActivity::class.java))
        }

        observeDeviceState()
    }

    private fun observeDeviceState() {
        stateListener = databaseRepository.observeDeviceState(
            deviceId = deviceId,
            onState = { state ->
                runOnUiThread {
                    if (state == null) showUnavailableState() else renderState(state)
                }
            },
            onError = { error ->
                runOnUiThread {
                    showUnavailableState()
                    Toast.makeText(
                        this,
                        "No se pudo actualizar el dispositivo: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    private fun renderState(state: DeviceState) {
        tvDistance.text = if (state.distanceCm > 0) "%.1f cm".format(state.distanceCm) else "Sin lectura"
        tvDoor.text = if (state.doorOpen) "Puerta: Abierta" else "Puerta: Cerrada"
        tvDoor.setTextColor(if (state.doorOpen) COLOR_ALERT else COLOR_SAFE)
        tvLed.text = if (state.ledOn) "LED: Encendido" else "LED: Apagado"
        tvLed.setTextColor(if (state.ledOn) COLOR_WARNING else COLOR_SAFE)
        tvBuzzer.text = if (state.buzzerOn) "Buzzer: Activo" else "Buzzer: Inactivo"
        tvBuzzer.setTextColor(if (state.buzzerOn) COLOR_ALERT else COLOR_SAFE)
        tvWifi.text = if (state.wifiConnected) "WiFi: Conectado" else "WiFi: Desconectado"
        tvWifi.setTextColor(if (state.wifiConnected) COLOR_SAFE else COLOR_ALERT)

        val proximityDetected = state.distanceCm > 0 && state.distanceCm <= PROXIMITY_WARNING_CM
        tvStatus.text = when {
            !state.wifiConnected -> "🔴 Sin conexión con el dispositivo"
            state.doorOpen -> "🔴 ALERTA: Puerta abierta"
            proximityDetected -> "🟡 Advertencia: proximidad detectada"
            else -> "🟢 Seguro: sistema operativo"
        }
        tvStatus.setTextColor(
            when {
                !state.wifiConnected || state.doorOpen -> COLOR_ALERT
                proximityDetected -> COLOR_WARNING
                else -> COLOR_SAFE
            }
        )
        tvUpdatedAt.text = if (state.updatedAt > 0) {
            "Última actualización: ${DateFormat.getDateTimeInstance().format(Date(state.updatedAt))}"
        } else "Última actualización: pendiente"
    }

    private fun showUnavailableState() {
        tvDistance.text = "Sin lectura"
        tvDoor.text = "Puerta: No disponible"
        tvLed.text = "LED: No disponible"
        tvBuzzer.text = "Buzzer: No disponible"
        tvWifi.text = "WiFi: Desconectado"
        tvWifi.setTextColor(COLOR_ALERT)
        tvStatus.text = "🔴 No hay datos del dispositivo"
        tvStatus.setTextColor(COLOR_ALERT)
        tvUpdatedAt.text = "Última actualización: pendiente"
    }

    override fun onDestroy() {
        stateListener?.let { databaseRepository.removeStateListener(deviceId, it) }
        stateListener = null
        super.onDestroy()
    }

    companion object {
        private const val PROXIMITY_WARNING_CM = 100.0
        private const val COLOR_SAFE = Color.rgb(27, 94, 32)
        private const val COLOR_WARNING = Color.rgb(230, 126, 34)
        private const val COLOR_ALERT = Color.rgb(198, 40, 40)
    }
}
