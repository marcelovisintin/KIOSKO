package com.schneider.kiosko

import android.graphics.drawable.Drawable

data class AllowedApp(
    val id: String,
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val launchUrl: String? = null,
)
