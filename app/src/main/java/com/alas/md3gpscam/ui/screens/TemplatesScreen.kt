package com.alas.md3gpscam.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alas.md3gpscam.location.GpsLocation
import com.alas.md3gpscam.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(viewModel: MainViewModel) {
    val selectedTemplate by viewModel.selectedTemplate.collectAsState()
    val speedUnit by viewModel.speedUnit.collectAsState()
    val altitudeUnit by viewModel.altitudeUnit.collectAsState()
    val customNoteVal by viewModel.customNote.collectAsState()

    val scrollState = rememberScrollState()

    val infiniteTransition = rememberInfiniteTransition(label = "bearing_rotation")
    val simulatedBearing by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bearing"
    )

    val mockLocation = remember {
        GpsLocation(
            latitude = 27.1751,
            longitude = 78.0421,
            altitude = 171.0,
            speed = 5.2f,
            address = "Taj Mahal, Dharmapuri, Tajganj, Agra, Uttar Pradesh 282001, India",
            time = System.currentTimeMillis(),
            accuracy = 2.5f
        )
    }

    // Local state for interactive custom tag note
    var simulatedNote by remember { mutableStateOf("") }
    LaunchedEffect(customNoteVal) {
        if (simulatedNote.isEmpty()) {
            simulatedNote = customNoteVal.ifBlank { "Taj Mahal Survey" }
        }
    }

    // Tabs categorization state
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Telemetry", "Classic", "Creative")

    val templateList = listOf(
        TemplateItem("gps_map_camera", "Premium GPS Map", "Mini-map + flag + rich telemetry", "Telemetry", true),
        TemplateItem("gps_qr_camera", "Location QR Code", "Scan code to see on Google Maps", "Telemetry", true),
        TemplateItem("hud_compass", "HUD Compass", "Rotating compass dial overlay", "Telemetry", false),
        TemplateItem("technical", "Technical Log", "Precise telemetry format", "Telemetry", false),
        TemplateItem("basic", "Classic", "Address, time, speed & bearing", "Classic", false),
        TemplateItem("detailed", "Detailed Stats", "Large readable info with address", "Classic", false),
        TemplateItem("minimal", "Minimal", "Clean floating pill", "Creative", false),
        TemplateItem("retro_film", "Retro Film", "Vintage orange stamp", "Creative", false)
    )

    val filteredTemplates = remember(selectedTemplate, selectedCategory) {
        if (selectedCategory == "All") templateList
        else templateList.filter { it.category == selectedCategory }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Watermark Templates", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // === REALISTIC SIMULATOR ===
            Text(
                text = "Interactive Preview",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(MaterialTheme.shapes.extraLarge),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Scenic Camera Viewport Drawing using Canvas
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // Sky gradient
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
                            ),
                            size = size
                        )

                        // Sun/Light Glow in top-right
                        drawCircle(
                            color = Color(0x33FFB300),
                            radius = 120.dp.toPx(),
                            center = Offset(w * 0.85f, h * 0.15f)
                        )
                        drawCircle(
                            color = Color(0x66FFD54F),
                            radius = 60.dp.toPx(),
                            center = Offset(w * 0.85f, h * 0.15f)
                        )
                        drawCircle(
                            color = Color(0xFFFFF176),
                            radius = 35.dp.toPx(),
                            center = Offset(w * 0.85f, h * 0.15f)
                        )

                        // Background Mountain Range 1
                        val mountainPath1 = androidx.compose.ui.graphics.Path().apply {
                            moveTo(0f, h)
                            lineTo(0f, h * 0.55f)
                            lineTo(w * 0.3f, h * 0.45f)
                            lineTo(w * 0.6f, h * 0.65f)
                            lineTo(w * 0.85f, h * 0.5f)
                            lineTo(w, h * 0.6f)
                            lineTo(w, h)
                            close()
                        }
                        drawPath(mountainPath1, color = Color(0xFF1F3540))

                        // Mountain Range 2 (Foreground)
                        val mountainPath2 = androidx.compose.ui.graphics.Path().apply {
                            moveTo(0f, h)
                            lineTo(0f, h * 0.7f)
                            lineTo(w * 0.2f, h * 0.6f)
                            lineTo(w * 0.45f, h * 0.75f)
                            lineTo(w * 0.75f, h * 0.55f)
                            lineTo(w, h * 0.7f)
                            lineTo(w, h)
                            close()
                        }
                        drawPath(mountainPath2, color = Color(0xFF14242C))

                        // Rule of Thirds camera grid lines
                        val linePaintColor = Color.White.copy(alpha = 0.15f)
                        // Vertical lines
                        drawLine(color = linePaintColor, start = Offset(w / 3f, 0f), end = Offset(w / 3f, h), strokeWidth = 1.dp.toPx())
                        drawLine(color = linePaintColor, start = Offset(2f * w / 3f, 0f), end = Offset(2f * w / 3f, h), strokeWidth = 1.dp.toPx())
                        // Horizontal lines
                        drawLine(color = linePaintColor, start = Offset(0f, h / 3f), end = Offset(w, h / 3f), strokeWidth = 1.dp.toPx())
                        drawLine(color = linePaintColor, start = Offset(0f, 2f * h / 3f), end = Offset(w, 2f * h / 3f), strokeWidth = 1.dp.toPx())

                        // Center reticle
                        val reticleRadius = 16.dp.toPx()
                        drawCircle(color = linePaintColor, radius = reticleRadius, center = Offset(w / 2f, h / 2f), style = Stroke(width = 1.5.dp.toPx()))
                        drawLine(color = linePaintColor, start = Offset(w / 2f - 24f, h / 2f), end = Offset(w / 2f + 24f, h / 2f), strokeWidth = 1.5.dp.toPx())
                        drawLine(color = linePaintColor, start = Offset(w / 2f, h / 2f - 24f), end = Offset(w / 2f, h / 2f + 24f), strokeWidth = 1.5.dp.toPx())
                    }

                    // Simulated Photo Frame Container border
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(14.dp))
                    ) {
                        // The actual stamp preview overlay
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(12.dp)
                        ) {
                            LiveStampPreview(
                                location = mockLocation,
                                templateId = selectedTemplate,
                                bearing = simulatedBearing,
                                speedUnit = speedUnit,
                                altitudeUnit = altitudeUnit,
                                customNote = simulatedNote
                            )
                        }
                    }

                    // Compass degree pill indicator in top-right
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(20.dp)
                            .background(Color.Black.copy(0.5f), RoundedCornerShape(16.dp))
                            .border(0.5.dp, Color.White.copy(0.2f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Navigation,
                            contentDescription = null,
                            tint = Color(0xFFFFF176),
                            modifier = Modifier
                                .size(14.dp)
                                .rotate(-simulatedBearing)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${simulatedBearing.toInt()}°",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    // Simulated Camera HUD indicators
                    Text(
                        text = "REC",
                        color = Color.Red,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // === LIVE TAG CUSTOMIZER SECTION ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Customize Test Tag Note",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = simulatedNote,
                        onValueChange = { simulatedNote = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter a custom note description...") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Rounded.Tag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            if (simulatedNote.isNotEmpty()) {
                                IconButton(onClick = { simulatedNote = "" }) {
                                    Icon(Icons.Rounded.Clear, "Clear")
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("Survey", "Inspection", "Work Site", "Holi Trip").forEach { suggestion ->
                            val isSelected = simulatedNote == suggestion
                            FilterChip(
                                selected = isSelected,
                                onClick = { simulatedNote = suggestion },
                                label = { Text(suggestion) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // === TABS & CATEGORIES ===
            Text(
                text = "Stamp Designs",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Segmented Tab Row
            ScrollableTabRow(
                selectedTabIndex = categories.indexOf(selectedCategory),
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                divider = {},
                indicator = { tabPositions ->
                    if (categories.indexOf(selectedCategory) < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[categories.indexOf(selectedCategory)]),
                            color = MaterialTheme.colorScheme.primary,
                            height = 3.dp
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                categories.forEach { category ->
                    Tab(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        text = {
                            Text(
                                text = category,
                                fontWeight = if (selectedCategory == category) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // === 2-COLUMN STAMP CARD GRID ===
            if (filteredTemplates.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No templates in this category",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val chunkedList = filteredTemplates.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    chunkedList.forEach { rowList ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowList.forEach { item ->
                                Box(modifier = Modifier.weight(1f)) {
                                    TemplateSelectionGridCard(
                                        item = item,
                                        isSelected = selectedTemplate == item.id,
                                        onSelect = { viewModel.setTemplate(item.id) }
                                    )
                                }
                            }
                            if (rowList.size == 1) {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateSelectionGridCard(
    item: TemplateItem,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val borderStrokeWidth by animateDpAsState(
        targetValue = if (isSelected) 2.5.dp else 1.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "border_width"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        animationSpec = tween(250),
        label = "border_color"
    )
    val containerColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        animationSpec = tween(250),
        label = "container_color"
    )

    OutlinedCard(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth().height(130.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(borderStrokeWidth, borderColor),
        colors = CardDefaults.outlinedCardColors(
            containerColor = containerColor
        )
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (item.isPremium) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Text(
                                    text = "PRO",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        lineHeight = 15.sp
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

data class TemplateItem(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val isPremium: Boolean = false
)
