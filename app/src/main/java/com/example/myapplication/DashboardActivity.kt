package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val btnSimularProximidad = findViewById<Button>(R.id.btnSimularProximidad)
        val btnSimularApertura = findViewById<Button>(R.id.btnSimularApertura)
        val btnSimularCierre = findViewById<Button>(R.id.btnSimularCierre)

        // Simula lo que pasaría si el sensor ultrasónico detecta movimiento
        btnSimularProximidad.setOnClickListener {
            tvStatus.text = "Precaución: Movimiento detectado cerca de la puerta"
            tvStatus.setTextColor(Color.parseColor("#FF9800")) // Naranja
        }

        // Simula lo que pasaría si se presiona/suelta el botón de la puerta
        btnSimularApertura.setOnClickListener {
            tvStatus.text = "ALERTA CRÍTICA: ¡Puerta Abierta!"
            tvStatus.setTextColor(Color.parseColor("#FF0000")) // Rojo
        }

        // Restablece el estado seguro
        btnSimularCierre.setOnClickListener {
            tvStatus.text = "Estado: Puerta Cerrada y Segura"
            tvStatus.setTextColor(Color.parseColor("#009900")) // Verde
        }
    }
}