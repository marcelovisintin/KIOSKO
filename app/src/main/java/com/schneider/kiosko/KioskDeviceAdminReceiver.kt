package com.schneider.kiosko

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class KioskDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Admin de dispositivo habilitado", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Deshabilitar el admin puede sacar el dispositivo del modo kiosco."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Admin de dispositivo deshabilitado", Toast.LENGTH_SHORT).show()
    }
}
