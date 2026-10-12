package com.example.budgetapptracker.pay

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri

data class UpiApp(val packageName: String, val label: String, val icon: Drawable)

// Asks Android which installed apps can open a UPI payment link (GPay, PhonePe, Paytm and so on).
fun installedUpiApps(context: Context): List<UpiApp> {
    val packageManager = context.packageManager
    val probe = Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay"))
    return packageManager.queryIntentActivities(probe, 0)
        .map {
            UpiApp(
                packageName = it.activityInfo.packageName,
                label = it.loadLabel(packageManager).toString(),
                icon = it.loadIcon(packageManager),
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}
