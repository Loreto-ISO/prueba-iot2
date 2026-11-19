package com.example.myapplication

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.data.FirebaseDatabaseRepository
import com.example.myapplication.model.DeviceCommand
import com.google.firebase.auth.FirebaseAuth

class ControlActivity : AppCompatActivity() {
    private val databaseRepository = FirebaseDatabaseRepository()
    private val deviceId = "casa-01"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_control)

        if (FirebaseAuth.getInstance().currentUser == null) {
            Toast.makeText(this, "Inicia sesión para controlar el dispositivo", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        findViewById<Button>(R.id.btnLedOn).setOnClickListener {
            sendCommand(DeviceCommand.SET_LED, true, "Encendido del LED solicitado")
        }
        findViewById<Button>(R.id.btnLedOff).setOnClickListener {
            sendCommand(DeviceCommand.SET_LED, false, "Apagado del LED solicitado")
        }
        findViewById<Button>(R.id.btnBuzzerOn).setOnClickListener {
            sendCommand(DeviceCommand.SET_BUZZER, true, "Activación del buzzer solicitada")
        }
        findViewById<Button>(R.id.btnBuzzerOff).setOnClickListener {
            sendCommand(DeviceCommand.SET_BUZZER, false, "Desactivación del buzzer solicitada")
        }
    }

    private fun sendCommand(type: String, value: Boolean, confirmation: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId.isNullOrBlank()) {
            Toast.makeText(this, "Sesión no válida. Inicia sesión nuevamente.", Toast.LENGTH_LONG).show()
            return
        }

        databaseRepository.sendCommand(deviceId, type, value, userId)
            .addOnSuccessListener {
                Toast.makeText(this, "$confirmation. Esperando respuesta del dispositivo.", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { error ->
                Toast.makeText(
                    this,
                    "No se pudo enviar el comando: ${error.localizedMessage ?: "error de conexión"}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
}
