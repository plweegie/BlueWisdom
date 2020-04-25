package com.plweegie.android.bluewisdom

import android.app.Application


class App : Application() {

    val appComponent : AppComponent by lazy {
        DaggerAppComponent.builder()
                .appModule(AppModule(this))
                .build()
    }
}