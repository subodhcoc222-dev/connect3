package com.deskconnect.companion.presentation.components

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deskconnect.companion.presentation.theme.*
import com.deskconnect.companion.receiver.DeskAdminReceiver

@Composable
fun PermissionSection(
    onPermissionsUpdated: () -> Unit
) {
    val context = LocalContext.current

    val hasAdmin = remember(context) { checkDeviceAdmin(context) }
    val hasOverlay = remember(context) { Settings.canDrawOverlays(context) }
    val hasBatteryOpt = remember(context) { checkBatteryOptimization(context) }

    val allGranted = hasAdmin && hasOverlay && hasBatteryOpt

    if (!allGranted) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = "REQUIRED PERMISSIONS",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(visible = !hasAdmin, exit = shrinkVertically()) {
                PermissionCard(
                    title = "Device Administrator",
                    description = "Prevents tampering & unauthorized uninstallation",
                    buttonText = "Activate Admin",
                    onClick = {
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(
                                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                ComponentName(context, DeskAdminReceiver::class.java)
                            )
                            putExtra(
                                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                "Required to lock uninstallation of DeskConnect Companion."
                            )
                        }
                        context.startActivity(intent)
                    }
                )
            }

            AnimatedVisibility(visible = !hasOverlay, exit = shrinkVertically()) {
                PermissionCard(
                    title = "Display Over Other Apps",
                    description = "Required to launch Alarmy-style overlay over all apps",
                    buttonText = "Grant Overlay",
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                )
            }

            AnimatedVisibility(visible = !hasBatteryOpt, exit = shrinkVertically()) {
                PermissionCard(
                    title = "Ignore Battery Optimizations",
                    description = "Guarantees 24/7 background watchdog survival without OS kill",
                    buttonText = "Disable Optimization",
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(SlateSurface, RoundedCornerShape(12.dp))
            .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(text = description, color = TextMuted, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(text = buttonText, color = SlateDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun checkDeviceAdmin(context: Context): Boolean {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(context, DeskAdminReceiver::class.java)
    return dpm.isAdminActive(adminComponent)
}

private fun checkBatteryOptimization(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}
