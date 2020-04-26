package com.plweegie.android.bluewisdom.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.polidea.rxandroidble2.RxBleClient
import javax.inject.Provider

class LeConnectionViewModelFactory(private val bleClient: Provider<RxBleClient>) : ViewModelProvider.NewInstanceFactory() {

    override fun <T : ViewModel?> create(modelClass: Class<T>): T = LeConnectionViewModel(bleClient.get()) as T
}