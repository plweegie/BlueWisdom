package com.plweegie.android.bluewisdom

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
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import java.util.*


class LeScanService : Service() {

    private lateinit var mBluetoothAdapter: BluetoothAdapter
    private lateinit var mThread: HandlerThread

    private val mScanCallback: ScanCallback by lazy {
        object : ScanCallback() {

            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val intent = Intent(SCAN_RESULT_ACTION)
                intent.putExtra(SCAN_RESULT_EXTRA, result)
                LocalBroadcastManager.getInstance(this@LeScanService).sendBroadcast(intent)

                mBluetoothAdapter.bluetoothLeScanner.stopScan(mScanCallback)
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
        mBluetoothAdapter = bluetoothManager.adapter
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        mThread = HandlerThread("LeScanThread")
        mThread.start()
        val handler = Handler(mThread.looper)

        val scanSettings: ScanSettings = intent!!.getParcelableExtra(SCAN_SETTINGS_EXTRA)
        val scanFilters = arrayListOf(
                ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid(UUID.fromString(GATT_ENVIRONMENTAL_SENSING_SERVICE_UUID)))
                        .build()
        )

        handler.apply {
            post {
                mBluetoothAdapter.bluetoothLeScanner.startScan(scanFilters, scanSettings, mScanCallback)
                Log.d("scanning", "started")
            }
            postDelayed({
                mBluetoothAdapter.bluetoothLeScanner.stopScan(mScanCallback)
                Log.d("scanning", "stopped")
                stopSelf(startId)
            }, 10000)
        }

        return START_NOT_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onDestroy() {
        mThread.quitSafely()
        super.onDestroy()
    }

    companion object {
        const val GATT_TIME_SERVICE_UUID = "00001805-0000-1000-8000-00805f9b34fb"
        const val GATT_ENVIRONMENTAL_SENSING_SERVICE_UUID = "0000181a-0000-1000-8000-00805f9b34fb"
        private const val SCAN_SETTINGS_EXTRA = "scan-settings"

        const val SCAN_RESULT_EXTRA = "scan-result"
        const val SCAN_RESULT_ACTION = "com.plweegie.android.bluewidsom.SCAN_RESULT"

        fun newIntent(context: Context, settings: ScanSettings): Intent? {
            val intent = Intent(context, LeScanService::class.java)
            intent.putExtra(SCAN_SETTINGS_EXTRA, settings)
            return intent
        }
    }
}