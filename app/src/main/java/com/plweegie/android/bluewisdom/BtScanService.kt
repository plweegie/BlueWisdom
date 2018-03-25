package com.plweegie.android.bluewisdom

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.IBinder


class BtScanService : Service() {

    private lateinit var mBluetoothAdapter: BluetoothAdapter

    override fun onCreate() {
        super.onCreate()
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = bluetoothManager.adapter
    }

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mBluetoothAdapter.startDiscovery()
        return START_NOT_STICKY
    }

    companion object {
        fun newIntent(context: Context): Intent? = Intent(context, BtScanService::class.java)
    }
}