package com.plweegie.android.bluewisdom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.plweegie.android.bluewisdom.services.LeConnectionService
import kotlinx.android.synthetic.main.activity_le_connection.*


class LeConnectionActivity : AppCompatActivity() {

    private lateinit var macAddress: String

    companion object {
        fun newIntent(context: Context, macAddress: String) =
                Intent(context, LeConnectionActivity::class.java).apply {
                    putExtra(LeConnectionService.MAC_ADDRESS_EXTRA, macAddress)
                }
    }

    private val resultReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                LeConnectionService.ACTION_TEMPERATURE_AVAILABLE -> {
                    temperature_text?.text = intent.getStringExtra(LeConnectionService.EXTRA_DATA)
                }
                LeConnectionService.ACTION_PRESSURE_AVAILABLE -> {
                    pressure_text?.text = intent.getStringExtra(LeConnectionService.EXTRA_DATA)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_le_connection)

        val intentFilter = IntentFilter(LeConnectionService.ACTION_TEMPERATURE_AVAILABLE)
        intentFilter.addAction(LeConnectionService.ACTION_PRESSURE_AVAILABLE)

        LocalBroadcastManager.getInstance(this).registerReceiver(resultReceiver, intentFilter)

        macAddress = intent.getStringExtra(LeConnectionService.MAC_ADDRESS_EXTRA) ?: ""
        startService(LeConnectionService.newIntent(this, macAddress))
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(resultReceiver)
        stopService((LeConnectionService.newIntent(this, macAddress)))
        super.onDestroy()
    }
}