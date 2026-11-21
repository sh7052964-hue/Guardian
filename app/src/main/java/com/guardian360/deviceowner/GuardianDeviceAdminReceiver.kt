package com.guardian360.deviceowner

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class GuardianDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i("GuardianDeviceAdmin", "Device Admin enabled for Guardian360")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w("GuardianDeviceAdmin", "Device Admin disabled for Guardian360")
    }
}
