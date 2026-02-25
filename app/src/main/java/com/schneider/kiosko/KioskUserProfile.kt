package com.schneider.kiosko

data class KioskUserProfile(
    val id: String,
    val name: String,
    val pin: String,
    val allowedPackages: Set<String>,
)
