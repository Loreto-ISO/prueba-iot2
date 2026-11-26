package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.data.FirebaseAuthRepository
import com.example.myapplication.data.FirebaseDatabaseRepository
import com.example.myapplication.model.DeviceState
import com.google.firebase.database.ValueEventListener
import java.text.DateFormat
import java.util.Date

class DashboardActivity : AppCompatActivity() {
    private val authRepository = FirebaseAuthRepository()
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

        if (authRepository.currentUser == null) {
            goToLogin()
            return
        }

        setContentView(R.layout.activity_dashboard)
        addBluetoothConnectionButton()

        tvStatus = findViewById(R.id.tvStatus)
        tvDistance = findViewById(R.id.tvDistance)
        tvDoor = findViewById(R.id.tvDoor)
        tvLed = findViewById(R.id.tvLed)
        tvBuzzer = findViewById(R.id.tvBuzzer)
        tvWifi = findViewById(R.id.tvWifi)
        tvUpdatedAt = findViewById(R.id.tvUpdatedAt)

        setupControlButtons()
        setupBottomNavigation()
        observeDeviceState()
    }

    private fun addBluetoothConnectionButton() {
        val content = findViewById<LinearLayout>(android.R.id.content)
        val root = content.getChildAt(0) as? LinearLayout ?: return
        val button = Button(this).apply {
            text = "Conexión Bluetooth HC-05"
            setOnClickListener {
                startActivity(Intent(this@DashboardActivity, BluetoothConnectionActivity::class.java))
            }
        }
        root.addView(
            button,
            1,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun setupControlButtons() {
        val btnLedOn = findViewById<Button>(R.id.btnLedOn)
        val btnLedOff = findViewById<Button>(R.id.btnLedOff)
        val btnBuzzerOn = findViewById<Button>(R.id.btnBuzzerOn)
        val btnBuzzerOff = findViewById<Button>(R.id.btnBuzzerOff)

        btnLedOn.setOnClickListener {
            sendActuatorCommand(
                type = "SET_LED",
                value = true,
                eventTitle = "LED_ON",
                eventMessage = "Luz preventiva encendida remotamente desde la app",
                toastMessage = "Comando enviado: Encender LED"
            )
        }

        btnLedOff.setOnClickListener {
            sendActuatorCommand(
                type = "SET_LED",
                value = false,
                eventTitle = "LED_OFF",
                eventMessage = "Luz preventiva apagada remotamente desde la app",
                toastMessage = "Comando enviado: Apagar LED"
            )
        }

        btnBuzzerOn.setOnClickListener {
            sendActuatorCommand(
                type = "SET_BUZZER",
                value = true,
                eventTitle = "BUZZER_ON",
                eventMessage = "Alarma sonora activada manualmente desde la app",
                toastMessage = "Comando enviado: Activar Buzzer"
            )
        }

        btnBuzzerOff.setOnClickListener {
            sendActuatorCommand(
                type = "SET_BUZZER",
                value = false,
                eventTitle = "BUZZER_OFF",
                eventMessage = "Alarma sonora silenciada desde la app",
                toastMessage = "Comando enviado: Silenciar Buzzer"
            )
        }
    }

    private fun sendActuatorCommand(
        type: String,
        value: Boolean,
        eventTitle: String,
        eventMessage: String,
        toastMessage: String
    ) {
        val user = authRepository.currentUser
        val userIdentifier = user?.email ?: user?.uid ?: "usuario_autorizado"

        databaseRepository.sendCommand(deviceId, type, value, userIdentifier)
            .addOnSuccessListener {
                databaseRepository.appendEvent(deviceId, eventTitle, eventMessage, userIdentifier)
                Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { error ->
                Toast.makeText(
                    this,
                    "Error al enviar instrucción: ${error.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun setupBottomNavigation() {
        val navBuscar = findViewById<LinearLayout>(R.id.navBuscar)
        val navPerfil = findViewById<LinearLayout>(R.id.navPerfil)

        navBuscar.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }

        navPerfil.setOnClickListener {
            startActivity(Intent(this, AdminActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }
    }

    private fun observeDeviceState() {
        stateListener = databaseRepository.observeDeviceState(
            deviceId = deviceId,
            onState = { state ->
                runOnUiThread {
                    if (state == null) {
                        showUnavailableState()
                    } else {
                        renderState(state)
                    }
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
        tvDistance.text = if (state.distanceCm > 0) {
            "%.1f cm".format(state.distanceCm)
        } else {
            "Sin lectura"
        }

        tvDoor.text = if (state.doorOpen) "Abierta" else "Cerrada"
        tvDoor.setTextColor(if (state.doorOpen) COLOR_ALERT else COLOR_SAFE)

        tvLed.text = if (state.ledOn) "Encendido" else "Apagado"
        tvLed.setTextColor(if (state.ledOn) COLOR_WARNING else COLOR_SAFE)

        tvBuzzer.text = if (state.buzzerOn) "Activo" else "Inactivo"
        tvBuzzer.setTextColor(if (state.buzzerOn) COLOR_ALERT else COLOR_SAFE)

        tvWifi.text = if (state.wifiConnected) "● Conectado" else "● Desconectado"
        tvWifi.setTextColor(if (state.wifiConnected) COLOR_SAFE else COLOR_ALERT)

        val proximityDetected = state.distanceCm in 0.1..PROXIMITY_WARNING_CM

        tvStatus.text = when {
            !state.wifiConnected -> "🔴 Sin conexión con el dispositivo"
            state.doorOpen -> "🔴 ALERTA CRÍTICA: Puerta abierta"
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
        } else {
            "Última actualización: en espera de telemetría"
        }
    }

    private fun showUnavailableState() {
        tvDistance.text = "Sin lectura"
        tvDoor.text = "En espera"
        tvLed.text = "Apagado"
        tvBuzzer.text = "Inactivo"
        tvWifi.text = "● En espera"
        tvWifi.setTextColor(COLOR_WARNING)
        tvStatus.text = "🟡 Esperando conexión del nodo IoT"
        tvStatus.setTextColor(COLOR_WARNING)
        tvUpdatedAt.text = "Última actualización: pendiente"
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        stateListener?.let { databaseRepository.removeStateListener(deviceId, it) }
        stateListener = null
        super.onDestroy()
    }

    companion object {
        private const val PROXIMITY_WARNING_CM = 100.0
        private val COLOR_SAFE = Color.rgb(22, 163, 74)
        private val COLOR_WARNING = Color.rgb(217, 119, 6)
        private val COLOR_ALERT = Color.rgb(220, 38, 38)
    }
}
