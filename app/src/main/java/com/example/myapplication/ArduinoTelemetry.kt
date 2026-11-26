package com.example.myapplication

data class ArduinoTelemetry(
    val estado: String,
    val puertaAbierta: Boolean,
    val distanciaCm: Int,
    val ledOn: Boolean,
    val buzzerOn: Boolean,
    val sensorValido: Boolean,
    val rawJson: String = ""
) {
    companion object {
        const val SAFE = "SEGURO"
        const val WARNING = "ADVERTENCIA"
        const val CRITICAL = "ALERTA_CRITICA"
        const val MAX_DISTANCE_CM = 400

        fun parse(json: String): ArduinoTelemetry {
            require(json.length <= BluetoothService.MAX_MESSAGE_LENGTH) { "Mensaje excede el máximo permitido" }
            val objectValue = org.json.JSONObject(json)
            val estado = objectValue.getString("estado")
            require(estado in setOf(SAFE, WARNING, CRITICAL)) { "Estado no permitido" }
            require(objectValue.has("puertaAbierta") && objectValue.get("puertaAbierta") is Boolean) {
                "puertaAbierta debe ser booleano"
            }
            val distanceValue = objectValue.get("distanciaCm")
            require(distanceValue is Number) { "distanciaCm debe ser numérico" }
            val distance = distanceValue.toDouble()
            require(distance.isFinite() && distance % 1.0 == 0.0 && distance >= 0.0 && distance <= MAX_DISTANCE_CM.toDouble()) {
                "distanciaCm fuera de rango"
            }
            val puertaAbierta = objectValue.getBoolean("puertaAbierta")
            require((estado == CRITICAL) == puertaAbierta) { "estado inconsistente con puertaAbierta" }
            require(estado != WARNING || distance in 1.0..150.0) { "distancia inconsistente con ADVERTENCIA" }
            require(estado != SAFE || distance > 150.0) { "distancia inconsistente con SEGURO" }
            return ArduinoTelemetry(
                estado = estado,
                puertaAbierta = puertaAbierta,
                distanciaCm = distance.toInt(),
                ledOn = estado != SAFE,
                buzzerOn = estado == CRITICAL,
                sensorValido = distance in 1.0..MAX_DISTANCE_CM.toDouble() && distance != 999.0,
                rawJson = json
            )
        }
    }
}
