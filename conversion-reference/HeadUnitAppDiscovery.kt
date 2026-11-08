package com.mekxz.drive.compat

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Head-unit app discovery reference implementation.
 * Not yet wired into the existing Glass TV Home UI.
 * Uses MAIN + LAUNCHER rather than TV-only LEANBACK_LAUNCHER.
 */
object HeadUnitAppDiscovery {
    data class LaunchableApp(val packageName: String, val label: String)

    @Suppress("DEPRECATION")
    fun listLaunchable(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = pm.queryIntentActivities(intent, 0)
        return matches.map { item ->
            LaunchableApp(item.activityInfo.packageName, item.loadLabel(pm).toString())
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    fun launch(context: Context, packageName: String): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launchIntent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
