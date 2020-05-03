package com.plweegie.android.bluewisdom.viewmodel

import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDevice
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import java.util.*
import java.util.concurrent.TimeUnit

class LeConnectionViewModel(private val bleClient: RxBleClient) : ViewModel() {

    companion object {
        private val TEMPERATURE_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002a6e-0000-1000-8000-00805f9b34fb")

        const val TEMPERATURE_PREFERENCE = "pref_temperature"
    }

    private val _temperature = MutableLiveData<Float>()
    private var disposable: Disposable? = null

    val temperatureLiveData: LiveData<Float>
        get() = _temperature

    fun connect(macAddress: String) {
        bleClient.getBleDevice(macAddress).also {
            connectGatt(it)
        }
    }

    override fun onCleared() {
        disposable?.dispose()
    }

    private fun connectGatt(device: RxBleDevice?) {
        val connectionDisposable = device!!.establishConnection(false)
                .flatMap {
                    setupNotifications(it, listOf(TEMPERATURE_CHARACTERISTIC_UUID))
                }
                .throttleFirst(500, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { result -> processCharacteristicResult(result.first, result.second) },
                        { error -> Log.e("RxBle", error.message ?: "Error") }
                )
        disposable = connectionDisposable
    }

    private fun setupNotifications(connection: RxBleConnection, characteristics: List<UUID>):
            Observable<Pair<UUID, ByteArray>> = Observable.fromIterable(characteristics)
                .zipWith(Observable.interval(1000, TimeUnit.MILLISECONDS), BiFunction<UUID, Long, UUID> {
                    uuid, _ -> uuid
                })
                .flatMap({
                    Log.d("RxBle", "Setting notification for $it")
                    connection.setupNotification(it)
                            .doOnError { err ->
                                Log.e("RxBLE", "Error: $err")
                                err.printStackTrace() }
                            .flatMap { observable -> observable }
                }, {
                    p0: UUID, p1: ByteArray -> Pair(p0, p1)
                })

    private fun processCharacteristicResult(characteristicUUID: UUID, byteArray: ByteArray) {
        val characteristic = BluetoothGattCharacteristic(characteristicUUID, 16, 1).also {
            it.value = byteArray
        }

        when (characteristicUUID) {
            TEMPERATURE_CHARACTERISTIC_UUID -> processTemperature(characteristic)
        }
    }

    private fun processTemperature(characteristic: BluetoothGattCharacteristic) {
        val temperature = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 0)
        _temperature.postValue(temperature / 100.0f)
    }
}