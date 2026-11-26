package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArduinoTelemetryTest {
    @Test fun parsesSafeState() {
        val value = ArduinoTelemetry.parse("{\"estado\":\"SEGURO\",\"puertaAbierta\":false,\"distanciaCm\":250}")
        assertEquals(ArduinoTelemetry.SAFE, value.estado); assertFalse(value.puertaAbierta); assertFalse(value.ledOn); assertFalse(value.buzzerOn); assertTrue(value.sensorValido)
    }
    @Test fun parsesWarningState() {
        val value = ArduinoTelemetry.parse("{\"estado\":\"ADVERTENCIA\",\"puertaAbierta\":false,\"distanciaCm\":150}")
        assertTrue(value.ledOn); assertFalse(value.buzzerOn); assertTrue(value.sensorValido)
    }
    @Test fun parsesCriticalState() {
        val value = ArduinoTelemetry.parse("{\"estado\":\"ALERTA_CRITICA\",\"puertaAbierta\":true,\"distanciaCm\":50}")
        assertTrue(value.ledOn); assertTrue(value.buzzerOn)
    }
    @Test fun sensorTimeout999IsInvalidReadingNotSafeMeasurement() {
        val value = ArduinoTelemetry.parse("{\"estado\":\"SEGURO\",\"puertaAbierta\":false,\"distanciaCm\":999}")
        assertFalse(value.sensorValido); assertEquals(999, value.distanciaCm)
    }
    @Test fun rejectsMalformedJson() { expectFailure { ArduinoTelemetry.parse("{broken}") } }
    @Test fun rejectsUnknownState() { expectFailure { ArduinoTelemetry.parse("{\"estado\":\"SAFE\",\"puertaAbierta\":false,\"distanciaCm\":250}") } }
    @Test fun rejectsNegativeDistance() { expectFailure { ArduinoTelemetry.parse("{\"estado\":\"SEGURO\",\"puertaAbierta\":false,\"distanciaCm\":-1}") } }
    private fun expectFailure(block: () -> Unit) { var failed = false; try { block() } catch (_: IllegalArgumentException) { failed = true }; assertTrue("Expected invalid telemetry to be rejected", failed) }
}
