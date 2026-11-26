package com.example.myapplication

data class ArduinoTelemetry(
    val estado: String,
    val puertaAbierta: Boolean,
    val distanciaCm: Int,
    val ledOn: Boolean,
    val buzzerOn: Boolean,
    val sensorValido: Boolean
)
