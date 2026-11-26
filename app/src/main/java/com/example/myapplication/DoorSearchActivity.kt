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
    private lateinit var list: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private var admin = false
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContentView(R.layout.activity_door_search); admin = GuardianSessionRepository.snapshot.role == GuardianSessionRepository.ROLE_ADMIN; doors = GuardianSessionRepository.doors; list = findViewById(R.id.doorList); adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels(doors)); list.adapter = adapter; list.setOnItemClickListener { _, _, position, _ -> val door = doorsFiltered[position]; GuardianSessionRepository.selectDoor(door.id); startActivity(Intent(this, if (admin) AdminHomeActivity::class.java else TutorHomeActivity::class.java)); finish() }; findViewById<EditText>(R.id.etDoorSearch).addTextChangedListener(object : TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {} override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { doorsFiltered = doors.filter { listOf(it.id,it.name,it.location).any { value -> value.contains(s.toString(), true) } }; adapter.clear(); adapter.addAll(labels(doorsFiltered)); adapter.notifyDataSetChanged() } override fun afterTextChanged(s: Editable?) {} }); findViewById<Button>(R.id.btnDoorSearchBack).setOnClickListener { finish() } }
    private var doorsFiltered: List<GuardianSessionRepository.Door> = emptyList()
    private fun labels(values: List<GuardianSessionRepository.Door>) = values.map { "${it.name} — ${it.location}\n${getString(if (it.connected) R.string.pending_configuration else R.string.no_device)} · ${getString(R.string.no_data)}" }
}
