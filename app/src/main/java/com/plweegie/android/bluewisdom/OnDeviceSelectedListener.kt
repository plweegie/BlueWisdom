package com.plweegie.android.bluewisdom

import com.polidea.rxandroidble2.RxBleDevice


interface OnDeviceSelectedListener {
    fun onDeviceSelected(device: RxBleDevice)
}