package com.alas.md3gpscam.image

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.alas.md3gpscam.location.GpsLocation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.pm.PackageManager
import java.net.URL
import java.net.HttpURLConnection

object ImageProcessor {

    fun stampImage(
        context: Context,
        inputUri: Uri,
        location: GpsLocation,
        templateId: String,
        bearing: Float = 0f,
        speedUnit: String = "kmh",
        altitudeUnit: String = "meters",
        customNote: String? = null,
        imageRatio: String = "4:3",
        photoQuality: String = "medium",
        stampMapType: String = "standard"
    ): Uri? {
        val resolver = context.contentResolver
        var bitmap: Bitmap? = null
        try {
            val options = BitmapFactory.Options()
            if (photoQuality != "high") {
                // Read original dimensions first to calculate downsampling size
                options.inJustDecodeBounds = true
                resolver.openInputStream(inputUri).use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, options)
                }

                val reqSize = if (photoQuality == "low") 1080 else 2048
                options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, reqSize)
                options.inJustDecodeBounds = false
            }

            resolver.openInputStream(inputUri).use { inputStream ->
                bitmap = BitmapFactory.decodeStream(inputStream, null, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

        val original = bitmap ?: return null
        
        // Crop the bitmap to the selected ratio if needed
        val croppedBitmap = if (imageRatio == "1:1") {
            val size = original.width.coerceAtMost(original.height)
            val x = (original.width - size) / 2
            val y = (original.height - size) / 2
            val cropped = Bitmap.createBitmap(original, x, y, size, size)
            if (cropped != original) {
                original.recycle()
            }
            cropped
        } else if (imageRatio == "16:9") {
            val currentRatio = original.width.toFloat() / original.height.toFloat()
            val targetRatio = 16f / 9f
            val targetRatioAlt = 9f / 16f
            
            val isPortrait = original.height > original.width
            val actualTargetRatio = if (isPortrait) targetRatioAlt else targetRatio
            
            if (Math.abs(currentRatio - actualTargetRatio) > 0.05f) {
                if (isPortrait) {
                    val targetHeight = (original.width * 16 / 9).coerceAtMost(original.height)
                    val targetWidth = original.width
                    val y = (original.height - targetHeight) / 2
                    val cropped = Bitmap.createBitmap(original, 0, y, targetWidth, targetHeight)
                    if (cropped != original) {
                        original.recycle()
                    }
                    cropped
                } else {
                    val targetWidth = (original.height * 16 / 9).coerceAtMost(original.width)
                    val targetHeight = original.height
                    val x = (original.width - targetWidth) / 2
                    val cropped = Bitmap.createBitmap(original, x, 0, targetWidth, targetHeight)
                    if (cropped != original) {
                        original.recycle()
                    }
                    cropped
                }
            } else {
                original
            }
        } else {
            original
        }

        val workingBitmap = croppedBitmap.copy(Bitmap.Config.ARGB_8888, true)
        if (workingBitmap != croppedBitmap) {
            croppedBitmap.recycle()
        }

        val apiKey = try {
            val appInfo = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            appInfo.metaData.getString("com.google.android.geo.API_KEY") ?: ""
        } catch (e: Exception) {
            ""
        }

        val canvas = Canvas(workingBitmap)
        drawStamp(canvas, workingBitmap.width, workingBitmap.height, location, templateId, bearing, speedUnit, altitudeUnit, customNote, stampMapType, apiKey)

        val timestamp = System.currentTimeMillis()
        val filename = "GPSCAM_${timestamp}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/GpsCamera")
            }
        }

        val outputUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (outputUri != null) {
            try {
                resolver.openOutputStream(outputUri).use { outputStream ->
                    if (outputStream != null) {
                        val compressQuality = when (photoQuality) {
                            "high" -> 95
                            "medium" -> 85
                            "low" -> 70
                            else -> 85
                        }
                        workingBitmap.compress(Bitmap.CompressFormat.JPEG, compressQuality, outputStream)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
        workingBitmap.recycle()
        return outputUri
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqSize: Int): Int {
        var inSampleSize = 1
        if (width > reqSize || height > reqSize) {
            val halfWidth = width / 2
            val halfHeight = height / 2
            while (halfWidth / inSampleSize >= reqSize && halfHeight / inSampleSize >= reqSize) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun drawStamp(
        canvas: Canvas,
        width: Int,
        height: Int,
        location: GpsLocation,
        templateId: String,
        bearing: Float,
        speedUnit: String,
        altitudeUnit: String,
        customNote: String? = null,
        stampMapType: String = "standard",
        apiKey: String = ""
    ) {
        val dateText = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(location.time))
        val captureDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(location.time))
        val captureTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(location.time))
        val premiumDateText = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(Date(location.time))
        val premiumTimeText = SimpleDateFormat("hh:mm:ss a 'GMT'XXX", Locale.getDefault()).format(Date(location.time))
        val coordsText = "Lat: %.6f°  Long: %.6f°".format(location.latitude, location.longitude)
        
        // Convert units
        val speedValue = if (speedUnit == "mph") location.speed * 2.23694f else location.speed * 3.6f
        val speedLabel = if (speedUnit == "mph") "mph" else "km/h"
        val speedText = "Speed: %.1f %s".format(speedValue, speedLabel)

        val altValue = if (altitudeUnit == "feet") location.altitude * 3.28084 else location.altitude
        val altLabel = if (altitudeUnit == "feet") "ft" else "m"
        val altText = "Alt: %.1f %s".format(altValue, altLabel)

        val directionAbbrev = getDirectionAbbreviation(bearing)
        val bearingText = "Heading: %s (%.0f°)".format(directionAbbrev, bearing)
        
        val addressText = location.address
        val scale = (width.coerceAtMost(height) / 1080f).coerceAtLeast(0.6f) * 1.48f

        val note = if (customNote.isNullOrBlank()) null else customNote.trim()
        val notePaint = Paint().apply {
            color = Color.rgb(255, 202, 40) // Amber accent color
            textSize = 23f * scale
            isAntiAlias = true
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }

        // Paint definitions
        val cardPaint = Paint().apply {
            color = Color.argb(170, 15, 17, 26) // Darker, rich M3 neutral-variant tone
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val borderPaint = Paint().apply {
            color = Color.argb(60, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2f * scale
            isAntiAlias = true
        }

        val textPaintPrimary = Paint().apply {
            color = Color.WHITE
            textSize = 28f * scale
            isAntiAlias = true
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }

        val textPaintSecondary = Paint().apply {
            color = Color.argb(230, 235, 235, 245)
            textSize = 22f * scale
            isAntiAlias = true
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        val highlightPaint = Paint().apply {
            color = Color.argb(255, 186, 195, 255) // Dynamic color highlight hue (M3 Dark Primary)
            textSize = 24f * scale
            isAntiAlias = true
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }

        when (templateId) {
            "minimal" -> {
                val pillText = if (note != null) {
                    "📌 $note  •  📍 %.4f, %.4f  •  %s  •  %s".format(note, location.latitude, location.longitude, directionAbbrev, dateText)
                } else {
                    "📍 %.4f, %.4f  •  %s  •  %s".format(location.latitude, location.longitude, directionAbbrev, dateText)
                }
                val textWidth = textPaintSecondary.measureText(pillText)
                val padding = 24f * scale
                val pillWidth = textWidth + padding * 2
                val pillHeight = 52f * scale
                val left = (width - pillWidth) / 2f
                val bottom = height - 60f * scale
                val top = bottom - pillHeight
                val rect = RectF(left, top, left + pillWidth, bottom)
                val radius = pillHeight / 2f

                canvas.drawRoundRect(rect, radius, radius, cardPaint)
                canvas.drawRoundRect(rect, radius, radius, borderPaint)
                canvas.drawText(pillText, left + padding, bottom - (pillHeight - textPaintSecondary.textSize) / 2f - 4f * scale, textPaintSecondary)
            }
            "technical" -> {
                val padding = 30f * scale
                val cardWidth = 540f * scale
                val extraHeight = if (note != null) 36f * scale else 0f
                val cardHeight = (250f * scale) + extraHeight
                val left = 40f * scale
                val bottom = height - 40f * scale
                val top = bottom - cardHeight
                val rect = RectF(left, top, left + cardWidth, bottom)
                val radius = 24f * scale

                canvas.drawRoundRect(rect, radius, radius, cardPaint)
                canvas.drawRoundRect(rect, radius, radius, borderPaint)

                var yOffset = top + padding + 20f * scale
                canvas.drawText("📡 GPS TELEMETRY LOG", left + padding, yOffset, highlightPaint)
                yOffset += 42f * scale
                canvas.drawText("LATITUDE:  %.7f".format(location.latitude), left + padding, yOffset, textPaintSecondary)
                yOffset += 32f * scale
                canvas.drawText("LONGITUDE: %.7f".format(location.longitude), left + padding, yOffset, textPaintSecondary)
                yOffset += 32f * scale
                canvas.drawText("ALTITUDE:  %.1f %s".format(altValue, altLabel), left + padding, yOffset, textPaintSecondary)
                yOffset += 32f * scale
                canvas.drawText("SPEED:     %.1f %s".format(speedValue, speedLabel), left + padding, yOffset, textPaintSecondary)
                yOffset += 32f * scale
                canvas.drawText("HEADING:   %s (%.0f°)".format(directionAbbrev, bearing), left + padding, yOffset, textPaintSecondary)
                if (note != null) {
                    yOffset += 32f * scale
                    canvas.drawText("NOTE:      $note".uppercase(Locale.ROOT), left + padding, yOffset, notePaint)
                }
            }
            "detailed" -> {
                val padding = 30f * scale
                val isLandscape = width > height
                val maxCardWidth = if (isLandscape) 480f * scale else 820f * scale
                val cardWidth = (width - 80f * scale).coerceAtMost(maxCardWidth)
                val left = 40f * scale
                val addressLimit = (cardWidth - padding * 2).toInt()
                val wrappedAddress = wrapText(addressText, textPaintPrimary, addressLimit)
                
                // Calculate dynamic card height
                val noteHeight = if (note != null) 36f * scale else 0f
                val cardHeight = padding * 2 + (wrappedAddress.size * 38f * scale) + 15f * scale + (3 * 32f * scale) + noteHeight
                val bottom = height - 40f * scale
                val top = bottom - cardHeight
                val rect = RectF(left, top, left + cardWidth, bottom)
                val radius = 28f * scale

                canvas.drawRoundRect(rect, radius, radius, cardPaint)
                canvas.drawRoundRect(rect, radius, radius, borderPaint)

                var yOffset = top + padding + 22f * scale
                
                for (line in wrappedAddress) {
                    canvas.drawText(line, left + padding, yOffset, textPaintPrimary)
                    yOffset += 38f * scale
                }
                
                if (note != null) {
                    yOffset += 10f * scale
                    canvas.drawText("📌 $note", left + padding, yOffset, notePaint)
                    yOffset += 28f * scale
                } else {
                    yOffset += 10f * scale
                }
                
                canvas.drawText(coordsText, left + padding, yOffset, textPaintSecondary)
                yOffset += 32f * scale
                canvas.drawText("%s  •  %s  •  %s".format(altText, speedText, bearingText), left + padding, yOffset, textPaintSecondary)
                yOffset += 32f * scale
                canvas.drawText(dateText, left + padding, yOffset, highlightPaint)
            }
            "hud_compass" -> {
                // Compass HUD style (Futuristic overlay drawing a mini compass dial)
                val padding = 30f * scale
                val compassRadius = 75f * scale
                val isLandscape = width > height
                val maxCardWidth = if (isLandscape) 560f * scale else 860f * scale
                val cardWidth = (width - 80f * scale).coerceAtMost(maxCardWidth)
                val left = 40f * scale
                val textLimitWidth = (cardWidth - compassRadius * 2 - padding * 3).toInt()
                val wrappedAddress = wrapText(addressText, textPaintPrimary, textLimitWidth)
                
                // Calculate dynamic card height
                val addressHeight = wrappedAddress.size * 38f * scale
                val noteHeight = if (note != null) 36f * scale else 0f
                val contentHeight = padding * 2 + addressHeight + (3 * 32f * scale) + 15f * scale + noteHeight
                val cardHeight = contentHeight.coerceAtLeast(220f * scale)
                
                val bottom = height - 40f * scale
                val top = bottom - cardHeight
                val rect = RectF(left, top, left + cardWidth, bottom)
                val radius = 28f * scale

                canvas.drawRoundRect(rect, radius, radius, cardPaint)
                canvas.drawRoundRect(rect, radius, radius, borderPaint)

                // Draw mini compass dial on the right side centered vertically
                val compassCx = left + cardWidth - compassRadius - padding
                val compassCy = top + cardHeight / 2f
                drawCompassDial(canvas, compassCx, compassCy, compassRadius, bearing, scale)

                // Draw Text info on the left side
                var yOffset = top + padding + 22f * scale
                for (line in wrappedAddress) {
                    canvas.drawText(line, left + padding, yOffset, textPaintPrimary)
                    yOffset += 38f * scale
                }
                
                if (note != null) {
                    yOffset += 10f * scale
                    canvas.drawText("Tag: $note", left + padding, yOffset, notePaint)
                    yOffset += 28f * scale
                } else {
                    yOffset += 10f * scale
                }
                
                canvas.drawText(coordsText, left + padding, yOffset, textPaintSecondary)
                yOffset += 32f * scale
                canvas.drawText("%s  •  %s".format(altText, speedText), left + padding, yOffset, textPaintSecondary)
                yOffset += 32f * scale
                canvas.drawText("DIR: %s  •  %s".format(bearingText, dateText), left + padding, yOffset, highlightPaint)
            }

            "retro_film" -> {
                // Classic camera orange timestamp with dropshadow direct stamp (no card background)
                val shadowPaint = Paint().apply {
                    color = Color.argb(180, 0, 0, 0)
                    textSize = 28f * scale
                    isAntiAlias = true
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                }

                val orangePaint = Paint().apply {
                    color = Color.rgb(255, 120, 0) // Camera Date Orange
                    textSize = 28f * scale
                    isAntiAlias = true
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                }

                val offset = 2f * scale
                val xPos = width - 560f * scale
                var yPos = height - (if (note != null) 156f else 120f) * scale

                if (note != null) {
                    val noteLine = "TAG: $note".uppercase(Locale.ROOT)
                    canvas.drawText(noteLine, xPos + offset, yPos + offset, shadowPaint)
                    canvas.drawText(noteLine, xPos, yPos, orangePaint)
                    yPos += 36f * scale
                }

                val latDir = if (location.latitude >= 0) "N" else "S"
                val lonDir = if (location.longitude >= 0) "E" else "W"
                // Line 1: Coords & Bearing
                val line1 = "📍 %.4f %s  %.4f %s  BEAR: %.0f°".format(
                    kotlin.math.abs(location.latitude), latDir,
                    kotlin.math.abs(location.longitude), lonDir,
                    bearing
                )
                canvas.drawText(line1, xPos + offset, yPos + offset, shadowPaint)
                canvas.drawText(line1, xPos, yPos, orangePaint)
                
                // Line 2: Alt, Speed & Time
                yPos += 36f * scale
                val line2 = "%s  %s  %s".format(altText.uppercase(), speedText.uppercase(), dateText)
                canvas.drawText(line2, xPos + offset, yPos + offset, shadowPaint)
                canvas.drawText(line2, xPos, yPos, orangePaint)
            }

            "gps_map_camera", "gps_qr_camera" -> {
                val padding = 14f * scale
                val isLandscape = width > height
                val maxCardWidth = if (isLandscape) 580f * scale else width - 80f * scale
                val cardWidth = (width - 80f * scale).coerceAtMost(maxCardWidth)
                val left = 40f * scale

                val premiumCardPaint = Paint(cardPaint).apply {
                    color = Color.argb(205, 10, 12, 18)
                }
                val accentPaint = Paint().apply {
                    color = Color.rgb(255, 202, 40)
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }

                // First calculate text layout dimensions to compute required card height
                val mapSize = 140f * scale
                val textLeft = left + padding + 8f * scale + mapSize + 18f * scale
                val textRight = left + cardWidth - padding
                val maxTextWidth = (textRight - textLeft).toInt()

                // Address (Bold title)
                val addressPaint = Paint(textPaintPrimary).apply {
                    textSize = 19f * scale
                }
                val wrappedAddress = wrapText(location.address, addressPaint, maxTextWidth)
                val addressLinesCount = wrappedAddress.size.coerceAtLeast(1)
                
                val noteLineHeight = if (note != null) 24f * scale else 0f
                // Required height calculation
                val requiredTextHeight = padding * 2 + (22f + addressLinesCount * 23f + 3f + 21f + 28f + 20f) * scale + noteLineHeight
                val cardHeight = requiredTextHeight.coerceAtLeast(168f * scale)

                val bottom = height - 40f * scale
                val top = bottom - cardHeight
                val rect = RectF(left, top, left + cardWidth, bottom)
                val radius = 22f * scale

                // Draw main card background
                canvas.drawRoundRect(rect, radius, radius, cardPaint)
                canvas.drawRoundRect(rect, radius, radius, premiumCardPaint)
                canvas.drawRoundRect(rect, radius, radius, borderPaint)
                canvas.drawRoundRect(
                    RectF(left, top, left + 8f * scale, bottom),
                    radius,
                    radius,
                    accentPaint
                )

                // Draw mini-map container on the left, centered vertically
                val mapLeft = left + padding + 8f * scale
                val mapTop = top + (cardHeight - mapSize) / 2f
                val mapRect = RectF(mapLeft, mapTop, mapLeft + mapSize, mapTop + mapSize)
                val mapRadius = 14f * scale

                if (templateId == "gps_qr_camera") {
                    val whiteBgPaint = Paint().apply {
                        color = Color.WHITE
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                    canvas.drawRoundRect(mapRect, mapRadius, mapRadius, whiteBgPaint)
                    val qrUrl = "https://maps.google.com/?q=${location.latitude},${location.longitude}"
                    val qrBitmap = generateQrCode(qrUrl, mapSize.toInt(), mapSize.toInt())
                    if (qrBitmap != null) {
                        canvas.drawBitmap(qrBitmap, null, mapRect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                        qrBitmap.recycle()
                    }
                } else {
                    val staticMap = fetchStaticMap(location.latitude, location.longitude, stampMapType, apiKey)
                    if (staticMap != null) {
                        canvas.save()
                        val clipPath = Path().apply {
                            addRoundRect(mapRect, mapRadius, mapRadius, Path.Direction.CW)
                        }
                        canvas.clipPath(clipPath)
                        canvas.drawBitmap(staticMap, null, mapRect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                        canvas.restore()
                        staticMap.recycle()
                    } else {
                        // Fallback to vector map illustration
                        // Draw map background based on theme
                        val mapBgPaint = Paint().apply {
                            color = when (stampMapType) {
                                "satellite" -> Color.rgb(27, 94, 32)
                                "dark" -> Color.rgb(18, 18, 24)
                                "grey" -> Color.rgb(245, 245, 245)
                                else -> Color.rgb(232, 245, 233)
                            }
                            style = Paint.Style.FILL
                            isAntiAlias = true
                        }
                        canvas.drawRoundRect(mapRect, mapRadius, mapRadius, mapBgPaint)

                        // Draw topographic lines or grids for advanced styles
                        if (stampMapType == "satellite") {
                            val contourPaint = Paint().apply {
                                color = Color.argb(40, 255, 255, 255)
                                style = Paint.Style.STROKE
                                strokeWidth = 1f * scale
                                isAntiAlias = true
                            }
                            val cPath = Path().apply {
                                moveTo(mapLeft + mapSize * 0.1f, mapTop)
                                quadTo(mapLeft + mapSize * 0.3f, mapTop + mapSize * 0.3f, mapLeft + mapSize * 0.1f, mapTop + mapSize * 0.6f)
                                moveTo(mapLeft + mapSize * 0.5f, mapTop)
                                quadTo(mapLeft + mapSize * 0.7f, mapTop + mapSize * 0.4f, mapLeft + mapSize * 0.5f, mapTop + mapSize * 0.9f)
                            }
                            canvas.drawPath(cPath, contourPaint)

                            val gridPaint = Paint().apply {
                                color = Color.argb(30, 255, 255, 255)
                                style = Paint.Style.STROKE
                                strokeWidth = 0.5f * scale
                                isAntiAlias = true
                            }
                            for (i in 1..3) {
                                val offset = (mapSize / 4f) * i
                                canvas.drawLine(mapLeft + offset, mapTop, mapLeft + offset, mapTop + mapSize, gridPaint)
                                canvas.drawLine(mapLeft, mapTop + offset, mapLeft + mapSize, mapTop + offset, gridPaint)
                            }
                        } else if (stampMapType == "dark") {
                            val gridPaint = Paint().apply {
                                color = Color.argb(20, 0, 229, 255)
                                style = Paint.Style.STROKE
                                strokeWidth = 0.5f * scale
                                isAntiAlias = true
                            }
                            for (i in 1..3) {
                                val offset = (mapSize / 4f) * i
                                canvas.drawLine(mapLeft + offset, mapTop, mapLeft + offset, mapTop + mapSize, gridPaint)
                                canvas.drawLine(mapLeft, mapTop + offset, mapLeft + mapSize, mapTop + offset, gridPaint)
                            }
                        }

                        // Draw map water body (curved teal/blue path)
                        val waterPaint = Paint().apply {
                            color = when (stampMapType) {
                                "satellite" -> Color.rgb(13, 71, 161)
                                "dark" -> Color.rgb(0, 77, 64)
                                "grey" -> Color.rgb(224, 224, 224)
                                else -> Color.rgb(224, 242, 241)
                            }
                            style = Paint.Style.FILL
                            isAntiAlias = true
                        }
                        val waterPath = Path().apply {
                            moveTo(mapLeft, mapTop + mapSize)
                            lineTo(mapLeft + mapSize * 0.5f, mapTop + mapSize)
                            quadTo(mapLeft + mapSize * 0.25f, mapTop + mapSize * 0.7f, mapLeft, mapTop + mapSize * 0.5f)
                            close()
                        }
                        canvas.drawPath(waterPath, waterPaint)

                        // Draw map major roads
                        val roadPaint = Paint().apply {
                            color = when (stampMapType) {
                                "satellite" -> Color.rgb(255, 179, 0)
                                "dark" -> Color.rgb(0, 229, 255)
                                "grey" -> Color.rgb(117, 117, 117)
                                else -> Color.WHITE
                            }
                            style = Paint.Style.STROKE
                            strokeWidth = 3.5f * scale
                            isAntiAlias = true
                            strokeCap = Paint.Cap.ROUND
                        }
                        canvas.drawLine(mapLeft, mapTop + mapSize * 0.3f, mapLeft + mapSize, mapTop + mapSize * 0.7f, roadPaint)
                        canvas.drawLine(mapLeft + mapSize * 0.4f, mapTop, mapLeft + mapSize * 0.6f, mapTop + mapSize, roadPaint)
                        
                        // Draw map minor roads
                        val thinRoadPaint = Paint().apply {
                            color = when (stampMapType) {
                                "satellite" -> Color.rgb(166, 107, 0)
                                "dark" -> Color.rgb(1, 87, 155)
                                "grey" -> Color.rgb(189, 189, 189)
                                else -> Color.rgb(207, 216, 220)
                            }
                            style = Paint.Style.STROKE
                            strokeWidth = 1.6f * scale
                            isAntiAlias = true
                            strokeCap = Paint.Cap.ROUND
                        }
                        canvas.drawLine(mapLeft, mapTop + mapSize * 0.6f, mapLeft + mapSize, mapTop + mapSize * 0.2f, thinRoadPaint)
                    }

                    // Draw pulsing location dot in center (applies to both real and fallback vector maps)
                    val cx = mapLeft + mapSize / 2f
                    val cy = mapTop + mapSize / 2f
                    val dotPulseColor = when (stampMapType) {
                        "satellite" -> Color.argb(60, 255, 235, 59)
                        "dark" -> Color.argb(51, 233, 30, 99)
                        "grey" -> Color.argb(51, 97, 97, 97)
                        else -> Color.argb(51, 33, 150, 243)
                    }
                    val dotPulsePaint = Paint().apply {
                        color = dotPulseColor
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                    val dotInnerPaint = Paint().apply {
                        color = Color.WHITE
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                    val dotCoreColor = when (stampMapType) {
                        "satellite" -> Color.rgb(255, 235, 59)
                        "dark" -> Color.rgb(233, 30, 99)
                        "grey" -> Color.rgb(97, 97, 97)
                        else -> Color.rgb(33, 150, 243)
                    }
                    val dotCorePaint = Paint().apply {
                        color = dotCoreColor
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                    canvas.drawCircle(cx, cy, 16f * scale, dotPulsePaint)
                    canvas.drawCircle(cx, cy, 6.5f * scale, dotInnerPaint)
                    canvas.drawCircle(cx, cy, 4f * scale, dotCorePaint)

                    // Draw tiny Flag Badge in top-left of the mini-map
                    val country = getCountryFromAddress(location.address)
                    drawFlagBadgeOnCanvas(canvas, country, mapLeft + 7f * scale, mapTop + 7f * scale, 30f * scale, 20f * scale, scale)
                }

                // Draw text info on the right
                var yOffset = top + padding + 22f * scale
                
                for (line in wrappedAddress.take(2).ifEmpty { listOf("Unknown location") }) {
                    canvas.drawText(line, textLeft, yOffset, addressPaint)
                    yOffset += 23f * scale
                }
                yOffset += 3f * scale

                // Render custom tag/note if present
                if (note != null) {
                    canvas.drawText(note, textLeft, yOffset, notePaint)
                    yOffset += 21f * scale
                }

                // Coordinates
                val smallTextPaint = Paint(textPaintSecondary).apply {
                    color = Color.argb(235, 245, 246, 255)
                    textSize = 14f * scale
                }
                canvas.drawText(
                    "Lat %.5f°  •  Long %.5f°".format(location.latitude, location.longitude),
                    textLeft,
                    yOffset,
                    smallTextPaint
                )
                yOffset += 21f * scale

                // Telemetry chips
                val chipPaint = Paint().apply {
                    color = Color.argb(70, 255, 255, 255)
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                val chipTextPaint = Paint(textPaintSecondary).apply {
                    color = Color.WHITE
                    textSize = 12f * scale
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                }
                val chips = listOf(
                    "ALT ${altText.removePrefix("Alt: ")}",
                    "SPD ${speedText.removePrefix("Speed: ")}",
                    "DIR ${bearingText.removePrefix("Heading: ")}",
                    if (location.accuracy > 0f) "GPS ±${location.accuracy.toInt()}m" else "GPS LOCK"
                )
                var chipLeft = textLeft
                val chipTop = yOffset - 13f * scale
                val chipHeight = 24f * scale
                for (chip in chips) {
                    val chipWidth = chipTextPaint.measureText(chip) + 18f * scale
                    if (chipLeft + chipWidth <= textRight) {
                        val chipRect = RectF(chipLeft, chipTop, chipLeft + chipWidth, chipTop + chipHeight)
                        canvas.drawRoundRect(chipRect, chipHeight / 2f, chipHeight / 2f, chipPaint)
                        canvas.drawText(chip, chipLeft + 9f * scale, chipTop + 16.5f * scale, chipTextPaint)
                        chipLeft += chipWidth + 8f * scale
                    }
                }
                yOffset += 28f * scale

                // Date Time
                val datePaint = Paint(highlightPaint).apply {
                    color = Color.rgb(186, 195, 255)
                    textSize = 13f * scale
                }
                canvas.drawText("$premiumDateText   •   $premiumTimeText", textLeft, yOffset, datePaint)
            }
            else -> { // basic
                val cardPaintDetailed = Paint().apply {
                    color = Color.argb(190, 15, 17, 26)
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                val borderPaintDetailed = Paint().apply {
                    color = Color.argb(100, 255, 255, 255)
                    style = Paint.Style.STROKE
                    strokeWidth = 5f * scale
                    isAntiAlias = true
                }
                val labelPaint = Paint().apply {
                    color = Color.rgb(180, 190, 255)
                    textSize = 26f * scale
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    isAntiAlias = true
                }
                val valuePaint = Paint().apply {
                    color = Color.WHITE
                    textSize = 38f * scale
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    isAntiAlias = true
                }
                val timePaintDetailed = Paint().apply {
                    color = Color.WHITE
                    textSize = 62f * scale
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    isAntiAlias = true
                }
                val datePaintDetailed = Paint().apply {
                    color = Color.rgb(255, 215, 100)
                    textSize = 34f * scale
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    isAntiAlias = true
                }

                drawFullDetailedBasicStamp(
                    canvas = canvas,
                    width = width,
                    height = height,
                    location = location,
                    captureTime = captureTime,
                    captureDate = captureDate,
                    altText = altText.removePrefix("Alt: "),
                    speedText = speedText.removePrefix("Speed: "),
                    bearingText = bearingText.removePrefix("Heading: "),
                    scale = scale,
                    cardPaint = cardPaintDetailed,
                    borderPaint = borderPaintDetailed,
                    labelPaint = labelPaint,
                    valuePaint = valuePaint,
                    timePaint = timePaintDetailed,
                    datePaint = datePaintDetailed,
                    customNote = note
                )
            }
        }
    }

    private fun drawCompassDial(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        bearing: Float,
        scale: Float
    ) {
        val linePaint = Paint().apply {
            color = Color.argb(130, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2f * scale
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 16f * scale
            isAntiAlias = true
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val needlePaint = Paint().apply {
            color = Color.rgb(255, 80, 80) // Modern coral red indicator
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // Draw compass housing circle
        canvas.drawCircle(cx, cy, radius, linePaint)
        canvas.drawCircle(cx, cy, radius * 0.15f, linePaint)

        // Draw tick marks and labels rotating relative to bearing
        val cardinals = listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0)
        for ((label, degree) in cardinals) {
            val angleRad = Math.toRadians(degree - bearing - 90.0) // 0 deg is North, which is straight up (-90 in Canvas)
            val tx = cx + (radius - 20f * scale) * Math.cos(angleRad).toFloat()
            val ty = cy + (radius - 20f * scale) * Math.sin(angleRad).toFloat()
            
            canvas.drawText(label, tx, ty + 6f * scale, textPaint)
            
            // Draw small ticks
            val tickStartX = cx + radius * Math.cos(angleRad).toFloat()
            val tickStartY = cy + radius * Math.sin(angleRad).toFloat()
            val tickEndX = cx + (radius - 8f * scale) * Math.cos(angleRad).toFloat()
            val tickEndY = cy + (radius - 8f * scale) * Math.sin(angleRad).toFloat()
            canvas.drawLine(tickStartX, tickStartY, tickEndX, tickEndY, linePaint)
        }

        // Draw solid needle arrow pointing north (at angle of -bearing)
        val northRad = Math.toRadians(0.0 - bearing - 90.0)
        val needlePath = Path().apply {
            val endX = cx + (radius - 25f * scale) * Math.cos(northRad).toFloat()
            val endY = cy + (radius - 25f * scale) * Math.sin(northRad).toFloat()
            
            val leftRad = Math.toRadians(160.0 - bearing - 90.0)
            val leftX = cx + (radius * 0.3f) * Math.cos(leftRad).toFloat()
            val leftY = cy + (radius * 0.3f) * Math.sin(leftRad).toFloat()

            val rightRad = Math.toRadians(200.0 - bearing - 90.0)
            val rightX = cx + (radius * 0.3f) * Math.cos(rightRad).toFloat()
            val rightY = cy + (radius * 0.3f) * Math.sin(rightRad).toFloat()

            moveTo(endX, endY)
            lineTo(leftX, leftY)
            lineTo(cx, cy)
            lineTo(rightX, rightY)
            close()
        }
        canvas.drawPath(needlePath, needlePaint)
    }

    private fun getDirectionAbbreviation(bearing: Float): String {
        val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW", "N")
        val index = (((bearing + 22.5) / 45).toInt()) % 8
        return directions[index]
    }

    private fun wrapText(text: String, paint: Paint, widthLimit: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val measure = paint.measureText(testLine)
            if (measure > widthLimit) {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                    currentLine = word
                } else {
                    lines.add(word)
                }
            } else {
                currentLine = testLine
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }
        return lines
    }

    private fun getCountryFromAddress(address: String): String {
        val addrUpper = address.uppercase()
        return when {
            addrUpper.contains("UNITED STATES") || addrUpper.contains("USA") || addrUpper.contains("MOUNTAIN VIEW") || addrUpper.contains("GOOGLEPLEX") -> "USA"
            addrUpper.contains("INDIA") || addrUpper.contains("NEW DELHI") -> "India"
            addrUpper.contains("UNITED KINGDOM") || addrUpper.contains(" UK ") || addrUpper.contains("GREAT BRITAIN") || addrUpper.contains("LONDON") -> "UK"
            addrUpper.contains("GERMANY") || addrUpper.contains("DEUTSCHLAND") || addrUpper.contains("BERLIN") -> "Germany"
            addrUpper.contains("CANADA") || addrUpper.contains("TORONTO") || addrUpper.contains("OTTAWA") -> "Canada"
            addrUpper.contains("FRANCE") || addrUpper.contains("PARIS") -> "France"
            addrUpper.contains("JAPAN") || addrUpper.contains("TOKYO") -> "Japan"
            else -> "World"
        }
    }

    private fun drawFlagBadgeOnCanvas(canvas: Canvas, country: String, left: Float, top: Float, width: Float, height: Float, scale: Float) {
        val borderPaint = Paint().apply {
            color = Color.argb(120, 120, 120, 120)
            style = Paint.Style.STROKE
            strokeWidth = 1f * scale
            isAntiAlias = true
        }
        canvas.drawRect(RectF(left - 1f, top - 1f, left + width + 1f, top + height + 1f), borderPaint)

        val fillPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        when (country) {
            "USA" -> {
                val stripeHeight = height / 7f
                for (i in 0 until 7) {
                    fillPaint.color = if (i % 2 == 0) Color.rgb(211, 47, 47) else Color.WHITE
                    canvas.drawRect(RectF(left, top + i * stripeHeight, left + width, top + (i + 1) * stripeHeight), fillPaint)
                }
                fillPaint.color = Color.rgb(25, 118, 210)
                canvas.drawRect(RectF(left, top, left + width * 0.45f, top + stripeHeight * 4f), fillPaint)
            }
            "India" -> {
                val sh = height / 3f
                fillPaint.color = Color.rgb(255, 152, 0)
                canvas.drawRect(RectF(left, top, left + width, top + sh), fillPaint)
                fillPaint.color = Color.WHITE
                canvas.drawRect(RectF(left, top + sh, left + width, top + sh * 2f), fillPaint)
                fillPaint.color = Color.rgb(76, 175, 80)
                canvas.drawRect(RectF(left, top + sh * 2f, left + width, top + height), fillPaint)
                
                fillPaint.color = Color.rgb(13, 71, 161)
                canvas.drawCircle(left + width / 2f, top + sh * 1.5f, sh * 0.35f, fillPaint)
            }
            "Germany" -> {
                val sh = height / 3f
                fillPaint.color = Color.BLACK
                canvas.drawRect(RectF(left, top, left + width, top + sh), fillPaint)
                fillPaint.color = Color.rgb(211, 47, 47)
                canvas.drawRect(RectF(left, top + sh, left + width, top + sh * 2f), fillPaint)
                fillPaint.color = Color.rgb(255, 235, 59)
                canvas.drawRect(RectF(left, top + sh * 2f, left + width, top + height), fillPaint)
            }
            "France" -> {
                val sw = width / 3f
                fillPaint.color = Color.rgb(13, 71, 161)
                canvas.drawRect(RectF(left, top, left + sw, top + height), fillPaint)
                fillPaint.color = Color.WHITE
                canvas.drawRect(RectF(left + sw, top, left + sw * 2f, top + height), fillPaint)
                fillPaint.color = Color.rgb(211, 47, 47)
                canvas.drawRect(RectF(left + sw * 2f, top, left + width, top + height), fillPaint)
            }
            "Japan" -> {
                fillPaint.color = Color.WHITE
                canvas.drawRect(RectF(left, top, left + width, top + height), fillPaint)
                fillPaint.color = Color.rgb(211, 47, 47)
                canvas.drawCircle(left + width / 2f, top + height / 2f, height * 0.28f, fillPaint)
            }
            "UK" -> {
                fillPaint.color = Color.rgb(13, 71, 161)
                canvas.drawRect(RectF(left, top, left + width, top + height), fillPaint)
                
                val whitePaint = Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 3f * scale
                    isAntiAlias = true
                }
                val redPaint = Paint().apply {
                    color = Color.rgb(211, 47, 47)
                    style = Paint.Style.STROKE
                    strokeWidth = 1.5f * scale
                    isAntiAlias = true
                }
                
                canvas.drawLine(left, top, left + width, top + height, whitePaint)
                canvas.drawLine(left + width, top, left, top + height, whitePaint)
                canvas.drawLine(left, top, left + width, top + height, redPaint)
                canvas.drawLine(left + width, top, left, top + height, redPaint)
                
                fillPaint.color = Color.WHITE
                canvas.drawRect(RectF(left + width * 0.35f, top, left + width * 0.65f, top + height), fillPaint)
                canvas.drawRect(RectF(left, top + height * 0.35f, left + width, top + height * 0.65f), fillPaint)
                
                fillPaint.color = Color.rgb(211, 47, 47)
                canvas.drawRect(RectF(left + width * 0.42f, top, left + width * 0.58f, top + height), fillPaint)
                canvas.drawRect(RectF(left, top + height * 0.42f, left + width, top + height * 0.58f), fillPaint)
            }
            "Canada" -> {
                val sw = width / 4f
                fillPaint.color = Color.rgb(211, 47, 47)
                canvas.drawRect(RectF(left, top, left + sw, top + height), fillPaint)
                fillPaint.color = Color.WHITE
                canvas.drawRect(RectF(left + sw, top, left + width - sw, top + height), fillPaint)
                fillPaint.color = Color.rgb(211, 47, 47)
                canvas.drawRect(RectF(left + width - sw, top, left + width, top + height), fillPaint)
                
                fillPaint.color = Color.rgb(211, 47, 47)
                val leafPath = Path().apply {
                    val cx = left + width / 2f
                    val cy = top + height / 2f
                    moveTo(cx, cy - 6f * scale)
                    lineTo(cx + 5f * scale, cy)
                    lineTo(cx, cy + 6f * scale)
                    lineTo(cx - 5f * scale, cy)
                    close()
                }
                canvas.drawPath(leafPath, fillPaint)
            }
            else -> {
                fillPaint.color = Color.rgb(176, 190, 197)
                canvas.drawRect(RectF(left, top, left + width, top + height), fillPaint)
                fillPaint.color = Color.rgb(0, 172, 193)
                canvas.drawCircle(left + width / 2f, top + height / 2f, height * 0.35f, fillPaint)
                
                val gridPaint = Paint().apply {
                    color = Color.argb(120, 255, 255, 255)
                    style = Paint.Style.STROKE
                    strokeWidth = 1f * scale
                    isAntiAlias = true
                }
                canvas.drawCircle(left + width / 2f, top + height / 2f, height * 0.22f, gridPaint)
            }
        }
    }

    private fun drawFullDetailedBasicStamp(
        canvas: Canvas,
        width: Int,
        height: Int,
        location: GpsLocation,
        captureTime: String,
        captureDate: String,
        altText: String,
        speedText: String,
        bearingText: String,
        scale: Float,
        cardPaint: Paint,
        borderPaint: Paint,
        labelPaint: Paint,
        valuePaint: Paint,
        timePaint: Paint,
        datePaint: Paint,
        customNote: String? = null
    ) {
        val padding = 14f * scale
        val isLandscape = width > height
        val maxCardWidth = if (isLandscape) 560f * scale else width - 100f * scale
        val cardWidth = (width - 100f * scale).coerceAtMost(maxCardWidth)
        val left = 50f * scale
        val rightSidebarWidth = 90f * scale
        
        val leftAccentWidth = 5f * scale
        val mainTextLeft = left + leftAccentWidth + 14f * scale
        val mainTextRight = left + cardWidth - rightSidebarWidth - 14f * scale
        val maxTextWidth = (mainTextRight - mainTextLeft).toInt()

        // Wrap address text dynamically
        val addressPaint = Paint(valuePaint).apply { 
            textSize = 20f * scale
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val wrappedAddress = wrapText(location.address, addressPaint, maxTextWidth)
        val addressLines = wrappedAddress.take(3)
        val addressLinesCount = addressLines.size.coerceAtLeast(1)

        val note = if (customNote.isNullOrBlank()) null else customNote.trim()
        val noteLineHeight = if (note != null) 28f * scale else 0f
        // Calculate card height dynamically
        val requiredHeight = padding * 2 + (addressLinesCount * 24f + 22f + 32f + 22f + 16f) * scale + noteLineHeight
        val cardHeight = requiredHeight.coerceAtLeast(154f * scale)

        val bottom = height - 80f * scale
        val top = bottom - cardHeight
        val rect = RectF(left, top, left + cardWidth, bottom)
        val radius = 20f * scale

        // 1. Draw main card background (Color(0xD91A1B1F) => argb(217, 26, 27, 31))
        val bgPaint = Paint().apply {
            color = Color.argb(217, 26, 27, 31)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(rect, radius, radius, bgPaint)

        // 2. Draw card border
        val borderPaintLocal = Paint().apply {
            color = Color.argb(40, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1f * scale
            isAntiAlias = true
        }
        canvas.drawRoundRect(rect, radius, radius, borderPaintLocal)

        // Save canvas state for clipped drawings
        val path = Path().apply {
            addRoundRect(rect, radius, radius, Path.Direction.CW)
        }

        // 3. Draw left yellow accent line
        val accentPaint = Paint().apply {
            color = Color.rgb(255, 193, 7) // Color(0xFFFFC107)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawRect(RectF(left, top, left + leftAccentWidth, bottom), accentPaint)
        canvas.restore()

        // 4. Draw Right Sidebar background (Color.White.copy(alpha = 0.055f))
        val rightSidebarBgPaint = Paint().apply {
            color = Color.argb(14, 255, 255, 255)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawRect(RectF(left + cardWidth - rightSidebarWidth, top, left + cardWidth, bottom), rightSidebarBgPaint)
        canvas.restore()

        // 5. Draw main column text (left side)
        var y = top + padding + 18f * scale

        // Draw pin icon before address
        val pinPaint = Paint().apply {
            color = Color.rgb(255, 202, 40) // Color(0xFFFFCA28)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val pinX = mainTextLeft
        val pinY = y - 4f * scale
        canvas.drawCircle(pinX + 6f * scale, pinY - 4f * scale, 6f * scale, pinPaint)
        val pinTriangle = Path().apply {
            moveTo(pinX + 1f * scale, pinY - 2f * scale)
            lineTo(pinX + 11f * scale, pinY - 2f * scale)
            lineTo(pinX + 6f * scale, pinY + 6f * scale)
            close()
        }
        canvas.drawPath(pinTriangle, pinPaint)
        val pinInnerPaint = Paint().apply {
            color = Color.argb(217, 26, 27, 31)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(pinX + 6f * scale, pinY - 4f * scale, 2f * scale, pinInnerPaint)

        // Draw wrapped address text
        for (i in addressLines.indices) {
            val line = addressLines[i]
            val textX = if (i == 0) mainTextLeft + 18f * scale else mainTextLeft
            canvas.drawText(line, textX, y, addressPaint)
            y += 24f * scale
        }
        y += 4f * scale

        if (note != null) {
            y += 4f * scale
            val notePaintLocal = Paint().apply {
                color = Color.rgb(255, 202, 40)
                textSize = 16f * scale
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText("📌 $note", mainTextLeft, y, notePaintLocal)
            y += 24f * scale
        }

        // Coordinates
        val coordsPaint = Paint().apply {
            color = Color.argb(210, 255, 255, 255) // White.copy(alpha = 0.82f)
            textSize = 14f * scale
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            isAntiAlias = true
        }
        val coordsStr = "%.6f°, %.6f°".format(location.latitude, location.longitude)
        canvas.drawText(coordsStr, mainTextLeft, y, coordsPaint)
        y += 28f * scale

        // Time and Date
        val timePaintLocal = Paint().apply {
            color = Color.WHITE
            textSize = 22f * scale
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            isAntiAlias = true
        }
        val datePaintLocal = Paint().apply {
            color = Color.rgb(255, 202, 40) // Color(0xFFFFCA28)
            textSize = 11f * scale
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText(captureTime, mainTextLeft, y, timePaintLocal)
        val timeWidth = timePaintLocal.measureText(captureTime)
        canvas.drawText(captureDate, mainTextLeft + timeWidth + 10f * scale, y - 2f * scale, datePaintLocal)
        y += 28f * scale

        // Metrics Row (Speed & Altitude side-by-side)
        val metricLabelPaint = Paint().apply {
            color = Color.argb(230, 255, 255, 255)
            textSize = 11f * scale
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            isAntiAlias = true
        }
        val metricIconPaint = Paint().apply {
            color = Color.rgb(255, 202, 40)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        
        // Speed
        val speedIconX = mainTextLeft
        val speedIconY = y - 9f * scale
        canvas.drawCircle(speedIconX + 6f * scale, speedIconY + 2f * scale, 6f * scale, metricIconPaint)
        val speedInner = Paint().apply {
            color = Color.argb(217, 26, 27, 31)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(speedIconX + 6f * scale, speedIconY + 2f * scale, 3.5f * scale, speedInner)
        canvas.drawLine(speedIconX + 6f * scale, speedIconY + 2f * scale, speedIconX + 9f * scale, speedIconY - 1f * scale, metricIconPaint)
        
        canvas.drawText(speedText.removePrefix("Speed: "), mainTextLeft + 16f * scale, y, metricLabelPaint)
        
        // Altitude
        val speedTextWidth = metricLabelPaint.measureText(speedText.removePrefix("Speed: "))
        val altX = mainTextLeft + 16f * scale + speedTextWidth + 24f * scale
        
        val altIconX = altX
        val altIconY = y - 9f * scale
        val upArrow = Path().apply {
            moveTo(altIconX + 6f * scale, altIconY - 2f * scale)
            lineTo(altIconX + 1f * scale, altIconY + 3f * scale)
            lineTo(altIconX + 11f * scale, altIconY + 3f * scale)
            close()
            moveTo(altIconX + 6f * scale, altIconY + 2f * scale)
            lineTo(altIconX + 2f * scale, altIconY + 7f * scale)
            lineTo(altIconX + 10f * scale, altIconY + 7f * scale)
            close()
        }
        canvas.drawPath(upArrow, metricIconPaint)
        canvas.drawText(altText.removePrefix("Alt: "), altX + 16f * scale, y, metricLabelPaint)

        // 6. Draw Right Sidebar Content
        val sidebarCenterX = left + cardWidth - rightSidebarWidth / 2f
        val sidebarItemY1 = top + cardHeight * 0.3f
        val sidebarItemY2 = top + cardHeight * 0.7f
        
        val sidebarTextPaint = Paint().apply {
            color = Color.argb(230, 255, 255, 255)
            textSize = 10f * scale
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val sidebarIconPaint = Paint().apply {
            color = Color.rgb(255, 202, 40)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // Draw Compass/Heading Item
        canvas.drawCircle(sidebarCenterX, sidebarItemY1 - 16f * scale, 10f * scale, sidebarIconPaint)
        canvas.drawCircle(sidebarCenterX, sidebarItemY1 - 16f * scale, 8.5f * scale, speedInner)
        val exploreNeedle = Path().apply {
            moveTo(sidebarCenterX, sidebarItemY1 - 22f * scale)
            lineTo(sidebarCenterX - 3f * scale, sidebarItemY1 - 14f * scale)
            lineTo(sidebarCenterX + 3f * scale, sidebarItemY1 - 14f * scale)
            close()
            moveTo(sidebarCenterX, sidebarItemY1 - 10f * scale)
            lineTo(sidebarCenterX - 3f * scale, sidebarItemY1 - 18f * scale)
            lineTo(sidebarCenterX + 3f * scale, sidebarItemY1 - 18f * scale)
            close()
        }
        canvas.drawPath(exploreNeedle, sidebarIconPaint)
        canvas.drawText(bearingText, sidebarCenterX, sidebarItemY1 + 10f * scale, sidebarTextPaint)

        // Draw GPS Status Item
        canvas.drawCircle(sidebarCenterX, sidebarItemY2 - 16f * scale, 9f * scale, sidebarIconPaint)
        canvas.drawCircle(sidebarCenterX, sidebarItemY2 - 16f * scale, 7.5f * scale, speedInner)
        canvas.drawCircle(sidebarCenterX, sidebarItemY2 - 16f * scale, 3f * scale, sidebarIconPaint)
        canvas.drawLine(sidebarCenterX - 11f * scale, sidebarItemY2 - 16f * scale, sidebarCenterX + 11f * scale, sidebarItemY2 - 16f * scale, sidebarIconPaint)
        canvas.drawLine(sidebarCenterX, sidebarItemY2 - 27f * scale, sidebarCenterX, sidebarItemY2 - 5f * scale, sidebarIconPaint)

        val accuracyStr = if (location.accuracy > 0f) "A%.0f m".format(location.accuracy) else "Locking?"
        canvas.drawText(accuracyStr, sidebarCenterX, sidebarItemY2 + 10f * scale, sidebarTextPaint)
    }

    private fun downloadTile(urlString: String): Bitmap? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0) Gecko/109.0 Firefox/115.0")
            connection.inputStream.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchStaticMap(latitude: Double, longitude: Double, mapType: String, apiKey: String): Bitmap? {
        val zoom = 16
        val n = 65536.0 // 2^16
        val xFraction = (longitude + 180.0) / 360.0 * n
        val latClamped = latitude.coerceIn(-85.0511, 85.0511)
        val latRad = latClamped * kotlin.math.PI / 180.0
        val yFraction = (1.0 - kotlin.math.ln(kotlin.math.tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / kotlin.math.PI) / 2.0 * n

        val xIndex = xFraction.toInt()
        val yIndex = yFraction.toInt()

        val offsetX = (xFraction - xIndex) * 256
        val offsetY = (yFraction - yIndex) * 256

        val xMin = if (offsetX > 128) xIndex else xIndex - 1
        val yMin = if (offsetY > 128) yIndex else yIndex - 1

        val xMinWrapped = (xMin % 65536 + 65536) % 65536
        val yMinClamped = yMin.coerceIn(0, 65535)

        val xMinPlus1 = ((xMin + 1) % 65536 + 65536) % 65536
        val yMinPlus1 = (yMin + 1).coerceIn(0, 65535)

        fun getUrl(x: Int, y: Int): String {
            return when (mapType) {
                "satellite" -> "https://mt1.google.com/vt/lyrs=y&x=$x&y=$y&z=$zoom"
                "dark" -> "https://a.basemaps.cartocdn.com/dark_all/$zoom/$x/$y.png"
                "grey" -> "https://a.basemaps.cartocdn.com/light_all/$zoom/$x/$y.png"
                else -> "https://mt1.google.com/vt/lyrs=m&x=$x&y=$y&z=$zoom"
            }
        }

        val url1 = getUrl(xMinWrapped, yMinClamped)
        val url2 = getUrl(xMinPlus1, yMinClamped)
        val url3 = getUrl(xMinWrapped, yMinPlus1)
        val url4 = getUrl(xMinPlus1, yMinPlus1)

        val tile1 = downloadTile(url1) ?: return null
        val tile2 = downloadTile(url2) ?: return null
        val tile3 = downloadTile(url3) ?: return null
        val tile4 = downloadTile(url4) ?: return null

        val stitched = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(stitched)
        canvas.drawBitmap(tile1, 0f, 0f, null)
        canvas.drawBitmap(tile2, 256f, 0f, null)
        canvas.drawBitmap(tile3, 0f, 256f, null)
        canvas.drawBitmap(tile4, 256f, 256f, null)

        tile1.recycle()
        tile2.recycle()
        tile3.recycle()
        tile4.recycle()

        val pixelX = (xFraction - xMin) * 256
        val pixelY = (yFraction - yMin) * 256

        val cropSize = 256
        val cropLeft = (pixelX - cropSize / 2).toInt().coerceIn(0, 512 - cropSize)
        val cropTop = (pixelY - cropSize / 2).toInt().coerceIn(0, 512 - cropSize)
        return try {
            val cropped = Bitmap.createBitmap(stitched, cropLeft, cropTop, cropSize, cropSize)
            stitched.recycle()
            cropped
        } catch (e: Exception) {
            stitched.recycle()
            null
        }
    }

    private fun generateQrCode(text: String, width: Int, height: Int): Bitmap? {
        return try {
            val writer = com.google.zxing.qrcode.QRCodeWriter()
            val bitMatrix = writer.encode(text, com.google.zxing.BarcodeFormat.QR_CODE, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
