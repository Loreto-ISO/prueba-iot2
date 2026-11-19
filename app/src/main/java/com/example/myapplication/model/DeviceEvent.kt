package com.example.myapplication.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class DeviceEvent(
    var eventId: String = "",
    var deviceId: String = "",
    var type: String = "",
    var message: String = "",
    var createdAt: Long = 0L,
    var createdBy: String = ""
)
