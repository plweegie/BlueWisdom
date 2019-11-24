package com.plweegie.android.bluewisdom

import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.RxBleDevice
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_le_connection.*
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject


class LeConnectionActivity : AppCompatActivity() {

    private var macAddress: String? = null
    private var bleDevice: RxBleDevice? = null

    @Inject
    lateinit var bleClient: RxBleClient

    companion object {
        private val TEMPERATURE_CHARACTERISTIC_UUID = UUID.fromString("00002a6e-0000-1000-8000-00805f9b34fb")
        private val PRESSURE_CHARACTERISTIC_UUID = UUID.fromString("00002a6d-0000-1000-8000-00805f9b34fb")

        private const val MAC_ADDRESS_EXTRA = "extra_mac_address"

        fun newIntent(context: Context, macAddress: String) =
                Intent(context, LeConnectionActivity::class.java).apply {
                    putExtra(MAC_ADDRESS_EXTRA, macAddress)
                }
    }

    private val disposable: CompositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as App).appComponent.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_le_connection)

        macAddress = intent.getStringExtra(MAC_ADDRESS_EXTRA)

        macAddress?.let {
            bleDevice = bleClient.getBleDevice(it)
            connectGatt(bleDevice)
        }
    }

    override fun onPause() {
        super.onPause()
        disposable.dispose()
    }

    private fun connectGatt(device: RxBleDevice?) {
        val connectionDisposable = device!!.establishConnection(false)
                .flatMap {
                    Observable.merge(it.setupNotification(TEMPERATURE_CHARACTERISTIC_UUID),
                            it.setupNotification(PRESSURE_CHARACTERISTIC_UUID)) }
                .flatMap { it }
                .throttleFirst(500, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { bytes -> processCharacteristicResult(bytes) },
                        { error -> Toast.makeText(this, error.message, Toast.LENGTH_SHORT).show() }
                )
        disposable.add(connectionDisposable)
    }

    private fun processCharacteristicResult(byteArray: ByteArray) {

        when {
            byteArray.size <= 2 -> {
                BluetoothGattCharacteristic(TEMPERATURE_CHARACTERISTIC_UUID, 16, 1).also {
                    it.value = byteArray
                    processTemperature(it)
                }
            }
            else -> {
                BluetoothGattCharacteristic(PRESSURE_CHARACTERISTIC_UUID, 16, 1).also {
                    it.value = byteArray
                    processPressure(it)
                }
            }
        }
    }

    private fun processTemperature(characteristic: BluetoothGattCharacteristic) {
        val temperature = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 0)
        temperature_text?.text = temperature.toString()
    }

    private fun processPressure(characteristic: BluetoothGattCharacteristic) {
        val pressure = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0)
        pressure_text?.text = pressure.toString()
    }
}