package com.plweegie.android.bluewisdom

import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var mBluetoothAdapter: BluetoothAdapter
    private var mBluetoothGatt: BluetoothGatt? = null
    private lateinit var mAdapter: LeDeviceAdapter

    private val mResultReceiver: BroadcastReceiver by lazy {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {

                val action = intent!!.action
                if (action == LeScanService.SCAN_RESULT_ACTION) {
                    scan_indicator.visibility = View.GONE
                    val result: ScanResult?
                            = intent.getParcelableExtra(LeScanService.SCAN_RESULT_EXTRA)

                    mAdapter.addDevice(result?.device)

//                    if (results!!.isNotEmpty()) {
//                        mBluetoothGatt = results[0].device.connectGatt(this@MainActivity,
//                                false, mGattCallback)
//                    }
                }
            }
        }
    }

    private val mGattCallback: BluetoothGattCallback by lazy {
        object : BluetoothGattCallback() {

            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                Log.d("Status", status.toString())
                Log.d("New state", newState.toString())
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    mBluetoothGatt?.discoverServices()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                val timeService = mBluetoothGatt?.services
                        ?.filter { it.uuid == UUID.fromString(LeScanService.GATT_ENVIRONMENTAL_SENSING_SERVICE_UUID)  }
                        ?.get(0)
                val characteristics = timeService?.characteristics

                if (characteristics != null) {
                    for (char in characteristics) {
                        Log.d("service", char.uuid.toString())
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mAdapter = LeDeviceAdapter(this)

        devices_rv.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            setHasFixedSize(true)
            addItemDecoration(DividerItemDecoration(this@MainActivity, LinearLayoutManager.VERTICAL))
            adapter = mAdapter
        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = bluetoothManager.adapter
    }

    override fun onStart() {
        super.onStart()
        val intentFilter = IntentFilter(LeScanService.SCAN_RESULT_ACTION)
        LocalBroadcastManager.getInstance(this).registerReceiver(mResultReceiver, intentFilter)
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mResultReceiver)
        mBluetoothGatt?.disconnect()
        mBluetoothGatt?.close()
        mBluetoothGatt = null
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.activity_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item?.itemId) {
            R.id.start_scanning_item -> {
                if (!mBluetoothAdapter.isEnabled) {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                } else if (mBluetoothAdapter.isEnabled && !hasLocationPermission()) {
                    ActivityCompat.requestPermissions(this, LOCATION_PERMISSIONS, REQUEST_LOCATION_PERMS)
                } else {
                    startDeviceScan()
                }
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                if (hasLocationPermission()) {
                    startDeviceScan()
                } else {
                    ActivityCompat.requestPermissions(this, LOCATION_PERMISSIONS, REQUEST_LOCATION_PERMS)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_LOCATION_PERMS -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startDeviceScan()
                }
            }
            else -> {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }
        }
    }

    private fun startDeviceScan() {
        scan_indicator.visibility = View.VISIBLE
        val settings: ScanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
        startService(LeScanService.newIntent(this, settings))
    }

    private fun hasLocationPermission(): Boolean =
            (ContextCompat.checkSelfPermission(this, LOCATION_PERMISSIONS[0]) ==
                    PackageManager.PERMISSION_GRANTED)

    companion object {
        private const val REQUEST_ENABLE_BT = 211
        private const val REQUEST_LOCATION_PERMS = 11

        private val LOCATION_PERMISSIONS = arrayOf(
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}
