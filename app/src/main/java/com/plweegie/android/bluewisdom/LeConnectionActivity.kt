package com.plweegie.android.bluewisdom

import android.content.*
import android.os.Bundle
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.plweegie.android.bluewisdom.services.LeConnectionService
import kotlinx.android.synthetic.main.activity_le_connection.*


class LeConnectionActivity : AppCompatActivity() {

    private var macAddress: String? = null
    private var lecService: LeConnectionService? = null

    companion object {
        fun newIntent(context: Context, macAddress: String) =
                Intent(context, LeConnectionActivity::class.java).apply {
                    putExtra(LeConnectionService.MAC_ADDRESS_EXTRA, macAddress)
                }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName?, service: IBinder?) {
            val binder = service as LeConnectionService.LocalBinder
            lecService = binder.getService()

            if (lecService?.initialize() == false) {
                finish()
            }
            lecService?.connect(macAddress)
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            lecService = null
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

        macAddress = intent.getStringExtra(LeConnectionService.MAC_ADDRESS_EXTRA)

        Intent(this, LeConnectionService::class.java).also {
            bindService(it, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onResume() {
        super.onResume()

        val intentFilter = IntentFilter(LeConnectionService.ACTION_TEMPERATURE_AVAILABLE)
        intentFilter.addAction(LeConnectionService.ACTION_PRESSURE_AVAILABLE)

        LocalBroadcastManager.getInstance(this).registerReceiver(resultReceiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(resultReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(connection)
        lecService = null
    }
}