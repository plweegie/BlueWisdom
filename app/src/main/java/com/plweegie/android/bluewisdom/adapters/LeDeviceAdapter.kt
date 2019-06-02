package com.plweegie.android.bluewisdom.adapters

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.plweegie.android.bluewisdom.OnDeviceSelectedListener
import com.plweegie.android.bluewisdom.R
import kotlinx.android.synthetic.main.device_list_item.view.*


class LeDeviceAdapter(private val context: Context) : RecyclerView.Adapter<LeDeviceAdapter.LeDeviceViewHolder>() {

    private var devices: MutableList<BluetoothDevice> = mutableListOf()
    private var onDeviceSelectedListener = context as OnDeviceSelectedListener

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeDeviceViewHolder {
        val inflater = LayoutInflater.from(context)
        return LeDeviceViewHolder(inflater, parent, R.layout.device_list_item)
    }

    override fun onBindViewHolder(holder: LeDeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount(): Int  = devices.size

    fun addDevice(device: BluetoothDevice?) {
        device?.let {
            devices.add(it)
            notifyDataSetChanged()
        }
    }

    inner class LeDeviceViewHolder(inflater: LayoutInflater, parent: ViewGroup?, layoutResId: Int) :
            RecyclerView.ViewHolder(inflater.inflate(layoutResId, parent, false)) {

        fun bind(device: BluetoothDevice) {
            itemView.apply {
                device_name_tv.text = device.name
                device_address_tv.text = device.address

                setOnClickListener {
                    onDeviceSelectedListener.onDeviceSelected(devices[adapterPosition])
                }
            }
        }
    }
}