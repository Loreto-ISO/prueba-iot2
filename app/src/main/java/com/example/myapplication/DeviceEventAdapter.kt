package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.model.DeviceEvent
import java.text.DateFormat
import java.util.Date

class DeviceEventAdapter : RecyclerView.Adapter<DeviceEventAdapter.EventViewHolder>() {
    private val events = mutableListOf<DeviceEvent>()

    fun submitList(newEvents: List<DeviceEvent>) {
        events.clear()
        events.addAll(newEvents)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(events[position])
    }

    override fun getItemCount(): Int = events.size

    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val eventType: TextView = itemView.findViewById(R.id.tvEventType)
        private val eventMessage: TextView = itemView.findViewById(R.id.tvEventMessage)
        private val eventTimestamp: TextView = itemView.findViewById(R.id.tvEventTimestamp)

        fun bind(event: DeviceEvent) {
            eventType.text = event.type.ifBlank { "Evento del dispositivo" }
            eventMessage.text = event.message.ifBlank { "Sin descripción" }
            eventTimestamp.text = if (event.createdAt > 0L) {
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(event.createdAt))
            } else {
                "Fecha no disponible"
            }
        }
    }
}
