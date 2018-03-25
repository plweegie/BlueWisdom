package com.plweegie.android.bluewisdom

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import kotlinx.android.synthetic.main.device_list_item.view.*


class LeDeviceAdapter(private val context: Context) : RecyclerView.Adapter<LeDeviceAdapter.LeDeviceViewHolder>() {

    private var mDevices: MutableList<BluetoothDevice> = mutableListOf()

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): LeDeviceViewHolder {
        val inflater = LayoutInflater.from(context)
        return LeDeviceViewHolder(inflater, parent, R.layout.device_list_item)
    }

    override fun onBindViewHolder(holder: LeDeviceViewHolder?, position: Int) {
        holder?.bind(mDevices[position])
    }

    override fun getItemCount(): Int  = mDevices.size

    fun addDevice(device: BluetoothDevice?) {
        if (device != null) {
            mDevices.add(device)
            notifyDataSetChanged()
        }

    }

    inner class LeDeviceViewHolder(inflater: LayoutInflater, parent: ViewGroup?, layoutResId: Int) :
            RecyclerView.ViewHolder(inflater.inflate(layoutResId, parent, false)) {

        fun bind(device: BluetoothDevice) {
            itemView.device_name_tv.text = device.name
            itemView.device_address_tv.text = device.address
        }
    }
}