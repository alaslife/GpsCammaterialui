package com.alas.md3gpscam.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alas.md3gpscam.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onNavigateToPrivacy: () -> Unit) {
    val scrollState = rememberScrollState()

    // Observe settings
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val hapticFeedbackEnabled by viewModel.hapticFeedbackEnabled.collectAsState()
    val gridLinesEnabled by viewModel.gridLinesEnabled.collectAsState()
    val speedUnit by viewModel.speedUnit.collectAsState()
    val altitudeUnit by viewModel.altitudeUnit.collectAsState()
    val recordAudioInVideo by viewModel.recordAudioInVideo.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicColorsEnabled by viewModel.dynamicColorsEnabled.collectAsState()
    val photoQuality by viewModel.photoQuality.collectAsState()
    val videoQuality by viewModel.videoQuality.collectAsState()
    val stampMapType by viewModel.stampMapType.collectAsState()

    var showClearDataDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showAltitudeDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showPhotoQualityDialog by remember { mutableStateOf(false) }
    var showVideoQualityDialog by remember { mutableStateOf(false) }
    var showStampMapDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        val customNoteVal by viewModel.customNote.collectAsState()
        var showNoteEditDialog by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Camera Settings Section
            SettingsSectionHeader(title = "Camera Capture")
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    SettingsSwitchRow(
                        icon = Icons.Rounded.VolumeUp,
                        title = "Shutter Sound",
                        subtitle = "Play a camera capture sound when taking photos",
                        checked = soundEnabled,
                        onCheckedChange = { viewModel.setSound(it) }
                    )

                    SettingsSwitchRow(
                        icon = Icons.Rounded.Vibration,
                        title = "Haptic Feedback",
                        subtitle = "Vibrate device gently on shutter press and settings clicks",
                        checked = hapticFeedbackEnabled,
                        onCheckedChange = { viewModel.setHaptics(it) }
                    )

                    SettingsSwitchRow(
                        icon = Icons.Rounded.Grid3x3,
                        title = "Camera Grid Lines",
                        subtitle = "Overlay vertical and horizontal rule-of-thirds grid",
                        checked = gridLinesEnabled,
                        onCheckedChange = { viewModel.setGridLines(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Capture & Quality Settings Section
            SettingsSectionHeader(title = "Capture & Quality")
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    SettingsClickPickerRow(
                        icon = Icons.Rounded.Camera,
                        title = "Photo Quality",
                        subtitle = "Select resolution and size scaling for captured photos",
                        value = when (photoQuality) {
                            "high" -> "High (Full Res)"
                            "low" -> "Low (1080px)"
                            else -> "Medium (2048px)"
                        },
                        onClick = { showPhotoQualityDialog = true }
                    )

                    SettingsClickPickerRow(
                        icon = Icons.Rounded.Videocam,
                        title = "Video Quality",
                        subtitle = "Select video recording resolution",
                        value = when (videoQuality) {
                            "1080p" -> "Full HD (1080p)"
                            "480p" -> "SD (480p)"
                            else -> "HD (720p)"
                        },
                        onClick = { showVideoQualityDialog = true }
                    )

                    SettingsClickPickerRow(
                        icon = Icons.Rounded.Map,
                        title = "Stamp Map Theme",
                        subtitle = "Customize the mini-map style embedded in your watermark",
                        value = when (stampMapType) {
                            "satellite" -> "Satellite"
                            "dark" -> "Dark Mode"
                            "grey" -> "Minimal Grey"
                            else -> "Standard"
                        },
                        onClick = { showStampMapDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Theme & Customization Section
            SettingsSectionHeader(title = "Theme & Customization")
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    SettingsClickPickerRow(
                        icon = Icons.Rounded.Palette,
                        title = "App Theme",
                        subtitle = "Choose light, dark, or system default color scheme",
                        value = when (themeMode) {
                            "light" -> "Light"
                            "dark" -> "Dark"
                            else -> "System Default"
                        },
                        onClick = { showThemeDialog = true }
                    )

                    SettingsSwitchRow(
                        icon = Icons.Rounded.ColorLens,
                        title = "Dynamic Colors (Material You)",
                        subtitle = "Use your system's dynamic color palette on Android 12+",
                        checked = dynamicColorsEnabled,
                        onCheckedChange = { viewModel.setDynamicColorsEnabled(it) }
                    )

                    SettingsClickPickerRow(
                        icon = Icons.Rounded.EditNote,
                        title = "Default Stamp Tag",
                        subtitle = "Set a persistent custom note or tag overlay",
                        value = if (customNoteVal.isBlank()) "None" else customNoteVal,
                        onClick = { showNoteEditDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Units and Formats Section
            SettingsSectionHeader(title = "Units & Telemetry Formats")
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    SettingsClickPickerRow(
                        icon = Icons.Rounded.Speed,
                        title = "Speed Unit",
                        subtitle = "Choose standard measurement for movement velocity",
                        value = if (speedUnit == "kmh") "km/h" else "mph",
                        onClick = { showSpeedDialog = true }
                    )

                    SettingsClickPickerRow(
                        icon = Icons.Rounded.Height,
                        title = "Altitude Unit",
                        subtitle = "Choose standard measurement for altitude heights",
                        value = if (altitudeUnit == "meters") "Meters (m)" else "Feet (ft)",
                        onClick = { showAltitudeDialog = true }
                    )

                    SettingsSwitchRow(
                        icon = Icons.Rounded.Mic,
                        title = "Record Audio in Video",
                        subtitle = "Capture environmental audio when recording video clips",
                        checked = recordAudioInVideo,
                        onCheckedChange = { viewModel.setRecordAudioInVideo(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Storage and Safety Section
            SettingsSectionHeader(title = "Privacy & Storage")
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    SettingsActionRow(
                        icon = Icons.Rounded.DeleteForever,
                        title = "Reset Application Data",
                        subtitle = "Permanently delete all geotagged photos & videos from history",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = { showClearDataDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Info Section
            SettingsSectionHeader(title = "About GPS Camera")
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    SettingsInfoRow(
                        icon = Icons.Rounded.Info,
                        title = "Version",
                        value = "3.0.0 (Material Glass Build)"
                    )

                    SettingsInfoRow(
                        icon = Icons.Rounded.Favorite,
                        title = "Developer",
                        value = "dev Alas"
                    )

                    SettingsInfoRow(
                        icon = Icons.Rounded.LocationOn,
                        title = "Origin",
                        value = "Made with love in India"
                    )

                    SettingsClickPickerRow(
                        icon = Icons.Rounded.VerifiedUser,
                        title = "Privacy Policy",
                        subtitle = "View our privacy policy",
                        value = "",
                        onClick = onNavigateToPrivacy
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Custom note tag editor dialog
        if (showNoteEditDialog) {
            var tempNoteText by remember { mutableStateOf(customNoteVal) }
            AlertDialog(
                onDismissRequest = { showNoteEditDialog = false },
                title = { Text("Watermark Default Tag", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Write a default label to overlay on photos & videos. Can be overridden in Camera.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = tempNoteText,
                            onValueChange = { tempNoteText = it },
                            placeholder = { Text("e.g. Site A - Survey") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                if (tempNoteText.isNotEmpty()) {
                                    IconButton(onClick = { tempNoteText = "" }) {
                                        Icon(Icons.Rounded.Clear, "Clear")
                                    }
                                }
                            }
                        )
                        Text("Suggestions:", style = MaterialTheme.typography.labelMedium)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("Survey", "Inspection", "Travel", "Work").forEach { suggestion ->
                                FilterChip(
                                    selected = tempNoteText == suggestion,
                                    onClick = { tempNoteText = suggestion },
                                    label = { Text(suggestion) }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.setCustomNote(tempNoteText)
                            showNoteEditDialog = false
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNoteEditDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Reset verification dialog
        if (showClearDataDialog) {
            AlertDialog(
                onDismissRequest = { showClearDataDialog = false },
                title = { Text("Reset App Data", color = MaterialTheme.colorScheme.error) },
                text = { Text("Are you sure you want to permanently delete all photo and video database entries? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.allPhotos.value.forEach {
                                viewModel.deletePhoto(it)
                            }
                            showClearDataDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Reset All")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDataDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Photo Quality Picker Dialog
        if (showPhotoQualityDialog) {
            UnitPickerDialog(
                title = "Select Photo Quality",
                selectedValue = photoQuality,
                options = listOf(
                    "high" to "High (Full Resolution)",
                    "medium" to "Medium (2048px - Recommended)",
                    "low" to "Low (1080px - Fastest)"
                ),
                onDismiss = { showPhotoQualityDialog = false },
                onSelect = {
                    viewModel.setPhotoQuality(it)
                    showPhotoQualityDialog = false
                }
            )
        }

        // Video Quality Picker Dialog
        if (showVideoQualityDialog) {
            UnitPickerDialog(
                title = "Select Video Quality",
                selectedValue = videoQuality,
                options = listOf(
                    "1080p" to "Full HD (1080p)",
                    "720p" to "HD (720p - Recommended)",
                    "480p" to "SD (480p)"
                ),
                onDismiss = { showVideoQualityDialog = false },
                onSelect = {
                    viewModel.setVideoQuality(it)
                    showVideoQualityDialog = false
                }
            )
        }

        // Stamp Map Theme Picker Dialog
        if (showStampMapDialog) {
            UnitPickerDialog(
                title = "Select Stamp Map Theme",
                selectedValue = stampMapType,
                options = listOf(
                    "standard" to "Standard (Light Green)",
                    "satellite" to "Satellite (Contour & Amber)",
                    "dark" to "Dark Mode (AMOLED & Cyan)",
                    "grey" to "Minimal Grey (Clean Grey)"
                ),
                onDismiss = { showStampMapDialog = false },
                onSelect = {
                    viewModel.setStampMapType(it)
                    showStampMapDialog = false
                }
            )
        }

        // Speed Unit Picker Dialog
        if (showSpeedDialog) {
            UnitPickerDialog(
                title = "Select Speed Unit",
                selectedValue = speedUnit,
                options = listOf("kmh" to "Kilometers per hour (km/h)", "mph" to "Miles per hour (mph)"),
                onDismiss = { showSpeedDialog = false },
                onSelect = {
                    viewModel.setSpeedUnit(it)
                    showSpeedDialog = false
                }
            )
        }

        // Altitude Unit Picker Dialog
        if (showAltitudeDialog) {
            UnitPickerDialog(
                title = "Select Altitude Unit",
                selectedValue = altitudeUnit,
                options = listOf("meters" to "Meters (m)", "feet" to "Feet (ft)"),
                onDismiss = { showAltitudeDialog = false },
                onSelect = {
                    viewModel.setAltitudeUnit(it)
                    showAltitudeDialog = false
                }
            )
        }

        // Theme Selection Dialog
        if (showThemeDialog) {
            UnitPickerDialog(
                title = "Select App Theme",
                selectedValue = themeMode,
                options = listOf(
                    "system" to "System Default",
                    "light" to "Light Theme",
                    "dark" to "Dark Theme"
                ),
                onDismiss = { showThemeDialog = false },
                onSelect = {
                    viewModel.setThemeMode(it)
                    showThemeDialog = false
                }
            )
        }
    }
}

@Composable
fun UnitPickerDialog(
    title: String,
    selectedValue: String,
    options: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
            ) {
                options.forEach { option ->
                    val isSelected = option.first == selectedValue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                onClick = { onSelect(option.first) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null // Selected handled by Row selectable
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(option.second, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        ),
        modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
    )
}

@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun SettingsClickPickerRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier.size(24.dp),
            tint = tint
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = tint))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun SettingsInfoRow(
    icon: ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
