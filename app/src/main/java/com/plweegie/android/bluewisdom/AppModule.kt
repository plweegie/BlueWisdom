package com.plweegie.android.bluewisdom

import android.app.Application
import android.content.Context
import com.polidea.rxandroidble2.RxBleClient
import dagger.Module
import dagger.Provides
import javax.inject.Singleton


@Module
class AppModule(private val app: Application) {

    @Provides
    @Singleton
    fun provideApplication(): Application = app

    @Provides
    @Singleton
    fun provideApplicationContext(): Context = app.applicationContext

    @Provides
    @Singleton
    fun provideBleClient(context: Context): RxBleClient = RxBleClient.create(context)
}