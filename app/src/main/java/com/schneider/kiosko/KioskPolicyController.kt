package com.schneider.kiosko

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.UserManager

sealed interface PolicyApplyResult {
    data object DeviceOwnerMissing : PolicyApplyResult
    data class Applied(val packages: Set<String>) : PolicyApplyResult
    data class Failed(val message: String) : PolicyApplyResult
}

class KioskPolicyController(private val context: Context) {

    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(context, KioskDeviceAdminReceiver::class.java)

    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(context.packageName)

    fun isLockTaskPermittedForSelf(): Boolean = dpm.isLockTaskPermitted(context.packageName)

    fun releaseKioskForAdminExit() {
        if (!isDeviceOwner()) return

        dpm.setLockTaskPackages(admin, emptyArray())
        dpm.clearPackagePersistentPreferredActivities(admin, context.packageName)
        dpm.setStatusBarDisabled(admin, false)
        dpm.setKeyguardDisabled(admin, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dpm.setLockTaskFeatures(
                admin,
                DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                    DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW or
                    DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS or
                    DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
                    DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS,
            )
        }

        dpm.clearUserRestriction(admin, UserManager.DISALLOW_CREATE_WINDOWS)
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY)
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
    }

    fun apply(config: KioskConfig): PolicyApplyResult {
        if (!isDeviceOwner()) {
            return PolicyApplyResult.DeviceOwnerMissing
        }

        return try {
            dpm.setLockTaskPackages(admin, config.allowedPackages.toTypedArray())
            setKioskAsHomeApp()
            dpm.setStatusBarDisabled(admin, !config.allowSystemUi)
            dpm.setKeyguardDisabled(admin, !config.allowSystemUi)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val features = if (config.allowSystemUi) {
                    DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                        DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW or
                        DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS or
                        DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
                        DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
                } else {
                    // Keep only HOME enabled so users can return to kiosk without closing foreground apps.
                    DevicePolicyManager.LOCK_TASK_FEATURE_HOME
                }
                dpm.setLockTaskFeatures(admin, features)
            }

            applyHardening(config.adminModeEnabled)
            PolicyApplyResult.Applied(config.allowedPackages)
        } catch (se: SecurityException) {
            PolicyApplyResult.Failed(se.message ?: "SecurityException aplicando politicas")
        } catch (ex: Exception) {
            PolicyApplyResult.Failed(ex.message ?: "Error inesperado aplicando politicas")
        }
    }

    private fun applyHardening(adminModeEnabled: Boolean) {
        dpm.addUserRestriction(admin, UserManager.DISALLOW_CREATE_WINDOWS)
        dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
        dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
        dpm.addUserRestriction(admin, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
        dpm.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
        dpm.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY)

        if (adminModeEnabled || isDebuggableBuild()) {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
        } else {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
        }
    }

    private fun isDebuggableBuild(): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private fun setKioskAsHomeApp() {
        val filter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        dpm.addPersistentPreferredActivity(
            admin,
            filter,
            ComponentName(context, MainActivity::class.java),
        )
    }
}
