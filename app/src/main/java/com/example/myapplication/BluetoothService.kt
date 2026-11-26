package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class BluetoothService(context: Context, listener: Listener) {
    interface Listener {
        fun onConnectionStateChanged(state: BluetoothConnectionState)
        fun onJsonMessage(json: String)
        fun onAcknowledgement(message: String)
        fun onProtocolError(message: String)
        fun onTelemetry(telemetry: ArduinoTelemetry) {}
    }

    private val appContext = context.applicationContext
    private val adapter = (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val listeners = CopyOnWriteArraySet<Listener>().apply { add(listener) }
    private val connectExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val ioExecutor: ExecutorService = Executors.newCachedThreadPool()
    private val socketLock = Any()
    private val connectionAttempt = AtomicLong(0)

    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var outputStream: OutputStream? = null
    @Volatile var state = BluetoothConnectionState.DISCONNECTED
        private set
    val isBluetoothAvailable get() = adapter != null
    val isConnected get() = state == BluetoothConnectionState.CONNECTED

    fun addListener(listener: Listener) { listeners.add(listener) }
    fun removeListener(listener: Listener) { listeners.remove(listener) }
    fun isBluetoothEnabled(): Boolean = try { adapter?.isEnabled == true } catch (_: SecurityException) { false }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        checkPermission()
        return adapter?.bondedDevices?.toList().orEmpty()
    }

    fun findPairedHC05(): BluetoothDevice? = getPairedDevices().firstOrNull {
        try { it.name.equals(HC05_DEVICE_NAME, true) } catch (_: SecurityException) { false }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        try { checkPermission() } catch (e: SecurityException) { error(e.message ?: "Permiso Bluetooth denegado"); setState(BluetoothConnectionState.ERROR); return }
        if (adapter == null) { error("Este dispositivo no dispone de Bluetooth"); setState(BluetoothConnectionState.ERROR); return }
        val attempt = connectionAttempt.incrementAndGet()
        setState(BluetoothConnectionState.CONNECTING)
        connectExecutor.execute {
            closeSocket()
            var candidate: BluetoothSocket? = null
            try {
                if (!device.name.equals(HC05_DEVICE_NAME, true)) throw IOException("El dispositivo seleccionado no es HC-05")
                val newSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                candidate = newSocket
                synchronized(socketLock) {
                    if (attempt != connectionAttempt.get()) { newSocket.close(); return@execute }
                    socket = newSocket
                    outputStream = null
                }
                newSocket.connect()
                val input = newSocket.inputStream
                val output = newSocket.outputStream
                synchronized(socketLock) {
                    if (attempt != connectionAttempt.get() || socket !== newSocket) { newSocket.close(); return@execute }
                    outputStream = output
                }
                setState(BluetoothConnectionState.CONNECTED)
                ioExecutor.execute { readMessages(newSocket, input) }
            } catch (e: IOException) {
                if (candidate != null) connectionFailure(candidate, e)
                else if (attempt == connectionAttempt.get()) { error(e.localizedMessage ?: "No se pudo conectar"); setState(BluetoothConnectionState.ERROR) }
            } catch (_: SecurityException) {
                if (candidate != null) connectionFailure(candidate, IOException("Permiso Bluetooth denegado"))
                else if (attempt == connectionAttempt.get()) { error("Permiso Bluetooth denegado"); setState(BluetoothConnectionState.ERROR) }
            }
        }
    }

    fun disconnect() {
        connectionAttempt.incrementAndGet()
        val old = synchronized(socketLock) { socket.also { socket = null; outputStream = null } }
        try { old?.close() } catch (_: IOException) { }
        setState(BluetoothConnectionState.DISCONNECTED)
    }

    fun sendCommand(command: String) {
        val text = command.trim()
        if (text.isEmpty() || text.length > MAX_MESSAGE_LENGTH || text.contains('\n') || text.contains('\r')) {
            error("Comando inválido o superior a $MAX_MESSAGE_LENGTH caracteres"); return
        }
        ioExecutor.execute {
            val stream = outputStream
            if (!isConnected || stream == null) { error("No hay una conexión Bluetooth activa"); return@execute }
            try {
                synchronized(socketLock) {
                    if (stream !== outputStream) return@synchronized
                    stream.write((text + "\n").toByteArray(StandardCharsets.UTF_8)); stream.flush()
                }
            } catch (e: IOException) { socket?.let { connectionFailure(it, e) } ?: error(e.localizedMessage ?: "Error de envío") }
        }
    }

    fun close() { disconnect(); connectExecutor.shutdownNow(); ioExecutor.shutdownNow() }

    private fun readMessages(current: BluetoothSocket, input: InputStream) {
        try {
            BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
                val line = StringBuilder(); var overLimit = false
                while (true) {
                    val ch = reader.read()
                    if (ch < 0) throw IOException("El HC-05 cerró la conexión")
                    if (ch == '\n'.code) {
                        if (!overLimit) processLine(current, line.toString().removeSuffix("\r"))
                        line.setLength(0); overLimit = false
                    } else if (!overLimit) {
                        if (line.length >= MAX_MESSAGE_LENGTH) { line.setLength(0); overLimit = true; error("Mensaje supera $MAX_MESSAGE_LENGTH caracteres") }
                        else line.append(ch.toChar())
                    }
                }
            }
        } catch (e: IOException) { connectionFailure(current, e) }
    }

    private fun processLine(current: BluetoothSocket, raw: String) {
        if (socket !== current) return
        val message = raw.trim(); if (message.isEmpty()) return
        when {
            message.startsWith("ACK:", true) -> listeners.forEach { it.onAcknowledgement(message) }
            message.startsWith("ERROR:", true) -> listeners.forEach { it.onProtocolError(message) }
            message.startsWith("{") -> try {
                JSONObject(message)
                val telemetry = ArduinoTelemetry.parse(message)
                listeners.forEach { it.onJsonMessage(message); it.onTelemetry(telemetry) }
            } catch (e: JSONException) { error("JSON Bluetooth malformado") }
              catch (e: IllegalArgumentException) { error(e.message ?: "Telemetría inválida") }
            else -> error("Mensaje Bluetooth no reconocido")
        }
    }

    private fun checkPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Se requiere BLUETOOTH_CONNECT")
        }
    }
    private fun setState(value: BluetoothConnectionState) { state = value; listeners.forEach { it.onConnectionStateChanged(value) } }
    private fun error(message: String) { listeners.forEach { it.onProtocolError(message) } }
    private fun closeSocket() {
        val old = synchronized(socketLock) { socket.also { socket = null; outputStream = null } }
        try { old?.close() } catch (_: IOException) { }
    }
    private fun connectionFailure(failed: BluetoothSocket, e: Exception) {
        val current = synchronized(socketLock) { if (socket === failed) { socket = null; outputStream = null; true } else false }
        try { failed.close() } catch (_: IOException) { }
        if (current) { error(e.localizedMessage ?: "Conexión Bluetooth interrumpida"); setState(BluetoothConnectionState.ERROR) }
    }

    companion object {
        const val HC05_DEVICE_NAME = "HC-05"
        const val MAX_MESSAGE_LENGTH = 256
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
