package com.networkanalyst.app.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun PermissionRationaleDialog(
    permissionType: PermissionType,
    onDismiss: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current

    val title = when (permissionType) {
        PermissionType.LOCATION_FOR_WIFI -> "Yêu cầu quyền Vị trí chính xác"
        PermissionType.NEARBY_DEVICES -> "Yêu cầu quyền Thiết bị lân cận"
    }

    val description = when (permissionType) {
        PermissionType.LOCATION_FOR_WIFI ->
            "Từ Android 8.0 trở lên, hệ điều hành Android bắt buộc quyền Vị trí (ACCESS_FINE_LOCATION) để đọc được tên mạng Wi-Fi (SSID) và địa chỉ BSSID của router.\n\n" +
            "Ứng dụng Network Analyst hoàn toàn KHÔNG theo dõi hoặc gửi vị trí thực của bạn ra ngoài."

        PermissionType.NEARBY_DEVICES ->
            "Trên Android 13 trở lên, quyền Thiết bị Wi-Fi lân cận (NEARBY_WIFI_DEVICES) cho phép ứng dụng khám phá và hiển thị các thiết bị cùng mạng Wi-Fi qua giao thức mDNS/multicast."
    }

    val icon = when (permissionType) {
        PermissionType.LOCATION_FOR_WIFI -> Icons.Default.LocationOn
        PermissionType.NEARBY_DEVICES -> Icons.Default.Wifi
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
                Text("Cấp quyền")
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
                Text("Mở Cài đặt")
            }
        }
    )
}

enum class PermissionType {
    LOCATION_FOR_WIFI,
    NEARBY_DEVICES
}
