package com.plweegie.android.bluewisdom

import android.bluetooth.BluetoothDevice


interface OnDeviceSelectedListener {
    fun onDeviceSelected(device: BluetoothDevice)
}