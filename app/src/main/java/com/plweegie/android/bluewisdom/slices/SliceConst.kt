package com.plweegie.android.bluewisdom.slices

import com.plweegie.android.bluewisdom.R

class SliceConst {

    companion object {
        const val HEALTH_STAT_NAME = "characteristic"
    }

    enum class Characteristic(val nameId: Int) {
        HEART_RATE(R.string.heart_rate),
        UNKNOWN(R.string.unknown);

        companion object {
            fun find(type: String): Characteristic =
                    values().find { it.name.equals(other = type, ignoreCase = true) } ?: UNKNOWN
        }
    }
}