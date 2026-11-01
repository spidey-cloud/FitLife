package com.fitlife.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionScreen(onAllGranted: () -> Unit) {

    // Build the list of permissions we need
    val permissions = mutableListOf(
        Manifest.permission.ACTIVITY_RECOGNITION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    val multiplePermissionsState = rememberMultiplePermissionsState(permissions) { results ->
        if (results[Manifest.permission.ACTIVITY_RECOGNITION] == true &&
            results[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            onAllGranted()
        }
    }

    // If already granted, skip straight to app
    LaunchedEffect(multiplePermissionsState.allPermissionsGranted) {
        if (multiplePermissionsState.allPermissionsGranted) onAllGranted()
    }

    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("F", fontSize = 40.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("Permissions needed", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("FitLife needs these permissions for activity tracking, location mapping, and reminders.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)

        Spacer(Modifier.height(32.dp))

        // Permission items
        PermissionItem(
            icon = Icons.Outlined.DirectionsWalk,
            title = "Physical Activity",
            description = "Required to count your steps automatically using device sensors.",
            granted = multiplePermissionsState.permissions
                .firstOrNull { it.permission == Manifest.permission.ACTIVITY_RECOGNITION }
                ?.status?.isGranted ?: false
        )

        Spacer(Modifier.height(12.dp))

        PermissionItem(
            icon = Icons.Outlined.LocationOn,
            title = "Location & GPS",
            description = "Required for accurate route mapping and live speed tracking during cycling trips.",
            granted = multiplePermissionsState.permissions
                .firstOrNull { it.permission == Manifest.permission.ACCESS_FINE_LOCATION }
                ?.status?.isGranted ?: false
        )

        Spacer(Modifier.height(12.dp))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionItem(
                icon = Icons.Outlined.Notifications,
                title = "Notifications",
                description = "For workout alerts, trip tracking updates, and reminders.",
                granted = multiplePermissionsState.permissions
                    .firstOrNull { it.permission == Manifest.permission.POST_NOTIFICATIONS }
                    ?.status?.isGranted ?: false
            )
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(16.dp))

        // Grant button
        Button(
            onClick = { multiplePermissionsState.launchMultiplePermissionRequest() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Grant Permissions", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun PermissionItem(icon: ImageVector, title: String, description: String, granted: Boolean) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (granted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(2.dp))
                Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
