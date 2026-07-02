package com.alas.md3gpscam.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alas.md3gpscam.data.database.PhotoEntity
import com.alas.md3gpscam.ui.MainViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.google.maps.android.compose.clustering.Clustering
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MainViewModel,
    onPhotoSelected: (PhotoEntity) -> Unit
) {
    val photos by viewModel.allPhotos.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val mapTypeSetting by viewModel.mapType.collectAsState()

    var selectedPhotoMarker by remember { mutableStateOf<PhotoEntity?>(null) }

    val properties = remember(mapTypeSetting) {
        MapProperties(
            mapType = when (mapTypeSetting) {
                "satellite" -> MapType.SATELLITE
                "hybrid" -> MapType.HYBRID
                "terrain" -> MapType.TERRAIN
                else -> MapType.NORMAL
            },
            isMyLocationEnabled = true
        )
    }

    val uiSettings by remember {
        mutableStateOf(
            MapUiSettings(
                myLocationButtonEnabled = true,
                zoomControlsEnabled = false
            )
        )
    }

    // Camera State
    val initialTarget = remember(photos) {
        if (photos.isNotEmpty()) {
            LatLng(photos.first().latitude, photos.first().longitude)
        } else {
            LatLng(currentLocation.latitude, currentLocation.longitude)
        }
    }
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialTarget, 10f)
    }

    var hasCenteredOnCurrentLocation by remember { mutableStateOf(false) }

    LaunchedEffect(currentLocation) {
        if (currentLocation.latitude != 0.0 && currentLocation.longitude != 0.0 && !hasCenteredOnCurrentLocation && photos.isEmpty()) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(currentLocation.latitude, currentLocation.longitude),
                    15f
                )
            )
            hasCenteredOnCurrentLocation = true
        }
    }

    // Keep camera updated if list changes first time
    LaunchedEffect(photos) {
        if (photos.isNotEmpty() && cameraPositionState.position.target.latitude == 0.0) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(photos.first().latitude, photos.first().longitude),
                12f
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Maps integration
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = properties,
            uiSettings = uiSettings,
            onMapClick = {
                selectedPhotoMarker = null
            }
        ) {
            Clustering(
                items = photos.map { PhotoClusterItem(it) },
                onClusterItemClick = { item ->
                    selectedPhotoMarker = item.photo
                    false
                }
            )
        }

        // Top Overlay Map Type Toggles
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Map, contentDescription = "Map Style")
                Text("Map Mode: ", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                
                listOf("normal", "satellite", "hybrid", "terrain").forEach { type ->
                    val isSelected = mapTypeSetting == type
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setMapType(type) },
                        label = { Text(type.replaceFirstChar { it.uppercase() }) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }

        // Bottom Popup Thumbnail Card for selected pin
        AnimatedVisibility(
            visible = selectedPhotoMarker != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 90.dp, start = 16.dp, end = 16.dp)
        ) {
            selectedPhotoMarker?.let { photo ->
                val dateText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(photo.timestamp))
                
                Card(
                    shape = MaterialTheme.shapes.large,
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clickable { onPhotoSelected(photo) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = photo.filePath,
                            contentDescription = "Photo thumbnail",
                            modifier = Modifier
                                .size(86.dp)
                                .clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.Crop
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = photo.address,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Lat: %.4f, Long: %.4f".format(photo.latitude, photo.longitude),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = dateText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
