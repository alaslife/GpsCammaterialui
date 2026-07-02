package com.alas.md3gpscam.ui.screens

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
    var isPageLoaded by remember { mutableStateOf(false) }

    // Serialize photos to JSON string for Leaflet JavaScript interface
    val jsonPhotos = remember(photos) {
        photos.map { photo ->
            """{"id":${photo.id},"latitude":${photo.latitude},"longitude":${photo.longitude}}"""
        }.joinToString(prefix = "[", postfix = "]")
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Keyless Web Maps integration (Leaflet + OpenStreetMap/Google Maps tiles)
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    @SuppressLint("SetJavaScriptEnabled")
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    
                    // Set custom User Agent to ensure tile servers do not reject requests
                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) GpsCamApp"
                    
                    // Allow loading HTTPS CDNs/tiles from local content
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                    
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isPageLoaded = true
                            // Initial setup once page loads
                            view?.evaluateJavascript("setMapType('$mapTypeSetting')", null)
                            view?.evaluateJavascript("updateMarkers('$jsonPhotos')", null)
                            
                            // Center on current location if there are no photos yet
                            if (photos.isEmpty() && currentLocation.latitude != 0.0 && currentLocation.longitude != 0.0) {
                                view?.evaluateJavascript(
                                    "centerOn(${currentLocation.latitude}, ${currentLocation.longitude}, 15)",
                                    null
                                )
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            android.util.Log.e("MapScreen", "WebView Error: ${error?.description} for ${request?.url}")
                        }
                    }

                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onMarkerClick(photoId: Long) {
                            post {
                                selectedPhotoMarker = photos.find { it.id == photoId }
                            }
                        }

                        @JavascriptInterface
                        fun onMapClick() {
                            post {
                                selectedPhotoMarker = null
                            }
                        }
                    }, "AndroidInterface")

                    // Read html from assets and load with secure base URL to bypass Same-Origin Policy (SOP) blocks
                    try {
                        val htmlContent = context.assets.open("map.html").bufferedReader().use { it.readText() }
                        loadDataWithBaseURL(
                            "https://appassets.androidplatform.net",
                            htmlContent,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("MapScreen", "Failed to load map.html from assets", e)
                        loadUrl("file:///android_asset/map.html")
                    }
                }
            },
            update = { view ->
                if (isPageLoaded) {
                    view.evaluateJavascript("setMapType('$mapTypeSetting')", null)
                    view.evaluateJavascript("updateMarkers('$jsonPhotos')", null)
                    
                    selectedPhotoMarker?.let { photo ->
                        view.evaluateJavascript("selectMarker(${photo.id})", null)
                    }
                }
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
                
                // Normal, Satellite, Hybrid, Terrain options (all keyless web tiles mapped in JS)
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
