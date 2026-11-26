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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Manages a classic Bluetooth SPP connection to a previously paired HC-05. */
class BluetoothService(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onConnectionStateChanged(state: BluetoothConnectionState)
        fun onJsonMessage(json: String)
        fun onAcknowledgement(message: String)
        fun onProtocolError(message: String)
    }

    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val connectExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val ioExecutor: ExecutorService = Executors.newCachedThreadPool()
    private val socketLock = Any()
    private val connectionAttempt = AtomicLong(0)

    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var outputStream: OutputStream? = null
    @Volatile var state: BluetoothConnectionState = BluetoothConnectionState.DISCONNECTED
        private set

    val isBluetoothAvailable: Boolean
        get() = adapter != null

    fun isBluetoothEnabled(): Boolean = try {
        adapter?.isEnabled == true
    } catch (_: SecurityException) {
        false
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        if (requiresConnectPermission() &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("Se requiere el permiso BLUETOOTH_CONNECT")
        }
        return adapter?.bondedDevices?.toList().orEmpty()
    }

    fun findPairedHC05(): BluetoothDevice? = getPairedDevices().firstOrNull {
        try {
            it.name.equals(HC05_DEVICE_NAME, ignoreCase = true)
        } catch (_: SecurityException) {
            false
        }
    }

    fun connect(device: BluetoothDevice) {
        if (requiresConnectPermission() &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            reportError("Falta el permiso Bluetooth para Android 12 o superior")
            publishState(BluetoothConnectionState.ERROR)
            return
        }
        if (!isBluetoothAvailable) {
            reportError("Este dispositivo no dispone de Bluetooth")
            publishState(BluetoothConnectionState.ERROR)
            return
        }

        val attempt = connectionAttempt.incrementAndGet()
        publishState(BluetoothConnectionState.CONNECTING)
        connectExecutor.execute {
            closeCurrentSocket()
            if (attempt != connectionAttempt.get()) return@execute

            var candidate: BluetoothSocket? = null
            try {
                if (!device.name.equals(HC05_DEVICE_NAME, ignoreCase = true)) {
                    throw IOException("Selecciona un dispositivo cuyo nombre sea HC-05")
                }
                val newSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                candidate = newSocket
                synchronized(socketLock) {
                    if (attempt != connectionAttempt.get()) {
                        newSocket.close()
                        return@execute
                    }
                    socket = newSocket
                    outputStream = null
                }

                // Blocking Bluetooth operations run on connectExecutor, never the UI thread.
                newSocket.connect()
                val input = newSocket.inputStream
                val output = newSocket.outputStream
                synchronized(socketLock) {
                    if (attempt != connectionAttempt.get() || socket !== newSocket) {
                        newSocket.close()
                        return@execute
                    }
                    outputStream = output
                }
                publishState(BluetoothConnectionState.CONNECTED)
                ioExecutor.execute { readMessages(newSocket, input) }
            } catch (error: IOException) {
                val failedSocket = candidate
                if (failedSocket != null) {
                    handleConnectionFailure(failedSocket, error)
                } else if (attempt == connectionAttempt.get()) {
                    reportError(error.localizedMessage ?: "No se pudo conectar al HC-05")
                    publishState(BluetoothConnectionState.ERROR)
                }
            } catch (_: SecurityException) {
                val failedSocket = candidate
                if (failedSocket != null) {
                    handleConnectionFailure(failedSocket, IOException("Permiso Bluetooth denegado"))
                } else if (attempt == connectionAttempt.get()) {
                    reportError("Permiso Bluetooth denegado")
                    publishState(BluetoothConnectionState.ERROR)
                }
            }
        }
    }

    fun disconnect() {
        connectionAttempt.incrementAndGet()
        val oldSocket: BluetoothSocket?
        synchronized(socketLock) {
            oldSocket = socket
            socket = null
            outputStream = null
        }
        try {
            oldSocket?.close()
        } catch (_: IOException) {
            // La desconexión continúa aunque el cierre del socket ya haya fallado.
        }
        publishState(BluetoothConnectionState.DISCONNECTED)
    }

    fun sendCommand(command: String) {
        val normalized = command.trim()
        if (normalized.isEmpty() || normalized.length > MAX_MESSAGE_LENGTH ||
            normalized.contains('\n') || normalized.contains('\r')
        ) {
            reportError("Comando vacío, demasiado largo o con terminador no permitido")
            return
        }

        ioExecutor.execute {
            val currentOutput = outputStream
            if (state != BluetoothConnectionState.CONNECTED || currentOutput == null) {
                reportError("No hay una conexión Bluetooth activa")
                return@execute
            }
            try {
                synchronized(socketLock) {
                    if (outputStream !== currentOutput) {
                        reportError("La conexión Bluetooth ya no está disponible")
                        return@execute
                    }
                    currentOutput.write((normalized + "\n").toByteArray(StandardCharsets.UTF_8))
                    currentOutput.flush()
                }
            } catch (error: IOException) {
                socket?.let { handleConnectionFailure(it, error) }
                    ?: reportError(error.localizedMessage ?: "Error al enviar por Bluetooth")
            }
        }
    }

    fun close() {
        disconnect()
        connectExecutor.shutdownNow()
        ioExecutor.shutdownNow()
    }

    private fun readMessages(connectedSocket: BluetoothSocket, input: InputStream) {
        try {
            BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
                val line = StringBuilder()
                var discardingLongLine = false
                while (true) {
                    val value = reader.read()
                    if (value == -1) throw IOException("El HC-05 cerró la conexión")
                    if (value == '\n'.code) {
                        if (!discardingLongLine) {
                            processLine(connectedSocket, line.toString().removeSuffix("\r"))
                        }
                        line.setLength(0)
                        discardingLongLine = false
                    } else if (!discardingLongLine) {
                        if (line.length >= MAX_MESSAGE_LENGTH) {
                            line.setLength(0)
                            discardingLongLine = true
                            reportError("Mensaje Bluetooth supera $MAX_MESSAGE_LENGTH caracteres")
                        } else {
                            line.append(value.toChar())
                        }
                    }
                }
            }
        } catch (error: IOException) {
            handleConnectionFailure(connectedSocket, error)
        }
    }

    private fun processLine(connectedSocket: BluetoothSocket, rawLine: String) {
        if (socket !== connectedSocket) return
        val message = rawLine.trim()
        if (message.isEmpty()) return

        when {
            message.startsWith("ACK:", ignoreCase = true) -> listener.onAcknowledgement(message)
            message.startsWith("ERROR:", ignoreCase = true) -> listener.onProtocolError(message)
            message.startsWith("{") -> try {
                JSONObject(message)
                listener.onJsonMessage(message)
            } catch (_: JSONException) {
                listener.onProtocolError("JSON Bluetooth malformado")
            }
            else -> listener.onProtocolError("Mensaje Bluetooth no reconocido")
        }
    }

    private fun handleConnectionFailure(failedSocket: BluetoothSocket, error: Exception) {
        val wasCurrent = synchronized(socketLock) {
            if (socket === failedSocket) {
                socket = null
                outputStream = null
                true
            } else {
                false
            }
        }
        try {
            failedSocket.close()
        } catch (_: IOException) {
            // No-op: el estado de error ya se informa abajo.
        }
        if (wasCurrent) {
            reportError(error.localizedMessage ?: "Se perdió la conexión Bluetooth")
            publishState(BluetoothConnectionState.ERROR)
        }
    }

    private fun closeCurrentSocket() {
        val oldSocket: BluetoothSocket?
        synchronized(socketLock) {
            oldSocket = socket
            socket = null
            outputStream = null
        }
        try {
            oldSocket?.close()
        } catch (_: IOException) {
            // Continúa con el intento de conexión nuevo.
        }
    }

    private fun publishState(newState: BluetoothConnectionState) {
        state = newState
        listener.onConnectionStateChanged(newState)
    }

    private fun reportError(message: String) {
        listener.onProtocolError(message)
    }

    private fun requiresConnectPermission(): Boolean =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

    companion object {
        const val HC05_DEVICE_NAME = "HC-05"
        const val MAX_MESSAGE_LENGTH = 256
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
