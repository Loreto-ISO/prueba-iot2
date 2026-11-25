package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.FirebaseAuthRepository
import com.example.myapplication.model.DeviceEvent
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.DateFormat
import java.util.Date

class HistoryActivity : AppCompatActivity() {
    private val authRepository = FirebaseAuthRepository()
    private val eventsRef = FirebaseDatabase.getInstance()
        .reference.child("devices").child("casa-01").child("events")

    private var eventsListener: ValueEventListener? = null
    private val allEvents = mutableListOf<DeviceEvent>()
    private lateinit var adapter: EventAdapter
    private lateinit var tvEmpty: TextView
    private lateinit var etSearch: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (authRepository.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_history)

        tvEmpty = findViewById(R.id.tvHistoryEmpty)
        etSearch = findViewById(R.id.etSearchEvents)
        val rvEvents = findViewById<RecyclerView>(R.id.rvEvents)

        adapter = EventAdapter(emptyList())
        rvEvents.layoutManager = LinearLayoutManager(this)
        rvEvents.adapter = adapter

        setupSearchFilter()
        setupBottomNavigation()
        observeEvents()
    }

    private fun setupSearchFilter() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterEvents(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterEvents(query: String) {
        val cleanQuery = query.trim().lowercase()
        val filtered = if (cleanQuery.isEmpty()) {
            allEvents
        } else {
            allEvents.filter { event ->
                event.type.lowercase().contains(cleanQuery) ||
                        event.message.lowercase().contains(cleanQuery) ||
                        event.createdBy.lowercase().contains(cleanQuery)
            }
        }
        adapter.updateData(filtered)
        tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun observeEvents() {
        eventsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allEvents.clear()
                for (child in snapshot.children) {
                    val event = child.getValue(DeviceEvent::class.java)
                    if (event != null) {
                        allEvents.add(event)
                    }
                }
                allEvents.sortByDescending { it.createdAt }
                filterEvents(etSearch.text?.toString().orEmpty())
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@HistoryActivity,
                    "No se pudo cargar el historial: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        eventsRef.addValueEventListener(eventsListener!!)
    }

    private fun setupBottomNavigation() {
        val navPrincipal = findViewById<LinearLayout>(R.id.navPrincipal)
        val navPerfil = findViewById<LinearLayout>(R.id.navPerfil)

        navPrincipal.setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }

        navPerfil.setOnClickListener {
            startActivity(Intent(this, AdminActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }
    }

    override fun onDestroy() {
        eventsListener?.let { eventsRef.removeEventListener(it) }
        super.onDestroy()
    }

    private class EventAdapter(private var items: List<DeviceEvent>) :
        RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

        fun updateData(newItems: List<DeviceEvent>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_device_event, parent, false)
            return EventViewHolder(view)
        }

        override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvIcon: TextView = itemView.findViewById(R.id.tvEventIcon)
            private val tvTitle: TextView = itemView.findViewById(R.id.tvEventTitle)
            private val tvMessage: TextView = itemView.findViewById(R.id.tvEventMessage)
            private val tvMeta: TextView = itemView.findViewById(R.id.tvEventMeta)

            fun bind(event: DeviceEvent) {
                tvIcon.text = when {
                    event.type.contains("DOOR", ignoreCase = true) ||
                            event.type.contains("ALERT", ignoreCase = true) -> "🚨"
                    event.type.contains("LED", ignoreCase = true) -> "💡"
                    event.type.contains("BUZZER", ignoreCase = true) -> "🔔"
                    event.type.contains("PROXIMITY", ignoreCase = true) -> "📡"
                    else -> "🛡️"
                }

                tvTitle.text = formatEventTitle(event.type)
                tvMessage.text = event.message.ifBlank { "Registro de actividad del sistema" }

                val dateText = if (event.createdAt > 0) {
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(event.createdAt))
                } else {
                    "Reciente"
                }
                val author = event.createdBy.ifBlank { "Sistema IoT" }
                tvMeta.text = "$dateText • $author"
            }

            private fun formatEventTitle(rawType: String): String = when (rawType.uppercase()) {
                "LED_ON" -> "Luz preventiva encendida"
                "LED_OFF" -> "Luz preventiva apagada"
                "BUZZER_ON" -> "Alarma sonora activada"
                "BUZZER_OFF" -> "Alarma sonora silenciada"
                "DOOR_OPEN" -> "Alerta: Puerta abierta"
                "PROXIMITY_ALERT" -> "Advertencia de proximidad"
                else -> rawType.ifBlank { "Evento del sistema" }
            }
        }
    }
}