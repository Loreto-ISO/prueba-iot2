package com.example.myapplication

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class BluetoothConnectionActivity : AppCompatActivity(), BluetoothService.Listener {
    private lateinit var service: BluetoothService
    private lateinit var stateView: TextView
    private lateinit var messageView: TextView
    private lateinit var list: ListView
    private lateinit var connect: Button
    private var devices: List<android.bluetooth.BluetoothDevice> = emptyList()
    private var selected: android.bluetooth.BluetoothDevice? = null
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) loadDevices() else showError(getString(R.string.bluetooth_permission)) }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); GuardianSessionRepository.initialize(this); setContentView(R.layout.activity_bluetooth_connection); stateView = findViewById(R.id.tvBluetoothConnectionState); messageView = findViewById(R.id.tvBluetoothMessages); list = findViewById(R.id.lvPairedBluetoothDevices); connect = findViewById(R.id.btnBluetoothConnect); service = BluetoothConnectionManager.get(this); service.addListener(this); list.setOnItemClickListener { _, _, position, _ -> selected = devices.getOrNull(position); updateButtons() }; connect.setOnClickListener { selected?.let { service.connect(it) } }; findViewById<Button>(R.id.btnBluetoothDisconnect).setOnClickListener { service.disconnect() }; if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.BLUETOOTH_CONNECT) else loadDevices(); updateState(service.state) }
    private fun loadDevices() { try { devices = service.getPairedDevices(); list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, devices.map { "${it.name ?: getString(R.string.unknown_device)}\n${maskAddress(it.address)}" }); selected = devices.firstOrNull { it.name.equals(BluetoothService.HC05_DEVICE_NAME, true) }; selected?.let { list.setItemChecked(devices.indexOf(it), true) }; if (devices.isEmpty()) showError(getString(R.string.no_paired_devices)); updateButtons() } catch (e: SecurityException) { showError(e.message ?: getString(R.string.bluetooth_permission)) } }
    private fun maskAddress(address: String): String = if (GuardianSessionRepository.snapshot.role == GuardianSessionRepository.ROLE_ADMIN) address else address.take(5) + "**:**:**"
    private fun updateButtons() { connect.isEnabled = selected?.name.equals(BluetoothService.HC05_DEVICE_NAME, true) && service.state != BluetoothConnectionState.CONNECTED && service.state != BluetoothConnectionState.CONNECTING }
    private fun updateState(state: BluetoothConnectionState) { stateView.text = getString(R.string.bluetooth_state, state.name); updateButtons() }
    private fun showError(message: String) { messageView.text = message; messageView.visibility = View.VISIBLE; GuardianSessionRepository.setError(message) }
    override fun onConnectionStateChanged(state: BluetoothConnectionState) { runOnUiThread { updateState(state) } }
    override fun onJsonMessage(json: String) { runOnUiThread { messageView.text = getString(R.string.last_valid_message, json); messageView.visibility = View.VISIBLE } }
    override fun onAcknowledgement(message: String) { runOnUiThread { messageView.text = message; messageView.visibility = View.VISIBLE } }
    override fun onProtocolError(message: String) { runOnUiThread { showError(message) } }
    override fun onTelemetry(telemetry: ArduinoTelemetry) {}
    override fun onDestroy() { if (::service.isInitialized) service.removeListener(this); super.onDestroy() }
}
