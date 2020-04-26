package com.plweegie.android.bluewisdom

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.plweegie.android.bluewisdom.viewmodel.LeConnectionViewModel
import com.plweegie.android.bluewisdom.viewmodel.LeConnectionViewModelFactory
import kotlinx.android.synthetic.main.activity_le_connection.*
import javax.inject.Inject


class LeConnectionActivity : AppCompatActivity() {

    private var macAddress: String? = null

    @Inject
    lateinit var viewModelFactory: LeConnectionViewModelFactory

    private lateinit var viewModel: LeConnectionViewModel

    companion object {
        private const val MAC_ADDRESS_EXTRA = "extra_mac_address"

        fun newIntent(context: Context, macAddress: String) =
                Intent(context, LeConnectionActivity::class.java).apply {
                    putExtra(MAC_ADDRESS_EXTRA, macAddress)
                }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as App).appComponent.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_le_connection)

        macAddress = intent.getStringExtra(MAC_ADDRESS_EXTRA)

        viewModel = ViewModelProvider(this, viewModelFactory)[LeConnectionViewModel::class.java]

        macAddress?.let {
            viewModel.connect(it)
        }

        viewModel.temperatureLiveData.observe(this, Observer {
            temperature_text?.text = it.toString()
        })
    }
}