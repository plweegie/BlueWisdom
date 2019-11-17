package com.plweegie.android.bluewisdom.services

import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.util.*


class LeConnectionService : Service() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothGatt: BluetoothGatt? = null

    companion object {
        private const val TEMPERATURE_CHARACTERISTIC_UUID = "00002a6e-0000-1000-8000-00805f9b34fb"
        private const val PRESSURE_CHARACTERISTIC_UUID = "00002a6d-0000-1000-8000-00805f9b34fb"
        private const val CLIENT_CONFIG_UUID = "00002902-0000-1000-8000-00805f9b34fb"

        const val MAC_ADDRESS_EXTRA = "mac-address"
        const val EXTRA_DATA = "com.plweegie.android.bluewisdom.EXTRA_DATA"
        const val ACTION_TEMPERATURE_AVAILABLE = "com.plweegie.android.bluewisdom.ACTION_TEMPERATURE_AVAILABLE"
        const val ACTION_PRESSURE_AVAILABLE = "com.plweegie.android.bluewisdom.ACTION_PRESSURE_AVAILABLE"

        fun newIntent(context: Context, macAddress: String): Intent? =
                Intent(context, LeScanService::class.java).apply {
                    putExtra(MAC_ADDRESS_EXTRA, macAddress)
                }
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
                newState == BluetoothProfile.STATE_CONNECTED -> bluetoothGatt?.discoverServices()
                newState == BluetoothProfile.STATE_DISCONNECTED -> disconnectGatt()
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

    override fun onCreate() {
        super.onCreate()
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val macAddress = intent?.getStringExtra(MAC_ADDRESS_EXTRA)
        bluetoothGatt = bluetoothAdapter.getRemoteDevice(macAddress)
                .connectGatt(this, false, gattCallback)
        return START_NOT_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        disconnectGatt()
        bluetoothGatt = null
    }

    private fun broadcastChangedUpdate(characteristic: BluetoothGattCharacteristic) {
        var intent = Intent()

        when (characteristic.uuid) {
            UUID.fromString(TEMPERATURE_CHARACTERISTIC_UUID) -> {
                intent = Intent(ACTION_TEMPERATURE_AVAILABLE)
                val temperature = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 0)
                intent.putExtra(EXTRA_DATA, temperature.toString())
            }
            UUID.fromString(PRESSURE_CHARACTERISTIC_UUID) -> {
                intent = Intent(ACTION_PRESSURE_AVAILABLE)
                val pressure = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0)
                intent.putExtra(EXTRA_DATA, pressure.toString())
            }
        }
        sendBroadcast(intent)
    }

    private fun broadcastReadUpdate(characteristic: BluetoothGattCharacteristic) {
        var intent = Intent()
        val data = characteristic.value

        when (characteristic.uuid) {
            UUID.fromString(TEMPERATURE_CHARACTERISTIC_UUID) -> {
                intent = Intent(ACTION_TEMPERATURE_AVAILABLE)
                val temperature = ((data[0].toInt() shl 8) or data[1].toInt()) / 100.0
                intent.putExtra(EXTRA_DATA, temperature.toString())
            }
            UUID.fromString(PRESSURE_CHARACTERISTIC_UUID) -> {
                intent = Intent(ACTION_PRESSURE_AVAILABLE)
                val pressure = ((data[0].toInt() shl 24) or (data[1].toInt() shl 16) or
                        (data[2].toInt() shl 8) or data[3].toInt()) / 10.0
                intent.putExtra(EXTRA_DATA, pressure.toString())
            }
        }
        sendBroadcast(intent)
    }

    private fun enableGattNotifications(characteristic: BluetoothGattCharacteristic) {
        bluetoothGatt?.setCharacteristicNotification(characteristic, true)

        val gattDescriptor = characteristic.getDescriptor(UUID.fromString(CLIENT_CONFIG_UUID))
        gattDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        bluetoothGatt?.writeDescriptor(gattDescriptor)
    }

    private fun disconnectGatt() {
        bluetoothGatt?.apply {
            disconnect()
            close()
        }
    }
}