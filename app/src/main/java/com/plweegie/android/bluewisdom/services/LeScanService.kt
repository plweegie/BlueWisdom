package com.plweegie.android.bluewisdom.services

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.*


class LeScanService : Service() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var handlerThread: HandlerThread

    private val scanCallback: ScanCallback by lazy {
        object : ScanCallback() {

            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val intent = Intent(SCAN_RESULT_ACTION)
                intent.putExtra(SCAN_RESULT_EXTRA, result)
                LocalBroadcastManager.getInstance(this@LeScanService).sendBroadcast(intent)

                bluetoothAdapter.bluetoothLeScanner.stopScan(scanCallback)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                Log.e("LeScanService", results?.size.toString())
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("LeScanService", "Bluetooth scan failed")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        handlerThread = HandlerThread("LeScanThread").apply { start() }
        val handler = Handler(handlerThread.looper)

        val scanSettings: ScanSettings? = intent?.getParcelableExtra(SCAN_SETTINGS_EXTRA)
        val scanFilters = arrayListOf(
                ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid(UUID.fromString(GATT_ENVIRONMENTAL_SENSING_SERVICE_UUID)))
                        .build()
        )

        handler.apply {
            post {
                bluetoothAdapter.bluetoothLeScanner.startScan(scanFilters, scanSettings, scanCallback)
                Log.d("scanning", "started")
            }
            postDelayed({
                bluetoothAdapter.bluetoothLeScanner.stopScan(scanCallback)
                Log.d("scanning", "stopped")
                stopSelf(startId)
            }, 10000)
        }

        return START_NOT_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onDestroy() {
        handlerThread.quitSafely()
        super.onDestroy()
    }

    companion object {
        const val GATT_ENVIRONMENTAL_SENSING_SERVICE_UUID = "0000181a-0000-1000-8000-00805f9b34fb"
        private const val SCAN_SETTINGS_EXTRA = "scan-settings"

        const val SCAN_RESULT_EXTRA = "scan-result"
        const val SCAN_RESULT_ACTION = "com.plweegie.android.bluewidsom.SCAN_RESULT"

        fun newIntent(context: Context, settings: ScanSettings): Intent? =
                Intent(context, LeScanService::class.java).apply {
                    putExtra(SCAN_SETTINGS_EXTRA, settings)
                }
    }
}