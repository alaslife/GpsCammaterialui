package com.alas.md3gpscam.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.alas.md3gpscam.data.database.PhotoEntity
import com.alas.md3gpscam.ui.MainViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onPhotoSelected: (PhotoEntity) -> Unit,
    onPlayVideo: (PhotoEntity) -> Unit
) {
    val photos by viewModel.allPhotos.collectAsState()
    val selectedPhoto by viewModel.selectedPhotoForGallery.collectAsState()
    val context = LocalContext.current
    
    // Multiple selection states
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedPhotos = remember { mutableStateListOf<PhotoEntity>() }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // Group photos by date
    val groupedPhotos = photos.groupBy {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        sdf.format(Date(it.timestamp))
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedPhotos.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = {
                            selectedPhotos.clear()
                            isSelectionMode = false
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancel Selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (selectedPhotos.size == photos.size) {
                                selectedPhotos.clear()
                            } else {
                                selectedPhotos.clear()
                                selectedPhotos.addAll(photos)
                            }
                        }) {
                            Icon(Icons.Rounded.SelectAll, contentDescription = "Select/Deselect All")
                        }
                        IconButton(
                            enabled = selectedPhotos.isNotEmpty(),
                            onClick = {
                                shareMultiplePhotos(context, selectedPhotos)
                            }
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = "Share Selected")
                        }
                        IconButton(
                            enabled = selectedPhotos.isNotEmpty(),
                            onClick = {
                                showDeleteConfirmation = true
                            }
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Gallery") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        if (photos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No photos or videos yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                groupedPhotos.forEach { (date, photoList) ->
                    item(key = date, contentType = "HEADER") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Text(
                                text = date,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    items(photoList, key = { it.id }) { photo ->
                        val isSelected = selectedPhotos.contains(photo)
                        GalleryItem(
                            photo = photo,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onItemClick = { clickedPhoto ->
                                if (isSelectionMode) {
                                    if (isSelected) {
                                        selectedPhotos.remove(clickedPhoto)
                                        if (selectedPhotos.isEmpty()) {
                                            isSelectionMode = false
                                        }
                                    } else {
                                        selectedPhotos.add(clickedPhoto)
                                    }
                                } else {
                                    viewModel.setSelectedPhotoForGallery(clickedPhoto)
                                }
                            },
                            onItemLongClick = { clickedPhoto ->
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    selectedPhotos.add(clickedPhoto)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Single photo detail overlay sheet
    if (selectedPhoto != null && !isSelectionMode) {
        val p = selectedPhoto!!
        PhotoDetailOverlay(
            photo = p,
            onDismiss = { viewModel.setSelectedPhotoForGallery(null) },
            onDelete = {
                viewModel.deletePhoto(p)
                viewModel.setSelectedPhotoForGallery(null)
            },
            onPlayClick = {
                viewModel.setSelectedPhotoForGallery(null)
                onPlayVideo(p)
            }
        )
    }

    // Delete confirmation alert dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete selected items?") },
            text = { Text("Are you sure you want to delete the ${selectedPhotos.size} selected photos and videos? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedPhotos.forEach { photo ->
                            viewModel.deletePhoto(photo)
                        }
                        selectedPhotos.clear()
                        isSelectionMode = false
                        showDeleteConfirmation = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryItem(
    photo: PhotoEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onItemClick: (PhotoEntity) -> Unit,
    onItemLongClick: (PhotoEntity) -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(
                onClick = { onItemClick(photo) },
                onLongClick = { onItemLongClick(photo) }
            ),
        shape = MaterialTheme.shapes.medium
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = photo.filePath,
                contentDescription = "Photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Video indicator overlay
            if (photo.isVideo) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Selection Mode overlay tint & badge
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            else Color.Black.copy(alpha = 0.15f)
                        )
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.6f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected check",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailOverlay(
    photo: PhotoEntity,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onPlayClick: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val dateText = SimpleDateFormat("EEEE, dd MMMM yyyy HH:mm", Locale.getDefault()).format(Date(photo.timestamp))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Photo/Video Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = photo.filePath,
                    contentDescription = "Full photo preview",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.large),
                    contentScale = ContentScale.Fit
                )
                if (photo.isVideo) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable { onPlayClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = "Play Video",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Metadata section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = photo.address,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, "More Options")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Share") },
                                onClick = {
                                    shareSinglePhoto(context, photo)
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Rounded.Share, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    onDelete()
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null) }
                            )
                        }
                    }
                }

                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                val sUnit = if (photo.speed > 0) "km/h" else ""
                val aUnit = if (photo.altitude > 0) "m" else ""
                
                val telemetry = listOf(
                    "Lat: %.5f".format(photo.latitude),
                    "Long: %.5f".format(photo.longitude),
                    "Alt: %.1f%s".format(photo.altitude, aUnit),
                    "Speed: %.1f%s".format(photo.speed * 3.6f, sUnit),
                    "Bearing: %.0f°".format(photo.bearing)
                )

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp) // Adjust as needed
                ) {
                    items(telemetry) { item ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Text(
                                item,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// Helpers for sharing single and multiple media items
fun getShareUri(context: Context, filePath: String): android.net.Uri? {
    return if (filePath.startsWith("content://")) {
        android.net.Uri.parse(filePath)
    } else {
        val file = if (filePath.startsWith("file://")) {
            File(android.net.Uri.parse(filePath).path ?: "")
        } else {
            File(filePath)
        }
        if (file.exists()) {
            FileProvider.getUriForFile(context, context.packageName + ".provider", file)
        } else {
            null
        }
    }
}

fun shareSinglePhoto(context: Context, photo: PhotoEntity) {
    val uri = getShareUri(context, photo.filePath) ?: return
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_STREAM, uri)
        type = if (photo.isVideo) "video/*" else "image/*"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share via"))
}

fun shareMultiplePhotos(context: Context, selectedList: List<PhotoEntity>) {
    if (selectedList.isEmpty()) return
    
    val uris = ArrayList<android.net.Uri>()
    selectedList.forEach { photo ->
        val uri = getShareUri(context, photo.filePath)
        if (uri != null) {
            uris.add(uri)
        }
    }
    
    if (uris.isEmpty()) return
    
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND_MULTIPLE
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        type = "*/*" // Mix images and videos
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share ${uris.size} items via"))
}
