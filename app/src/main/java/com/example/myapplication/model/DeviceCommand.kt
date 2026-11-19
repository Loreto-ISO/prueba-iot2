package com.example.myapplication.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class DeviceCommand(
    var commandId: String = "",
    var deviceId: String = "",
    var type: String = "",
    var value: Boolean = false,
    var issuedBy: String = "",
    var createdAt: Long = 0L,
    var status: String = "PENDING"
) {
    companion object {
        const val SET_LED = "SET_LED"
        const val SET_BUZZER = "SET_BUZZER"
    }
}
