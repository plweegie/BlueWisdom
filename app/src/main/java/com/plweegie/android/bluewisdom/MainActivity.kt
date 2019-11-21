package com.plweegie.android.bluewisdom

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.plweegie.android.bluewisdom.adapters.LeDeviceAdapter
import com.plweegie.android.bluewisdom.services.LeScanService
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), OnDeviceSelectedListener {

    private lateinit var leDeviceAdapter: LeDeviceAdapter

    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val BluetoothAdapter.isDisabled: Boolean
        get() = !isEnabled

    private val resultReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                LeScanService.SCAN_RESULT_ACTION -> {
                    scan_indicator.visibility = View.GONE
                    val result: ScanResult?
                            = intent.getParcelableExtra(LeScanService.SCAN_RESULT_EXTRA)

                    leDeviceAdapter.addDevice(result?.device)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        leDeviceAdapter = LeDeviceAdapter(this)

        devices_rv.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            setHasFixedSize(true)
            addItemDecoration(DividerItemDecoration(this@MainActivity, LinearLayoutManager.VERTICAL))
            adapter = leDeviceAdapter
        }

        val intentFilter = IntentFilter(LeScanService.SCAN_RESULT_ACTION)
        LocalBroadcastManager.getInstance(this).registerReceiver(resultReceiver, intentFilter)
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(resultReceiver)
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.activity_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.start_scanning_item -> {
                bluetoothAdapter?.takeIf { it.isDisabled }?.apply {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                } ?: if (hasLocationPermission()) {
                    startDeviceScan()
                } else {
                    ActivityCompat.requestPermissions(this, LOCATION_PERMISSIONS, REQUEST_LOCATION_PERMS)
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
        } else {
            super.onActivityResult(requestCode, resultCode, data)
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

    override fun onDeviceSelected(device: BluetoothDevice) {
        startActivity(LeConnectionActivity.newIntent(this, device.address))
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
