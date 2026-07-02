package com.alas.md3gpscam.ui.screens

import android.content.Intent
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.alas.md3gpscam.data.database.PhotoEntity
import com.alas.md3gpscam.ui.MainViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onNavigateToCamera: () -> Unit,
    onNavigateToGallery: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToTemplates: () -> Unit,
    onPhotoClick: (PhotoEntity) -> Unit
) {
    val context = LocalContext.current
    val recentPhotos by viewModel.recentPhotos.collectAsState()
    val allPhotos by viewModel.allPhotos.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val deviceBearing by viewModel.deviceBearing.collectAsState()

    val scrollState = rememberScrollState()

    // Dynamic greeting based on hour of day
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 0..11 -> "Good Morning, Explorer"
            in 12..16 -> "Good Afternoon, Adventurer"
            in 17..21 -> "Good Evening, Voyager"
            else -> "Good Night, Stargazer"
        }
    }

    val headingText = remember(deviceBearing) {
        val abbreviation = viewModel.getDirectionAbbreviation(deviceBearing)
        val degreeStr = String.format(java.util.Locale.US, "%.0f", deviceBearing)
        "$abbreviation ($degreeStr°)"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(scrollState)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = "GPS Camera", style = MaterialTheme.typography.headlineLarge)
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = "GPS active",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(24.dp)
                )
            }
        }

        // Real-time location dashboard widget card (Glassmorphic Gradient Style)
        LocationDashboard(
            currentLocation = currentLocation,
            deviceBearing = deviceBearing,
            onShareClick = {
                val coordinates = "%.6f, %.6f".format(
                    currentLocation.latitude,
                    currentLocation.longitude
                )
                val mapsLink = "https://maps.google.com/?q=${currentLocation.latitude},${currentLocation.longitude}"
                val shareText = buildString {
                    append("My current location\n")
                    if (currentLocation.address.isNotBlank()) {
                        append(currentLocation.address)
                        append('\n')
                    }
                    append(coordinates)
                    append('\n')
                    append(mapsLink)
                }
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "My location")
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(
                    Intent.createChooser(shareIntent, "Share location")
                )
            }
        )

        // Live Telemetry Grid (Altitude, Speed, Compass heading)
        TelemetryGrid(
            altitude = viewModel.getFormattedAltitude(currentLocation.altitude),
            speed = viewModel.getFormattedSpeed(currentLocation.speed),
            heading = headingText
        )

        // Quick Actions Row
        QuickActions(
            onNavigateToGallery = onNavigateToGallery,
            onNavigateToTemplates = onNavigateToTemplates
        )

        // Stats Display Row
        StatsCards(
            totalCaptures = allPhotos.size,
            taggedPlaces = allPhotos.distinctBy { it.latitude.toString() + it.longitude.toString() }.size,
            onNavigateToMap = onNavigateToMap
        )

        // Recent Photos Section
        RecentPhotos(
            recentPhotos = recentPhotos,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            onPhotoClick = onPhotoClick
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun LocationDashboard(
    currentLocation: com.alas.md3gpscam.location.GpsLocation,
    deviceBearing: Float,
    onShareClick: () -> Unit
) {
    val context = LocalContext.current
    var showDetails by remember { androidx.compose.runtime.mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Current Location",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = CircleShape
                        ) {
                            Text(
                                text = "🌤 31°C",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = currentLocation.address.ifBlank { "Fetching address..." },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (showDetails) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Lat: %.6f".format(java.util.Locale.US, currentLocation.latitude),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Lon: %.6f".format(java.util.Locale.US, currentLocation.longitude),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onShareClick,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Share Location",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText(
                                    "Location Coordinates",
                                    "%.6f, %.6f".format(java.util.Locale.US, currentLocation.latitude, currentLocation.longitude)
                                )
                                clipboard.setPrimaryClip(clip)
                                android.widget.Toast.makeText(context, "Coordinates copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy Coordinates",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        TextButton(
                            onClick = { showDetails = !showDetails },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = if (showDetails) "Hide details" else "Show details",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                CompassDialWidget(
                    bearing = deviceBearing,
                    tintColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun CompassDialWidget(bearing: Float, tintColor: Color) {
    val rotation by animateFloatAsState(
        targetValue = -bearing,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 120f),
        label = "compass_rot"
    )

    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(tintColor.copy(alpha = 0.1f))
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            rotate(rotation, pivot = center) {
                val textPaint = android.graphics.Paint().apply {
                    color = tintColor.copy(alpha = 0.8f).hashCode()
                    textSize = 12.sp.toPx()
                    isAntiAlias = true
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                drawContext.canvas.nativeCanvas.drawText("N", center.x, 15.dp.toPx(), textPaint)
                drawContext.canvas.nativeCanvas.drawText("S", center.x, size.height - 5.dp.toPx(), textPaint)
                drawContext.canvas.nativeCanvas.drawText("E", size.width - 8.dp.toPx(), center.y + 4.dp.toPx(), textPaint)
                drawContext.canvas.nativeCanvas.drawText("W", 8.dp.toPx(), center.y + 4.dp.toPx(), textPaint)

                for (i in 0..360 step 45) {
                    if (i % 90 != 0) {
                        drawLine(
                            color = tintColor.copy(alpha = 0.5f),
                            start = Offset(center.x, 2.dp.toPx()),
                            end = Offset(center.x, 10.dp.toPx()),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }
                }
            }
        }

        Icon(
            imageVector = Icons.Filled.North,
            contentDescription = "Compass Needle",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun TelemetryTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun TelemetryGrid(
    altitude: String,
    speed: String,
    heading: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TelemetryTile(
            icon = Icons.Rounded.Height,
            label = "Altitude",
            value = altitude,
            modifier = Modifier.weight(1f)
        )
        TelemetryTile(
            icon = Icons.Rounded.Speed,
            label = "Speed",
            value = speed,
            modifier = Modifier.weight(1f)
        )
        TelemetryTile(
            icon = Icons.Rounded.Explore,
            label = "Heading",
            value = heading,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun QuickActions(
    onNavigateToGallery: () -> Unit,
    onNavigateToTemplates: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onNavigateToTemplates,
            modifier = Modifier
                .weight(1f)
                .height(60.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Rounded.Style, contentDescription = "Designs")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Designs", fontWeight = FontWeight.Bold)
        }

        FilledTonalButton(
            onClick = onNavigateToGallery,
            modifier = Modifier
                .weight(1f)
                .height(60.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Rounded.PhotoLibrary, contentDescription = "Gallery")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Gallery", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StatsCards(
    totalCaptures: Int,
    taggedPlaces: Int,
    onNavigateToMap: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Captures", style = MaterialTheme.typography.labelMedium)
                    Icon(
                        Icons.Rounded.Photo,
                        contentDescription = "Total Captures",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$totalCaptures",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        OutlinedCard(
            modifier = Modifier
                .weight(1.2f)
                .clickable { onNavigateToMap() },
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Geo Map", style = MaterialTheme.typography.labelMedium)
                    Icon(
                        Icons.Rounded.Map,
                        contentDescription = "Map View",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (taggedPlaces == 0) "No tags" else "$taggedPlaces places",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RecentPhotos(
    recentPhotos: List<PhotoEntity>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onPhotoClick: (PhotoEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = "Recent Captures",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (recentPhotos.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recent captures yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 20.dp)
            ) {
                items(recentPhotos, key = { photo -> photo.id }) { photo ->
                    Card(
                        modifier = Modifier
                            .size(120.dp)
                            .clickable { onPhotoClick(photo) },
                        shape = MaterialTheme.shapes.medium,
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            with(sharedTransitionScope) {
                                AsyncImage(
                                    model = photo.filePath,
                                    contentDescription = "Recent capture",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .sharedElement(
                                            rememberSharedContentState(key = "photo-${photo.id}"),
                                            animatedVisibilityScope = animatedVisibilityScope
                                        )
                                )
                            }
                            if (photo.isVideo) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = "Video",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
