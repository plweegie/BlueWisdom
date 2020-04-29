package com.plweegie.android.bluewisdom

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.ParcelUuid
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.plweegie.android.bluewisdom.adapters.LeDeviceAdapter
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.RxBleDevice
import com.polidea.rxandroidble2.scan.ScanFilter
import com.polidea.rxandroidble2.scan.ScanSettings
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class MainActivity : AppCompatActivity(), OnDeviceSelectedListener {

    private lateinit var leDeviceAdapter: LeDeviceAdapter

    @Inject
    lateinit var bleClient: RxBleClient

    private var scanDisposable: Disposable? = null
    private var hasClickedScan = false

    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val BluetoothAdapter.isDisabled: Boolean
        get() = !isEnabled

    private val isScanning: Boolean
        get() = scanDisposable != null

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as App).appComponent.inject(this)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        leDeviceAdapter = LeDeviceAdapter(this)

        devices_rv.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            setHasFixedSize(true)
            addItemDecoration(DividerItemDecoration(this@MainActivity, LinearLayoutManager.VERTICAL))
            adapter = leDeviceAdapter
        }
    }

    override fun onPause() {
        super.onPause()
        if (isScanning) scanDisposable?.dispose()
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
                } ?: maybeStartDeviceScan()
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
                maybeStartDeviceScan()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (isLocationPermissionGranted(requestCode, grantResults) && hasClickedScan) {
            hasClickedScan = false
            scanBleDevices()
        }
    }

    override fun onDeviceSelected(device: RxBleDevice) {
        with(getPreferences(Context.MODE_PRIVATE).edit()) {
            putString(MACHINE_ADDRESS_PREF, device.macAddress)
            commit()
        }

        startActivity(LeConnectionActivity.newIntent(this, device.macAddress))
    }

    private fun maybeStartDeviceScan() {
        if (isScanning) {
            scanDisposable?.dispose()
        } else {
            scan_indicator.visibility = View.VISIBLE

            if (bleClient.isScanRuntimePermissionGranted) {
                scanBleDevices()

                Handler().postDelayed({
                    scan_indicator.visibility = View.GONE
                }, 5000)

            } else {
                hasClickedScan = true
                requestLocationPermission(bleClient)
            }
        }
    }

    private fun scanBleDevices() {
        leDeviceAdapter.clearData()

        val settings: ScanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

        val scanFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(GATT_ENVIRONMENTAL_SENSING_SERVICE_UUID))
                .build()

        bleClient.scanBleDevices(settings, scanFilter)
                .distinct { result -> result.bleDevice.macAddress }
                .take(5, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally { dispose() }
                .subscribe(
                        { scanResult ->
                            scan_indicator.visibility = View.GONE
                            leDeviceAdapter.addDevice(scanResult.bleDevice) },
                        { error -> showToast(error) }
                )
                .let { scanDisposable = it }
    }

    private fun requestLocationPermission(client: RxBleClient) =
        ActivityCompat.requestPermissions(
            this,
        /*
         * the below would cause a ArrayIndexOutOfBoundsException on API < 23. Yet it should not be called then as runtime
         * permissions are not needed and RxBleClient.isScanRuntimePermissionGranted() returns `true`
         */
            arrayOf(client.recommendedScanRuntimePermissions[0]),
            REQUEST_LOCATION_PERMS
        )

    private fun isLocationPermissionGranted(requestCode: Int, grantResults: IntArray): Boolean =
            requestCode == REQUEST_LOCATION_PERMS && grantResults[0] == PackageManager.PERMISSION_GRANTED

    private fun dispose() {
        scanDisposable = null
    }

    private fun showToast(t: Throwable) {
        Toast.makeText(this, t.message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REQUEST_ENABLE_BT = 211
        private const val REQUEST_LOCATION_PERMS = 101
        private const val MACHINE_ADDRESS_PREF = "machine_address_pref"
        val GATT_ENVIRONMENTAL_SENSING_SERVICE_UUID: UUID = UUID.fromString("0000181a-0000-1000-8000-00805f9b34fb")
    }
}
