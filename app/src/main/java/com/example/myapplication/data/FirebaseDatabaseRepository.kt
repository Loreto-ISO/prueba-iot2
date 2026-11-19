package com.example.myapplication.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.example.myapplication.model.DeviceCommand
import com.example.myapplication.model.DeviceEvent
import com.example.myapplication.model.DeviceState

class FirebaseDatabaseRepository(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    private fun deviceRoot(deviceId: String) = database.reference.child("devices").child(deviceId)

    fun observeDeviceState(deviceId: String, onState: (DeviceState?) -> Unit, onError: (DatabaseError) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onState(snapshot.getValue(DeviceState::class.java)?.also { it.deviceId = deviceId })
            }

            override fun onCancelled(error: DatabaseError) = onError(error)
        }
        deviceRoot(deviceId).child("state").addValueEventListener(listener)
        return listener
    }

    fun removeStateListener(deviceId: String, listener: ValueEventListener) {
        deviceRoot(deviceId).child("state").removeEventListener(listener)
    }

    fun observeDeviceEvents(
        deviceId: String,
        onEvents: (List<DeviceEvent>) -> Unit,
        onError: (DatabaseError) -> Unit
    ): ValueEventListener {
        val eventsQuery = deviceRoot(deviceId).child("events").orderByChild("createdAt").limitToLast(100)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val events = snapshot.children.mapNotNull { child ->
                    child.getValue(DeviceEvent::class.java)?.also { event ->
                        if (event.eventId.isBlank()) event.eventId = child.key.orEmpty()
                        if (event.deviceId.isBlank()) event.deviceId = deviceId
                    }
                }.sortedByDescending { it.createdAt }
                onEvents(events)
            }

            override fun onCancelled(error: DatabaseError) = onError(error)
        }
        eventsQuery.addValueEventListener(listener)
        return listener
    }

    fun removeDeviceEventsListener(deviceId: String, listener: ValueEventListener) {
        deviceRoot(deviceId).child("events").orderByChild("createdAt").limitToLast(100)
            .removeEventListener(listener)
    }

    fun sendCommand(deviceId: String, type: String, value: Boolean, userId: String) =
        deviceRoot(deviceId).child("commands").push().let { reference ->
            val command = DeviceCommand(
                commandId = reference.key.orEmpty(),
                deviceId = deviceId,
                type = type,
                value = value,
                issuedBy = userId,
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            reference.setValue(command)
        }

    fun appendEvent(deviceId: String, type: String, message: String, userId: String) =
        deviceRoot(deviceId).child("events").push().let { reference ->
            reference.setValue(
                DeviceEvent(
                    eventId = reference.key.orEmpty(),
                    deviceId = deviceId,
                    type = type,
                    message = message,
                    createdAt = System.currentTimeMillis(),
                    createdBy = userId
                )
            )
        }
}
