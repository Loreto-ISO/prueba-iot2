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

class BluetoothConnectionActivity :
    AppCompatActivity(),
    BluetoothService.Listener {

    private lateinit var service: BluetoothService
    private lateinit var stateView: TextView
    private lateinit var messageView: TextView
    private lateinit var deviceList: ListView
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button

    private var devices: List<BluetoothDevice> = emptyList()
    private var selectedDevice: BluetoothDevice? = null

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                loadDevices()
            } else {
                showError(
                    getString(R.string.bluetooth_permission)
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        GuardianSessionRepository.initialize(this)

        setContentView(
            R.layout.activity_bluetooth_connection
        )

        stateView =
            findViewById(
                R.id.tvBluetoothConnectionState
            )

        messageView =
            findViewById(
                R.id.tvBluetoothMessages
            )

        deviceList =
            findViewById(
                R.id.lvPairedBluetoothDevices
            )

        connectButton =
            findViewById(
                R.id.btnBluetoothConnect
            )

        disconnectButton =
            findViewById(
                R.id.btnBluetoothDisconnect
            )

        service =
            BluetoothConnectionManager.get(this)

        service.addListener(this)

        configureDeviceList()
        configureButtons()
        requestPermissionOrLoadDevices()
        updateState(service.state)
    }

    private fun configureDeviceList() {
        deviceList.setOnItemClickListener {
                _,
                _,
                position,
                _ ->

            selectedDevice =
                devices.getOrNull(position)

            updateButtons()
        }
    }

    private fun configureButtons() {
        connectButton.setOnClickListener {
            val device = selectedDevice

            if (device == null) {
                showError(
                    getString(
                        R.string.hc05_not_paired
                    )
                )

                return@setOnClickListener
            }

            val deviceName = getDeviceName(device)

            if (
                !deviceName.equals(
                    BluetoothService.HC05_DEVICE_NAME,
                    ignoreCase = true
                )
            ) {
                showError(
                    getString(
                        R.string.hc05_not_paired
                    )
                )

                return@setOnClickListener
            }

            messageView.visibility = View.GONE

            service.connect(device)
        }

        disconnectButton.setOnClickListener {
            service.disconnect()
        }
    }

    private fun requestPermissionOrLoadDevices() {
        if (hasBluetoothConnectPermission()) {
            loadDevices()
        } else {
            permissionLauncher.launch(
                Manifest.permission.BLUETOOTH_CONNECT
            )
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }

        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun loadDevices() {
        if (!hasBluetoothConnectPermission()) {
            permissionLauncher.launch(
                Manifest.permission.BLUETOOTH_CONNECT
            )

            return
        }

        if (!service.isBluetoothAvailable) {
            showError(
                getString(
                    R.string.bluetooth_not_available
                )
            )

            return
        }

        if (!service.isBluetoothEnabled()) {
            showError(
                getString(
                    R.string.bluetooth_disabled
                )
            )

            return
        }

        try {
            devices = service.getPairedDevices()

            val labels = devices.map { device ->
                val name = getDeviceName(device)
                    .ifBlank {
                        getString(
                            R.string.unknown_device
                        )
                    }

                val address = getDeviceAddress(device)

                "$name\n${maskAddress(address)}"
            }

            deviceList.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_list_item_single_choice,
                labels
            )

            selectedDevice =
                devices.firstOrNull { device ->
                    getDeviceName(device).equals(
                        BluetoothService.HC05_DEVICE_NAME,
                        ignoreCase = true
                    )
                }

            selectedDevice?.let { device ->
                val index = devices.indexOf(device)

                if (index >= 0) {
                    deviceList.setItemChecked(
                        index,
                        true
                    )
                }
            }

            if (devices.isEmpty()) {
                showError(
                    getString(
                        R.string.no_paired_devices
                    )
                )
            } else if (selectedDevice == null) {
                showError(
                    getString(
                        R.string.hc05_not_paired
                    )
                )
            } else {
                showMessage(
                    getString(
                        R.string.hc05_ready
                    )
                )
            }

            updateButtons()
        } catch (error: SecurityException) {
            showError(
                error.localizedMessage
                    ?: getString(
                        R.string.bluetooth_permission
                    )
            )
        }
    }

    private fun getDeviceName(
        device: BluetoothDevice
    ): String {
        return try {
            device.name.orEmpty()
        } catch (_: SecurityException) {
            ""
        }
    }

    private fun getDeviceAddress(
        device: BluetoothDevice
    ): String {
        return try {
            device.address.orEmpty()
        } catch (_: SecurityException) {
            ""
        }
    }

    private fun maskAddress(
        address: String
    ): String {
        if (
            GuardianSessionRepository
                .snapshot
                .role ==
            GuardianSessionRepository.ROLE_ADMIN
        ) {
            return address
        }

        if (address.length < 5) {
            return getString(
                R.string.address_hidden
            )
        }

        return address.take(5) + ":**:**:**:**"
    }

    private fun updateButtons() {
        val validHC05Selected =
            selectedDevice?.let { device ->
                getDeviceName(device).equals(
                    BluetoothService.HC05_DEVICE_NAME,
                    ignoreCase = true
                )
            } == true

        connectButton.isEnabled =
            validHC05Selected &&
                    service.state !=
                    BluetoothConnectionState.CONNECTED &&
                    service.state !=
                    BluetoothConnectionState.CONNECTING &&
                    hasBluetoothConnectPermission()

        disconnectButton.isEnabled =
            service.state ==
                    BluetoothConnectionState.CONNECTED ||
                    service.state ==
                    BluetoothConnectionState.CONNECTING
    }

    private fun updateState(
        state: BluetoothConnectionState
    ) {
        stateView.text =
            getString(
                R.string.bluetooth_state,
                state.name
            )

        updateButtons()
    }

    private fun showError(
        message: String
    ) {
        messageView.text = message
        messageView.visibility = View.VISIBLE

        GuardianSessionRepository.setError(
            message
        )
    }

    private fun showMessage(
        message: String
    ) {
        messageView.text = message
        messageView.visibility = View.VISIBLE
    }

    override fun onConnectionStateChanged(
        state: BluetoothConnectionState
    ) {
        runOnUiThread {
            updateState(state)
        }
    }

    override fun onJsonMessage(
        json: String
    ) {
        runOnUiThread {
            showMessage(
                getString(
                    R.string.last_valid_message,
                    json
                )
            )
        }
    }

    override fun onAcknowledgement(
        message: String
    ) {
        runOnUiThread {
            showMessage(message)
        }
    }

    override fun onProtocolError(
        message: String
    ) {
        runOnUiThread {
            showError(message)
        }
    }

    override fun onTelemetry(
        telemetry: ArduinoTelemetry
    ) {
        // GuardianBluetoothListener actualiza
        // GuardianSessionRepository.
    }

    override fun onDestroy() {
        if (::service.isInitialized) {
            service.removeListener(this)
        }

        super.onDestroy()
    }
}