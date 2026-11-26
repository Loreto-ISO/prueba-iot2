package com.example.myapplication

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class BluetoothConnectionActivity : AppCompatActivity(), BluetoothService.Listener {
    private lateinit var service: BluetoothService
    private lateinit var tvConnectionState: TextView
    private lateinit var tvMessages: TextView
    private lateinit var deviceList: ListView
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private var pairedDevices: List<BluetoothDevice> = emptyList()
    private var selectedDevice: BluetoothDevice? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            loadPairedDevices()
        } else {
            showError("Se requiere BLUETOOTH_CONNECT para ver dispositivos emparejados")
            renderConnectionState(BluetoothConnectionState.ERROR)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_connection)

        tvConnectionState = findViewById(R.id.tvBluetoothConnectionState)
        tvMessages = findViewById(R.id.tvBluetoothMessages)
        deviceList = findViewById(R.id.lvPairedBluetoothDevices)
        connectButton = findViewById(R.id.btnBluetoothConnect)
        disconnectButton = findViewById(R.id.btnBluetoothDisconnect)
        service = BluetoothService(applicationContext, this)

        deviceList.onItemClickListener = android.widget.AdapterView.OnItemClickListener { _, _, position, _ ->
            selectedDevice = pairedDevices.getOrNull(position)
            val selectedName = selectedDevice?.let { getDeviceName(it) }.orEmpty()
            if (selectedName.equals(BluetoothService.HC05_DEVICE_NAME, ignoreCase = true)) {
                showMessage("Seleccionado: $selectedName")
            } else {
                showError("Selecciona el dispositivo emparejado HC-05")
            }
            updateButtons(service.state)
        }

        connectButton.setOnClickListener { connectSelectedDevice() }
        disconnectButton.setOnClickListener { service.disconnect() }

        if (!service.isBluetoothAvailable) {
            showError("Este dispositivo no dispone de Bluetooth")
            renderConnectionState(BluetoothConnectionState.ERROR)
        } else if (hasConnectPermission()) {
            loadPairedDevices()
        } else {
            requestConnectPermission()
        }
    }

    private fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    private fun loadPairedDevices() {
        if (!hasConnectPermission()) {
            requestConnectPermission()
            return
        }
        if (!service.isBluetoothEnabled()) {
            showError("Bluetooth está desactivado; actívalo e intenta nuevamente")
            renderConnectionState(BluetoothConnectionState.DISCONNECTED)
            return
        }

        try {
            pairedDevices = service.getPairedDevices()
            selectedDevice = pairedDevices.firstOrNull {
                getDeviceName(it).equals(BluetoothService.HC05_DEVICE_NAME, ignoreCase = true)
            }
            val labels = pairedDevices.map { device ->
                "${getDeviceName(device).ifBlank { "Dispositivo sin nombre" }}\n${device.address}"
            }
            deviceList.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
            selectedDevice?.let { device ->
                val index = pairedDevices.indexOf(device)
                if (index >= 0) deviceList.setItemChecked(index, true)
                showMessage("HC-05 emparejado y listo para seleccionar")
            }
            if (pairedDevices.isEmpty()) {
                showError("No hay dispositivos Bluetooth emparejados. Empareja el HC-05 desde Ajustes.")
            }
            updateButtons(service.state)
        } catch (error: SecurityException) {
            showError(error.localizedMessage ?: "Permiso Bluetooth denegado")
            renderConnectionState(BluetoothConnectionState.ERROR)
        }
    }

    private fun connectSelectedDevice() {
        if (!hasConnectPermission()) {
            requestConnectPermission()
            return
        }
        val device = selectedDevice
        if (device == null || !getDeviceName(device).equals(BluetoothService.HC05_DEVICE_NAME, true)) {
            showError("Selecciona un dispositivo emparejado cuyo nombre sea HC-05")
            return
        }
        tvMessages.visibility = View.GONE
        service.connect(device)
    }

    private fun getDeviceName(device: BluetoothDevice): String = try {
        device.name.orEmpty()
    } catch (_: SecurityException) {
        ""
    }

    override fun onConnectionStateChanged(state: BluetoothConnectionState) {
        runOnUiThread { renderConnectionState(state) }
    }

    override fun onJsonMessage(json: String) {
        runOnUiThread { showMessage("JSON recibido: $json") }
    }

    override fun onAcknowledgement(message: String) {
        runOnUiThread { showMessage(message) }
    }

    override fun onProtocolError(message: String) {
        runOnUiThread { showError(message) }
    }

    private fun renderConnectionState(state: BluetoothConnectionState) {
        tvConnectionState.text = when (state) {
            BluetoothConnectionState.DISCONNECTED -> "Estado Bluetooth: DISCONNECTED"
            BluetoothConnectionState.CONNECTING -> "Estado Bluetooth: CONNECTING"
            BluetoothConnectionState.CONNECTED -> "Estado Bluetooth: CONNECTED"
            BluetoothConnectionState.ERROR -> "Estado Bluetooth: ERROR"
        }
        updateButtons(state)
    }

    private fun updateButtons(state: BluetoothConnectionState) {
        val isHC05Selected = selectedDevice?.let {
            getDeviceName(it).equals(BluetoothService.HC05_DEVICE_NAME, ignoreCase = true)
        } == true
        connectButton.isEnabled = state != BluetoothConnectionState.CONNECTING &&
            state != BluetoothConnectionState.CONNECTED && isHC05Selected && hasConnectPermission()
        disconnectButton.isEnabled = state == BluetoothConnectionState.CONNECTING ||
            state == BluetoothConnectionState.CONNECTED
    }

    private fun showError(message: String) {
        tvMessages.text = message
        tvMessages.visibility = View.VISIBLE
    }

    private fun showMessage(message: String) {
        tvMessages.text = message
        tvMessages.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        if (::service.isInitialized) service.close()
        super.onDestroy()
    }
}
