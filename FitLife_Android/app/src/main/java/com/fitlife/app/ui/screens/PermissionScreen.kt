package com.fitlife.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitlife.app.ui.components.*
import com.fitlife.app.ui.theme.FitTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState

/**
 * Asks for the permissions needed on day one. Location is deliberately NOT requested here:
 * it is asked for just-in-time when the user starts their first ride.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionScreen(onDone: () -> Unit) {
    val permissions = buildList {
        // ACTIVITY_RECOGNITION only exists as a runtime permission from Android 10.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (permissions.isEmpty()) { LaunchedEffect(Unit) { onDone() }; return }

    val state = rememberMultiplePermissionsState(permissions) { onDone() }
    LaunchedEffect(state.allPermissionsGranted) { if (state.allPermissionsGranted) onDone() }

    fun granted(p: String) = state.permissions.firstOrNull { it.permission == p }?.status?.isGranted == true

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("A couple of permissions", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("FitLife works without them, but these make tracking automatic.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) PermissionRow("👟", "Physical activity",
            "Counts your steps with the phone's step sensor.", granted(Manifest.permission.ACTIVITY_RECOGNITION))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) PermissionRow("🔔", "Notifications",
            "Step and water reminders, plus live stats while a ride records.", granted(Manifest.permission.POST_NOTIFICATIONS))
        PermissionRow("📍", "Location — later", "Asked only when you start your first ride. Used only while recording.", null)

        Spacer(Modifier.height(24.dp))
        PrimaryButton("Allow", onClick = { state.launchMultiplePermissionRequest() })
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Skip for now", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PermissionRow(emoji: String, title: String, body: String, granted: Boolean?) {
    FitCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(color = MaterialTheme.colorScheme.primary, emoji = emoji)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (granted == true) Icon(Icons.Filled.CheckCircle, "Granted", tint = FitTheme.colors.success)
        }
    }
}
