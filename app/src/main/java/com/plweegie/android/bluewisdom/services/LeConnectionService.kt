package com.plweegie.android.bluewisdom.services

import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.*


class LeConnectionService : Service() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothDeviceAddress: String? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var bluetoothManager: BluetoothManager? = null
    private var connectionState: Int = STATE_DISCONNECTED
    private lateinit var bleThread: HandlerThread

    private val binder = LocalBinder()

    companion object {
        private const val TEMPERATURE_CHARACTERISTIC_UUID = "00002a6e-0000-1000-8000-00805f9b34fb"
        private const val PRESSURE_CHARACTERISTIC_UUID = "00002a6d-0000-1000-8000-00805f9b34fb"
        private const val CLIENT_CONFIG_UUID = "00002902-0000-1000-8000-00805f9b34fb"

        private const val STATE_DISCONNECTED = 0
        private const val STATE_CONNECTING = 1
        private const val STATE_CONNECTED = 2

        const val MAC_ADDRESS_EXTRA = "mac-address"
        const val EXTRA_DATA = "com.plweegie.android.bluewisdom.EXTRA_DATA"
        const val ACTION_TEMPERATURE_AVAILABLE = "com.plweegie.android.bluewisdom.ACTION_TEMPERATURE_AVAILABLE"
        const val ACTION_PRESSURE_AVAILABLE = "com.plweegie.android.bluewisdom.ACTION_PRESSURE_AVAILABLE"
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            Log.d("Status", status.toString())
            Log.d("New state", newState.toString())

            when {
                status != BluetoothGatt.GATT_SUCCESS -> {
                    disconnectGatt()
                    return
                }
                newState == BluetoothProfile.STATE_CONNECTED -> {
                    connectionState = STATE_CONNECTED
                    bluetoothGatt?.discoverServices()
                }
                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    connectionState = STATE_DISCONNECTED
                    closeGatt()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {

            if (status != BluetoothGatt.GATT_SUCCESS) {
                return
            }
            val timeService = bluetoothGatt?.services
                    ?.filter { it.uuid == UUID.fromString(LeScanService.GATT_ENVIRONMENTAL_SENSING_SERVICE_UUID) }
                    ?.get(0)
            val characteristics = timeService?.characteristics

            characteristics?.let {
                it.forEach { char ->
                    Log.d("service", char.uuid.toString())
                    enableGattNotifications(char)
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {

            if (characteristic?.value != null) {
                broadcastChangedUpdate(characteristic)
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt?,
                                          characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic?.value != null) {

                broadcastReadUpdate(characteristic)
            }
        }
    }

    override fun onBind(p0: Intent?): IBinder? = binder

    override fun onUnbind(intent: Intent?): Boolean {
        closeGatt()
        return super.onUnbind(intent)
    }

    fun initialize(): Boolean {
        bluetoothManager = bluetoothManager ?: getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        return (bluetoothAdapter != null)
    }

    fun connect(address: String?): Boolean {
        if (bluetoothAdapter == null || address == null) {
            return false
        }

        if (address == bluetoothDeviceAddress && bluetoothGatt != null) {
            return if (bluetoothGatt?.connect() == true) {
                connectionState = STATE_CONNECTING
                true
            } else {
                false
            }
        }

        bleThread = HandlerThread("LeConnectThread").apply { start() }
        val handler = Handler(bleThread.looper)

        return bluetoothAdapter?.getRemoteDevice(address)?.let {
            handler.postDelayed({
                bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    it.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    it.connectGatt(this, false, gattCallback)
                }
            }, 500)
            bluetoothDeviceAddress = address
            connectionState = STATE_CONNECTING
            true
        } ?: false
    }

    private fun broadcastChangedUpdate(characteristic: BluetoothGattCharacteristic) {
        val intent = Intent()

        when (characteristic.uuid) {
            UUID.fromString(TEMPERATURE_CHARACTERISTIC_UUID) -> {
                val temperature = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 0)
                Log.d("JAN", "Temperature $temperature")
                intent.apply {
                    action = ACTION_TEMPERATURE_AVAILABLE
                    putExtra(EXTRA_DATA, temperature.toString())

                }
            }
            UUID.fromString(PRESSURE_CHARACTERISTIC_UUID) -> {
                val pressure = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0)
                intent.apply {
                    action = ACTION_PRESSURE_AVAILABLE
                    putExtra(EXTRA_DATA, pressure.toString())
                }
            }
        }
        //LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastReadUpdate(characteristic: BluetoothGattCharacteristic) {
        val intent = Intent()
        val data = characteristic.value

        when (characteristic.uuid) {
            UUID.fromString(TEMPERATURE_CHARACTERISTIC_UUID) -> {
                val temperature = ((data[0].toInt() shl 8) or data[1].toInt()) / 100.0
                intent.apply {
                    action = ACTION_TEMPERATURE_AVAILABLE
                    putExtra(EXTRA_DATA, temperature.toString())
                }
            }
            UUID.fromString(PRESSURE_CHARACTERISTIC_UUID) -> {
                val pressure = ((data[0].toInt() shl 24) or (data[1].toInt() shl 16) or
                        (data[2].toInt() shl 8) or data[3].toInt()) / 10.0
                intent.apply {
                    action = ACTION_PRESSURE_AVAILABLE
                    putExtra(EXTRA_DATA, pressure.toString())
                }
            }
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun enableGattNotifications(characteristic: BluetoothGattCharacteristic) {
        bluetoothGatt?.setCharacteristicNotification(characteristic, true)

        val gattDescriptor = characteristic.getDescriptor(UUID.fromString(CLIENT_CONFIG_UUID))
        gattDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        bluetoothGatt?.writeDescriptor(gattDescriptor)
    }

    fun disconnectGatt() {
        bluetoothGatt?.disconnect()
    }

    fun closeGatt() {
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    inner class LocalBinder: Binder() {
        fun getService(): LeConnectionService = this@LeConnectionService
    }
}