package com.networkanalyst.app.ui.components

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.networkanalyst.app.R

object WarningPreferences {
    private const val PREFS_NAME = "network_analyst_prefs"
    private const val KEY_SCAN_WARNING_ACCEPTED = "key_scan_warning_accepted"

    fun isWarningAccepted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SCAN_WARNING_ACCEPTED, false)
    }

    fun setWarningAccepted(context: Context, accepted: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SCAN_WARNING_ACCEPTED, accepted).apply()
    }
}

object ScanPolicyHelper {
    fun shouldShowWarning(isAccepted: Boolean): Boolean = !isAccepted
    fun canProceedWithScan(isAccepted: Boolean): Boolean = isAccepted
}

@Composable
fun NetworkScanWarningDialog(
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = stringResource(R.string.scan_warning_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = stringResource(R.string.scan_warning_message)
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    WarningPreferences.setWarningAccepted(context, true)
                    onConfirmed()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.scan_warning_confirm))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
