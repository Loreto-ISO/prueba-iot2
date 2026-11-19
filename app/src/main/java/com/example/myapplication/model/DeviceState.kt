package com.example.myapplication.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class DeviceState(
    var deviceId: String = "",
    var distanceCm: Double = 0.0,
    var doorOpen: Boolean = false,
    var ledOn: Boolean = false,
    var buzzerOn: Boolean = false,
    var wifiConnected: Boolean = false,
    var updatedAt: Long = 0L
)
