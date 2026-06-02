package com.example.util

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager

object ServiceUtils {
    /**
     * Checks if the specified Accessibility Service is currently enabled and active in Android.
     */
    fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<out AccessibilityService>): Boolean {
        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            if (enabledServices != null) {
                for (enabledService in enabledServices) {
                    val serviceInfo = enabledService.resolveInfo?.serviceInfo
                    if (serviceInfo != null) {
                        if (serviceInfo.packageName == context.packageName && serviceInfo.name == serviceClass.name) {
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Safe fallback
        }
        
        // Also check using settings secure string just in case as a fallback
        try {
            val settingValue = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                val serviceName = "${context.packageName}/${serviceClass.name}"
                return settingValue.contains(serviceName) || settingValue.contains(context.packageName)
            }
        } catch (e: Exception) {
            // Fallback fail
        }
        return false
    }
}
