package com.example.myapplication

/** Parsed from the Arduino line protocol. The LED/buzzer values are derived until firmware reports them. */
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
        private const val SENSOR_NO_READING = 999

        /** Pure Kotlin parser so validation can run in local JVM unit tests. */
        fun parse(json: String): ArduinoTelemetry {
            require(json.length <= BluetoothService.MAX_MESSAGE_LENGTH) { "Mensaje excede el máximo permitido" }
            val trimmed = json.trim()
            require(trimmed.startsWith('{') && trimmed.endsWith('}')) { "JSON incompleto" }
            val body = trimmed.substring(1, trimmed.length - 1).trim()
            require(body.isNotEmpty()) { "JSON vacío" }

            val fields = linkedMapOf<String, String>()
            body.split(',').forEach { entry ->
                val pair = entry.split(':', limit = 2)
                require(pair.size == 2) { "Campo JSON malformado" }
                val key = pair[0].trim()
                require(key.length >= 2 && key.first() == '"' && key.last() == '"') { "Nombre de campo inválido" }
                val name = key.substring(1, key.length - 1)
                require(name.matches(Regex("[A-Za-z][A-Za-z0-9_]*"))) { "Nombre de campo inválido" }
                require(fields.put(name, pair[1].trim()) == null) { "Campo JSON duplicado" }
            }
            require(fields.keys == setOf("estado", "puertaAbierta", "distanciaCm")) { "JSON incompleto o con campos desconocidos" }

            val stateValue = fields.getValue("estado")
            require(stateValue.length >= 2 && stateValue.first() == '"' && stateValue.last() == '"') { "estado debe ser texto" }
            val estado = stateValue.substring(1, stateValue.length - 1)
            require(estado in setOf(SAFE, WARNING, CRITICAL)) { "Estado no permitido" }

            val doorValue = fields.getValue("puertaAbierta")
            require(doorValue == "true" || doorValue == "false") { "puertaAbierta debe ser booleano" }
            val puertaAbierta = doorValue == "true"

            val distanceValue = fields.getValue("distanciaCm")
            require(distanceValue.matches(Regex("-?\\d+"))) { "distanciaCm debe ser entero" }
            val distanciaCm = distanceValue.toIntOrNull() ?: throw IllegalArgumentException("distanciaCm inválida")
            require(distanciaCm >= 0 && (distanciaCm <= MAX_DISTANCE_CM || distanciaCm == SENSOR_NO_READING)) {
                "distanciaCm fuera de rango"
            }

            val sensorValido = distanciaCm in 1..MAX_DISTANCE_CM
            if (sensorValido) {
                require((estado == CRITICAL) == puertaAbierta) { "estado inconsistente con puertaAbierta" }
                require(estado != WARNING || distanciaCm in 1..150) { "distancia inconsistente con ADVERTENCIA" }
                require(estado != SAFE || distanciaCm > 150) { "distancia inconsistente con SEGURO" }
            }
            return ArduinoTelemetry(
                estado = estado,
                puertaAbierta = puertaAbierta,
                distanciaCm = distanciaCm,
                ledOn = estado != SAFE,
                buzzerOn = estado == CRITICAL,
                sensorValido = sensorValido,
                rawJson = json
            )
        }
    }
}
