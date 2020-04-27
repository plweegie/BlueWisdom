package com.plweegie.android.bluewisdom

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.service.voice.VoiceInteractionService
import androidx.slice.SliceManager


class App : Application() {

    val appComponent : AppComponent by lazy {
        DaggerAppComponent.builder()
                .appModule(AppModule(this))
                .build()
    }

    override fun onCreate() {
        super.onCreate()
        grantSlicePermissions()
    }

    private fun grantSlicePermissions() {
        val sliceProviderUri = Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(packageName)
                .build()

        val assistantPackage = getAssistantPackage(applicationContext) ?: return
        SliceManager.getInstance(applicationContext).grantSlicePermission(assistantPackage, sliceProviderUri)
    }

    private fun getAssistantPackage(context: Context): String? {
        val packageManager = context.packageManager
        val resolveInfoList = packageManager.queryIntentServices(
                Intent(VoiceInteractionService.SERVICE_INTERFACE), 0)

        return resolveInfoList[0]?.serviceInfo?.packageName
    }
}