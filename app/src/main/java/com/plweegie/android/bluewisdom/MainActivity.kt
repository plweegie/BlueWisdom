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
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.plweegie.android.bluewisdom.adapters.LeDeviceAdapter
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.RxBleDevice
import com.polidea.rxandroidble2.scan.ScanFilter
import com.polidea.rxandroidble2.scan.ScanSettings
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class MainActivity : AppCompatActivity(), OnDeviceSelectedListener {

    private lateinit var leDeviceAdapter: LeDeviceAdapter

    @Inject
    lateinit var bleClient: RxBleClient

    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val BluetoothAdapter.isDisabled: Boolean
        get() = !isEnabled

    private val disposable: CompositeDisposable = CompositeDisposable()

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
        disposable.dispose()
        super.onPause()
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

    override fun onDeviceSelected(device: RxBleDevice) {
        with(getPreferences(Context.MODE_PRIVATE).edit()) {
            putString(MACHINE_ADDRESS_PREF, device.macAddress)
            commit()
        }

        startActivity(LeConnectionActivity.newIntent(this, device.macAddress))
    }

    private fun startDeviceScan() {
        scan_indicator.visibility = View.VISIBLE

        val settings: ScanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

        val scanFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(UUID.fromString(GATT_ENVIRONMENTAL_SENSING_SERVICE_UUID)))
                .build()

        val scanObservable = bleClient.scanBleDevices(settings, scanFilter)
                .distinct { result -> result.bleDevice.macAddress }
                .take(5, TimeUnit.SECONDS)
        val scanDisposable = scanObservable
                .subscribe(
                        { scanResult ->
                            scan_indicator.visibility = View.GONE
                            leDeviceAdapter.addDevice(scanResult.bleDevice) },
                        { error -> showToast(error) }
                )

        val flowDisposable = bleClient.observeStateChanges()
                .switchMap { state ->
                    when (state) {
                        RxBleClient.State.READY -> scanObservable
                        RxBleClient.State.BLUETOOTH_NOT_AVAILABLE -> Observable.error(Throwable("No BLE on your device"))
                        RxBleClient.State.LOCATION_PERMISSION_NOT_GRANTED ->
                            Observable.error(Throwable("Location permissions not granted"))
                        RxBleClient.State.BLUETOOTH_NOT_ENABLED ->
                            Observable.error(Throwable("Bluetooth not enabled"))
                        RxBleClient.State.LOCATION_SERVICES_NOT_ENABLED ->
                            Observable.error(Throwable("Location services not enabled"))
                    }
                }
                .subscribe(
                        { scanResult ->
                            scan_indicator.visibility = View.GONE
                            leDeviceAdapter.addDevice(scanResult.bleDevice) },
                        { error -> showToast(error) }
                )

        disposable.apply {
            add(flowDisposable)
            add(scanDisposable)
        }

        Handler().postDelayed({
            disposable.clear()
            scan_indicator.visibility = View.GONE
        }, 5000)
    }

    private fun showToast(t: Throwable) {
        Toast.makeText(this, t.message, Toast.LENGTH_SHORT).show()
    }

    private fun hasLocationPermission(): Boolean =
            (ContextCompat.checkSelfPermission(this, LOCATION_PERMISSIONS[0]) ==
                    PackageManager.PERMISSION_GRANTED)

    companion object {
        private const val REQUEST_ENABLE_BT = 211
        private const val REQUEST_LOCATION_PERMS = 11
        private const val MACHINE_ADDRESS_PREF = "machine_address_pref"
        private const val GATT_ENVIRONMENTAL_SENSING_SERVICE_UUID = "0000181a-0000-1000-8000-00805f9b34fb"

        private val LOCATION_PERMISSIONS = arrayOf(
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}
