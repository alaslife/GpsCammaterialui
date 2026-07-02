package com.alas.md3gpscam.ui.screens

import android.annotation.SuppressLint
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
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.alas.md3gpscam.data.database.PhotoEntity
import com.alas.md3gpscam.ui.MainViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
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
    var hasInitialCentered by remember { mutableStateOf(false) }

    // Tile sources definition using public unauthenticated servers
    val googleRoadmapTileSource = remember {
        object : OnlineTileSourceBase(
            "Google-Roadmap",
            0, 20, 256, ".png",
            arrayOf("https://mt0.google.com/vt", "https://mt1.google.com/vt", "https://mt2.google.com/vt", "https://mt3.google.com/vt")
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                val zoom = MapTileIndex.getZoom(pMapTileIndex)
                val x = MapTileIndex.getX(pMapTileIndex)
                val y = MapTileIndex.getY(pMapTileIndex)
                return "${getBaseUrl()}/lyrs=m&x=$x&y=$y&z=$zoom"
            }
        }
    }

    val googleSatelliteTileSource = remember {
        object : OnlineTileSourceBase(
            "Google-Satellite",
            0, 20, 256, ".png",
            arrayOf("https://mt0.google.com/vt", "https://mt1.google.com/vt", "https://mt2.google.com/vt", "https://mt3.google.com/vt")
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                val zoom = MapTileIndex.getZoom(pMapTileIndex)
                val x = MapTileIndex.getX(pMapTileIndex)
                val y = MapTileIndex.getY(pMapTileIndex)
                return "${getBaseUrl()}/lyrs=y&x=$x&y=$y&z=$zoom"
            }
        }
    }

    val googleTerrainTileSource = remember {
        object : OnlineTileSourceBase(
            "Google-Terrain",
            0, 20, 256, ".png",
            arrayOf("https://mt0.google.com/vt", "https://mt1.google.com/vt", "https://mt2.google.com/vt", "https://mt3.google.com/vt")
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                val zoom = MapTileIndex.getZoom(pMapTileIndex)
                val x = MapTileIndex.getX(pMapTileIndex)
                val y = MapTileIndex.getY(pMapTileIndex)
                return "${getBaseUrl()}/lyrs=p&x=$x&y=$y&z=$zoom"
            }
        }
    }

    val cartoDarkTileSource = remember {
        XYTileSource(
            "CartoDB-Dark",
            0, 20, 256, ".png",
            arrayOf("https://a.basemaps.cartocdn.com/dark_all/", "https://b.basemaps.cartocdn.com/dark_all/")
        )
    }

    val cartoLightTileSource = remember {
        XYTileSource(
            "CartoDB-Light",
            0, 20, 256, ".png",
            arrayOf("https://a.basemaps.cartocdn.com/light_all/", "https://b.basemaps.cartocdn.com/light_all/")
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Native OpenStreetMap Viewer wrapping MapView
        AndroidView(
            factory = { context ->
                // Configure Osmdroid settings
                Configuration.getInstance().load(
                    context,
                    context.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE)
                )
                Configuration.getInstance().userAgentValue = context.packageName

                MapView(context).apply {
                    setBuiltInZoomControls(false)
                    setMultiTouchControls(true)
                    isTilesScaledToDpi = true

                    // Event receiver to clear marker selection when clicking elsewhere on the map
                    val mapEventsReceiver = object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            selectedPhotoMarker = null
                            return true
                        }
                        override fun longPressHelper(p: GeoPoint?): Boolean = false
                    }
                    overlays.add(MapEventsOverlay(mapEventsReceiver))

                    // Initial centering on layout dimensions ready
                    addOnLayoutChangeListener(object : android.view.View.OnLayoutChangeListener {
                        override fun onLayoutChange(
                            v: android.view.View?,
                            left: Int, top: Int, right: Int, bottom: Int,
                            oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
                        ) {
                            removeOnLayoutChangeListener(this)
                            if (photos.isNotEmpty()) {
                                val maxLat = photos.maxOf { it.latitude }
                                val minLat = photos.minOf { it.latitude }
                                val maxLon = photos.maxOf { it.longitude }
                                val minLon = photos.minOf { it.longitude }
                                val box = BoundingBox(maxLat, maxLon, minLat, minLon)
                                post {
                                    zoomToBoundingBox(box, false, 80)
                                }
                                hasInitialCentered = true
                            } else if (currentLocation.latitude != 0.0 && currentLocation.longitude != 0.0) {
                                controller.setZoom(15.0)
                                controller.setCenter(GeoPoint(currentLocation.latitude, currentLocation.longitude))
                                hasInitialCentered = true
                            }
                        }
                    })
                }
            },
            update = { mapView ->
                // 1. Update Tile Source based on MapMode setting
                val selectedSource = when (mapTypeSetting) {
                    "satellite", "hybrid" -> googleSatelliteTileSource
                    "terrain" -> googleTerrainTileSource
                    "dark" -> cartoDarkTileSource
                    "grey" -> cartoLightTileSource
                    else -> googleRoadmapTileSource
                }
                mapView.setTileSource(selectedSource)

                // 2. Remove all overlays except the click-intercept mapEventsOverlay (index 0)
                while (mapView.overlays.size > 1) {
                    mapView.overlays.removeAt(mapView.overlays.size - 1)
                }

                // 3. Render markers
                photos.forEach { photo ->
                    val marker = Marker(mapView).apply {
                        position = GeoPoint(photo.latitude, photo.longitude)
                        title = photo.address
                        infoWindow = null // Disable default info bubble popup
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { _, _ ->
                            selectedPhotoMarker = photo
                            true
                        }
                    }
                    mapView.overlays.add(marker)
                }

                // 4. Center on selected marker when user clicks a pin
                selectedPhotoMarker?.let { photo ->
                    mapView.controller.animateTo(GeoPoint(photo.latitude, photo.longitude))
                }

                // 5. Update center on location coordinates change if map hasn't loaded pins yet
                if (photos.isEmpty() && !hasInitialCentered && currentLocation.latitude != 0.0 && currentLocation.longitude != 0.0) {
                    mapView.controller.setZoom(15.0)
                    mapView.controller.setCenter(GeoPoint(currentLocation.latitude, currentLocation.longitude))
                    hasInitialCentered = true
                }

                mapView.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

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
                
                // Map Mode options: Normal (Roadmap), Satellite, Terrain, Dark, Grey
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
