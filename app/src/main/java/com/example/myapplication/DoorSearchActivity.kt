package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

class DoorSearchActivity : AppCompatActivity() {
    private lateinit var doors: List<GuardianSessionRepository.Door>
    private var doorsFiltered: List<GuardianSessionRepository.Door> = emptyList()
    private lateinit var list: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private var admin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GuardianSessionRepository.initialize(this)
        setContentView(R.layout.activity_door_search)
        admin = GuardianSessionRepository.snapshot.role == GuardianSessionRepository.ROLE_ADMIN
        doors = GuardianSessionRepository.doors
        doorsFiltered = doors
        list = findViewById(R.id.doorList)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels(doorsFiltered))
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            val selected = doorsFiltered.getOrNull(position) ?: return@setOnItemClickListener
            GuardianSessionRepository.selectDoor(selected.id)
            val target = if (admin) AdminHomeActivity::class.java else TutorHomeActivity::class.java
            startActivity(Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        }
        findViewById<EditText>(R.id.etDoorSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty()
                doorsFiltered = if (query.isBlank()) doors else doors.filter { door ->
                    door.id.contains(query, true) || door.name.contains(query, true) || door.location.contains(query, true)
                }
                adapter.clear()
                adapter.addAll(labels(doorsFiltered))
                adapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        findViewById<Button>(R.id.btnDoorSearchBack).setOnClickListener { finish() }
    }

    private fun labels(values: List<GuardianSessionRepository.Door>) = values.map { door ->
        val status = if (door.connected && BluetoothConnectionManager.connected()) getString(R.string.device_configured) else getString(R.string.no_device)
        "${door.name} — ${door.location}\n$status · ${getString(R.string.no_data)}"
    }
}
