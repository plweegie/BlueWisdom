package com.plweegie.android.bluewisdom.slices

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.preference.PreferenceManager
import androidx.slice.Slice
import androidx.slice.SliceProvider
import androidx.slice.builders.ListBuilder
import androidx.slice.builders.SliceAction
import androidx.slice.builders.list
import androidx.slice.builders.row
import com.plweegie.android.bluewisdom.LeConnectionActivity
import com.plweegie.android.bluewisdom.R
import com.plweegie.android.bluewisdom.viewmodel.LeConnectionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class BleSliceProvider : SliceProvider() {

    companion object {
        private const val PATH = "/temp"

        fun getUri(context: Context, path: String): Uri =
                Uri.Builder()
                        .scheme(ContentResolver.SCHEME_CONTENT)
                        .authority(context.packageName)
                        .appendPath(path)
                        .build()

        fun getEncodedUri(context: Context, path: String): Uri =
                Uri.Builder()
                        .scheme(ContentResolver.SCHEME_CONTENT)
                        .encodedAuthority(context.packageName)
                        .appendEncodedPath(path)
                        .build()
    }

    private lateinit var currentContext: Context

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == LeConnectionViewModel.TEMPERATURE_PREFERENCE) {
            currentContext.contentResolver.notifyChange(getUri(currentContext, PATH), null)
            onBindSlice(getEncodedUri(currentContext, PATH))
        }
    }

    private var temperature: Float = 0.0f

    override fun onCreateSliceProvider(): Boolean {
        currentContext = context ?: return false
        return true
    }

    override fun onBindSlice(sliceUri: Uri): Slice? {
        return when (sliceUri.path) {
            PATH -> createBleSlice(sliceUri)
            else -> null
        }
    }

    private fun createBleSlice(sliceUri: Uri): Slice {
        val characteristicName = SliceConst.Characteristic
                .find(sliceUri.getQueryParameter(SliceConst.HEALTH_STAT_NAME).orEmpty())

        temperature = if (characteristicName == SliceConst.Characteristic.HEART_RATE) {
            runBlocking { getTemperatureValue() }
        } else -1.0f

        val iconColor = when {
            temperature < 30 -> R.color.colorPrimaryDark
            else -> R.color.colorRed
        }

        return list(currentContext, sliceUri, ListBuilder.INFINITY) {
            setAccentColor(ContextCompat.getColor(currentContext, iconColor))

            row {
                title = currentContext.getString(R.string.slice_action)
                subtitle = currentContext.getString(R.string.temperature_report, temperature)

                addEndItem(IconCompat.createWithResource(currentContext, R.drawable.ic_baseline_wb_sunny_24),
                        ListBuilder.ICON_IMAGE)

                primaryAction = SliceAction.create(
                        PendingIntent.getActivity(
                                currentContext,
                                sliceUri.hashCode(),
                                Intent(currentContext, LeConnectionActivity::class.java),
                                0
                        ),
                        IconCompat.createWithResource(currentContext, R.drawable.ic_baseline_headset_24),
                        ListBuilder.ICON_IMAGE,
                        currentContext.getString(R.string.connect_yes)
                )
            }
        }

    }

    override fun onSlicePinned(sliceUri: Uri?) {
        PreferenceManager.getDefaultSharedPreferences(currentContext)
                .registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onSliceUnpinned(sliceUri: Uri?) {
        PreferenceManager.getDefaultSharedPreferences(currentContext)
                .unregisterOnSharedPreferenceChangeListener(listener)
    }

    private suspend fun getTemperatureValue(): Float =
            withContext(Dispatchers.IO) {
                PreferenceManager.getDefaultSharedPreferences(currentContext)
                        .getFloat(LeConnectionViewModel.TEMPERATURE_PREFERENCE, 0.0f)
            }
}