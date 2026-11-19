package com.example.myapplication

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.FirebaseDatabaseRepository
import com.example.myapplication.model.DeviceEvent
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class HistoryActivity : AppCompatActivity() {
    private val repository = FirebaseDatabaseRepository()
    private val deviceId = "casa-01"
    private var eventsListener: ValueEventListener? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyMessage: TextView
    private val eventAdapter = DeviceEventAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        recyclerView = findViewById(R.id.rvEvents)
        emptyMessage = findViewById(R.id.tvEmptyHistory)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = eventAdapter

        observeEvents()
    }

    private fun observeEvents() {
        eventsListener = repository.observeDeviceEvents(
            deviceId = deviceId,
            onEvents = { events ->
                runOnUiThread {
                    eventAdapter.submitList(events)
                    emptyMessage.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
                    recyclerView.visibility = if (events.isEmpty()) View.GONE else View.VISIBLE
                }
            },
            onError = { error ->
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "No se pudo cargar el historial: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    override fun onDestroy() {
        eventsListener?.let { repository.removeDeviceEventsListener(deviceId, it) }
        eventsListener = null
        super.onDestroy()
    }
}
