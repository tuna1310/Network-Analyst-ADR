package com.networkanalyst.app.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.networkanalyst.app.R

@Composable
fun PermissionRationaleDialog(
    permissionType: PermissionType,
    onDismiss: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current

    val title = when (permissionType) {
        PermissionType.LOCATION_FOR_WIFI -> stringResource(R.string.perm_location_title)
        PermissionType.NEARBY_DEVICES -> stringResource(R.string.perm_nearby_title)
        PermissionType.READ_PHONE_STATE -> stringResource(R.string.perm_phone_state_title)
    }

    val description = when (permissionType) {
        PermissionType.LOCATION_FOR_WIFI -> stringResource(R.string.perm_location_desc)
        PermissionType.NEARBY_DEVICES -> stringResource(R.string.perm_nearby_desc)
        PermissionType.READ_PHONE_STATE -> stringResource(R.string.perm_phone_state_desc)
    }

    val icon = when (permissionType) {
        PermissionType.LOCATION_FOR_WIFI -> Icons.Default.LocationOn
        PermissionType.NEARBY_DEVICES -> Icons.Default.Wifi
        PermissionType.READ_PHONE_STATE -> Icons.Default.PhoneAndroid
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(text = title) },
        text = { Text(text = description) },
        confirmButton = {
            Button(onClick = {
                onDismiss()
                onRequestPermission()
            }) {
                Text(stringResource(R.string.perm_btn_grant))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = {
                onDismiss()
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }) {
                Text(stringResource(R.string.perm_btn_settings))
            }
        }
    )
}

enum class PermissionType {
    LOCATION_FOR_WIFI,
    NEARBY_DEVICES,
    READ_PHONE_STATE
}
