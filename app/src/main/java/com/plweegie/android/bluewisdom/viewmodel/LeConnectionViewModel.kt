package com.plweegie.android.bluewisdom.viewmodel

import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.RxBleDevice
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.util.*
import java.util.concurrent.TimeUnit

class LeConnectionViewModel(private val bleClient: RxBleClient) : ViewModel() {

    private companion object {
        val TEMPERATURE_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002a6e-0000-1000-8000-00805f9b34fb")
        val PRESSURE_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002a6d-0000-1000-8000-00805f9b34fb")
    }

    private val _temperature = MutableLiveData<Float>()
    private val disposable: CompositeDisposable = CompositeDisposable()

    val temperatureLiveData: LiveData<Float>
        get() = _temperature

    fun connect(macAddress: String) {
        bleClient.getBleDevice(macAddress).also {
            connectGatt(it)
        }
    }

    override fun onCleared() {
        disposable.clear()
    }

    private fun connectGatt(device: RxBleDevice?) {
        val connectionDisposable = device!!.establishConnection(false)
                .flatMap {
                    it.setupNotification(TEMPERATURE_CHARACTERISTIC_UUID)
                }
                .flatMap { it }
                .throttleFirst(500, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { bytes -> processCharacteristicResult(bytes) },
                        { error -> Log.e("RxBle", error.message ?: "Error") }
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
        _temperature.postValue(temperature / 100.0f)
    }

    private fun processPressure(characteristic: BluetoothGattCharacteristic) {
        val pressure = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0)
    }
}