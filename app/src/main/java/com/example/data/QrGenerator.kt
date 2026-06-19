package com.example.data

import android.graphics.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

object QrGenerator {

    fun generateQrBitmap(qrCode: QrCode, sizePixels: Int = 512): Bitmap {
        val finalSize = sizePixels.coerceIn(200, 1024)
        val bitmap = Bitmap.createBitmap(finalSize, finalSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // 1. Setup background brush/paint
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        
        if (qrCode.transparentBackground) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        } else {
            if (qrCode.isGradient) {
                val shader = if (qrCode.gradientType == "RADIAL") {
                    RadialGradient(
                        finalSize / 2f, finalSize / 2f, finalSize * 0.7f,
                        qrCode.backgroundColor, qrCode.gradientColor2,
                        Shader.TileMode.CLAMP
                    )
                } else {
                    LinearGradient(
                        0f, 0f, finalSize.toFloat(), finalSize.toFloat(),
                        qrCode.backgroundColor, qrCode.gradientColor2,
                        Shader.TileMode.CLAMP
                    )
                }
                bgPaint.shader = shader
                canvas.drawRect(0f, 0f, finalSize.toFloat(), finalSize.toFloat(), bgPaint)
            } else {
                bgPaint.color = qrCode.backgroundColor
                canvas.drawRect(0f, 0f, finalSize.toFloat(), finalSize.toFloat(), bgPaint)
            }
        }
        
        // 2. Generate BitMatrix using ZXing
        val hints: MutableMap<EncodeHintType, Any> = EnumMap(EncodeHintType::class.java)
        hints[EncodeHintType.ERROR_CORRECTION] = when (qrCode.errorCorrectionLevel) {
            "L" -> ErrorCorrectionLevel.L
            "Q" -> ErrorCorrectionLevel.Q
            "H" -> ErrorCorrectionLevel.H
            else -> ErrorCorrectionLevel.M
        }
        hints[EncodeHintType.MARGIN] = 0
        
        val bitMatrix = try {
            QRCodeWriter().encode(qrCode.content, BarcodeFormat.QR_CODE, 0, 0, hints)
        } catch (e: Exception) {
            return generateFallbackBitmap(finalSize, "Error encoding data: ${e.message}")
        }
        
        val width = bitMatrix.width
        val height = bitMatrix.height
        
        // Calculate cell sizes
        // If there's a frame style, reserve bottom/top padding for text frame
        val frameOffsetTop = 0f
        val frameWeightBottom = if (qrCode.frameStyle != "NONE" && !qrCode.frameText.isNullOrBlank()) 0.15f else 0.0f
        
        val qrAreaYStart = 0f
        val qrAreaHeight = finalSize * (1f - frameWeightBottom)
        val qrAreaWidth = finalSize.toFloat()
        
        var qrSize = qrAreaHeight.coerceAtMost(qrAreaWidth)
        val xOffset = (finalSize - qrSize) / 2f
        val yOffset = (qrAreaHeight - qrSize) / 2f
        
        val cellSize = qrSize / width
        
        // Setup foreground paint
        val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = qrCode.foregroundColor
        }
        
        if (qrCode.isGradient) {
            val fgShader = if (qrCode.gradientType == "RADIAL") {
                RadialGradient(
                    finalSize / 2f, finalSize / 2f, qrSize * 0.5f,
                    qrCode.foregroundColor, qrCode.gradientColor2,
                    Shader.TileMode.CLAMP
                )
            } else {
                LinearGradient(
                    xOffset, yOffset, xOffset + qrSize, yOffset + qrSize,
                    qrCode.foregroundColor, qrCode.gradientColor2,
                    Shader.TileMode.CLAMP
                )
            }
            fgPaint.shader = fgShader
        }
        
        // Clear center area range if logo is present
        val clearRadius = if (!qrCode.logoAsset.isNullOrBlank()) {
            val scale = qrCode.logoScale.coerceIn(0.1f, 0.25f)
            (width * scale / 2f).toInt() + 1
        } else 0
        
        val centerCol = width / 2
        val centerRow = height / 2
        
        val clearRangeCol = (centerCol - clearRadius)..(centerCol + clearRadius)
        val clearRangeRow = (centerRow - clearRadius)..(centerRow + clearRadius)
        
        // 3. Draw modules (pixels)
        for (col in 0 until width) {
            for (row in 0 until height) {
                // Skip if cell is inactive
                if (!bitMatrix.get(col, row)) continue
                
                // Skip if inside Finder pattern buffer (we draw themed eyes subsequently)
                if (isFinderPattern(col, row, width, height)) continue
                
                // Skip if inside Logo space
                if (clearRadius > 0 && col in clearRangeCol && row in clearRangeRow) {
                    continue
                }
                
                // Calc pixel position
                val px = xOffset + col * cellSize
                val py = yOffset + row * cellSize
                
                // Draw shaped module
                drawModuleShape(canvas, px, py, cellSize, qrCode.moduleShape, fgPaint)
            }
        }
        
        // 4. Draw Custom Finder Patterns (Eyes)
        drawFinderEye(canvas, xOffset, yOffset, cellSize, 0, 0, qrCode.eyeStyle, fgPaint, qrCode.backgroundColor, qrCode.transparentBackground)
        drawFinderEye(canvas, xOffset, yOffset, cellSize, width - 7, 0, qrCode.eyeStyle, fgPaint, qrCode.backgroundColor, qrCode.transparentBackground)
        drawFinderEye(canvas, xOffset, yOffset, cellSize, 0, height - 7, qrCode.eyeStyle, fgPaint, qrCode.backgroundColor, qrCode.transparentBackground)
        
        // 5. Draw Centered Logo
        if (!qrCode.logoAsset.isNullOrBlank()) {
            drawCenteredLogo(
                canvas = canvas,
                px = xOffset + centerCol * cellSize + cellSize / 2f,
                py = yOffset + centerRow * cellSize + cellSize / 2f,
                rawRadius = clearRadius * cellSize,
                assetType = qrCode.logoAsset,
                bgColor = if (qrCode.transparentBackground) Color.WHITE else qrCode.backgroundColor,
                fgColor = qrCode.foregroundColor,
                paddingDp = qrCode.logoPaddingDp,
                titlePrefix = qrCode.title.firstOrNull()?.uppercase()?.toString() ?: "Q"
            )
        }
        
        // 6. Draw Frame / Banner if configured
        if (qrCode.frameStyle != "NONE" && !qrCode.frameText.isNullOrBlank()) {
            drawFrameBanner(
                canvas = canvas,
                width = finalSize,
                height = finalSize,
                qrAreaHeight = qrAreaHeight,
                text = qrCode.frameText,
                frameStyle = qrCode.frameStyle,
                color = qrCode.foregroundColor,
                textColor = if (qrCode.transparentBackground) Color.BLACK else qrCode.backgroundColor
            )
        }
        
        return bitmap
    }
    
    private fun isFinderPattern(col: Int, row: Int, width: Int, height: Int): Boolean {
        return (col < 8 && row < 8) || 
               (col >= width - 8 && row < 8) || 
               (col < 8 && row >= height - 8)
    }
    
    private fun drawModuleShape(canvas: Canvas, x: Float, y: Float, size: Float, shape: String, paint: Paint) {
        val pad = size * 0.05f
        val left = x + pad
        val top = y + pad
        val right = x + size - pad
        val bottom = y + size - pad
        
        when (shape) {
            "CIRCLE" -> {
                canvas.drawCircle(x + size / 2f, y + size / 2f, (size - pad * 2) / 2f, paint)
            }
            "ROUNDED" -> {
                val r = size * 0.35f
                canvas.drawRoundRect(left, top, right, bottom, r, r, paint)
            }
            "DIAMOND" -> {
                val path = Path().apply {
                    moveTo(x + size / 2f, top)
                    lineTo(right, y + size / 2f)
                    lineTo(x + size / 2f, bottom)
                    lineTo(left, y + size / 2f)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            else -> { // "SQUARE"
                canvas.drawRect(left, top, right, bottom, paint)
            }
        }
    }
    
    private fun drawFinderEye(
        canvas: Canvas, xOffset: Float, yOffset: Float, cellSize: Float,
        col: Int, row: Int, eyeStyle: String, fgPaint: Paint, bgColorInt: Int, isTransparent: Boolean
    ) {
        val px = xOffset + col * cellSize
        val py = yOffset + row * cellSize
        
        val outerRadius = cellSize * 3.5f
        val centerXP = px + outerRadius
        val legacyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (isTransparent) Color.WHITE else bgColorInt
        }
        
        when (eyeStyle) {
            "CIRCULAR", "CIRCLE" -> {
                // Outer ring (7x7 cells)
                canvas.drawCircle(centerXP, py + outerRadius, cellSize * 3.5f, fgPaint)
                // Middle gap (5x5 cells hollow ring)
                canvas.drawCircle(centerXP, py + outerRadius, cellSize * 2.5f, legacyBgPaint)
                // Inner solid circle (3x3 cells)
                canvas.drawCircle(centerXP, py + outerRadius, cellSize * 1.5f, fgPaint)
            }
            "LEAF" -> {
                // Top-left and bottom-right rounded, top-right and bottom-left sharp
                // Outer ring shape
                val outerPath = getLeafPath(px, py, cellSize * 7)
                canvas.drawPath(outerPath, fgPaint)
                
                // Middle gap
                val midPath = getLeafPath(px + cellSize, py + cellSize, cellSize * 5)
                canvas.drawPath(midPath, legacyBgPaint)
                
                // Inner solid leaf
                val innerPath = getLeafPath(px + cellSize * 2, py + cellSize * 2, cellSize * 3)
                canvas.drawPath(innerPath, fgPaint)
            }
            "SHARP" -> {
                // Diamond style finder eye structure
                val outerPath = getDiamondPath(px + outerRadius, py + outerRadius, cellSize * 3.5f)
                canvas.drawPath(outerPath, fgPaint)
                
                val midPath = getDiamondPath(px + outerRadius, py + outerRadius, cellSize * 2.5f)
                canvas.drawPath(midPath, legacyBgPaint)
                
                val innerPath = getDiamondPath(px + outerRadius, py + outerRadius, cellSize * 1.5f)
                canvas.drawPath(innerPath, fgPaint)
            }
            else -> { // "CLASSIC" / "SQUARE"
                // Outer border (7x7)
                canvas.drawRect(px, py, px + cellSize * 7, py + cellSize * 7, fgPaint)
                // Inner gap (5x5)
                canvas.drawRect(px + cellSize, py + cellSize, px + cellSize * 6, py + cellSize * 6, legacyBgPaint)
                // Inner solid (3x3)
                canvas.drawRect(px + cellSize * 2, py + cellSize * 2, px + cellSize * 5, py + cellSize * 5, fgPaint)
            }
        }
    }
    
    private fun getLeafPath(left: Float, top: Float, size: Float): Path {
        val right = left + size
        val bottom = top + size
        return Path().apply {
            moveTo(left + size / 2f, top)
            // Quadratic curve to top-right (sharp or slightly rounded)
            lineTo(right, top)
            // Curve with leaf sweep to bottom-right (curved)
            quadTo(right, bottom, left + size / 2f, bottom)
            // Straight to bottom-left (sharp)
            lineTo(left, bottom)
            // Sweep sweep back to top-left (curved)
            quadTo(left, top, left + size / 2f, top)
            close()
        }
    }
    
    private fun getDiamondPath(cx: Float, cy: Float, radius: Float): Path {
        return Path().apply {
            moveTo(cx, cy - radius)
            lineTo(cx + radius, cy)
            lineTo(cx, cy + radius)
            lineTo(cx - radius, cy)
            close()
        }
    }
    
    private fun drawCenteredLogo(
        canvas: Canvas, px: Float, py: Float, rawRadius: Float,
        assetType: String, bgColor: Int, fgColor: Int, paddingDp: Int,
        titlePrefix: String
    ) {
        val r = rawRadius * 0.95f
        val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        // Draw backing circles for contrast popup
        logoPaint.color = bgColor
        logoPaint.style = Paint.Style.FILL
        canvas.drawCircle(px, py, r, logoPaint)
        
        // Border ring
        logoPaint.color = fgColor
        logoPaint.style = Paint.Style.STROKE
        logoPaint.strokeWidth = r * 0.1f
        canvas.drawCircle(px, py, r, logoPaint)
        
        // Draw the sleek vector logo representing the content type
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = r * 0.15f
            strokeCap = Paint.Cap.ROUND
            color = fgColor
        }
        
        val size = r * 0.85f
        when (assetType.uppercase()) {
            "WIFI" -> {
                // Draw Wi-Fi concentric waves
                iconPaint.style = Paint.Style.FILL
                // Center dot
                canvas.drawCircle(px, py + size * 0.35f, size * 0.15f, iconPaint)
                iconPaint.style = Paint.Style.STROKE
                val rect1 = RectF(px - size * 0.4f, py - size * 0.1f, px + size * 0.4f, py + size * 0.7f)
                canvas.drawArc(rect1, 200f, 140f, false, iconPaint)
                val rect2 = RectF(px - size * 0.8f, py - size * 0.5f, px + size * 0.8f, py + size * 1.1f)
                canvas.drawArc(rect2, 200f, 140f, false, iconPaint)
            }
            "URL", "LINK" -> {
                // sleeks links chain
                val linkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = r * 0.12f
                    color = fgColor
                    strokeCap = Paint.Cap.ROUND
                }
                // Link 1
                val r1 = RectF(px - size * 0.45f, py - size * 0.35f, px + size * 0.05f, py + size * 0.15f)
                canvas.drawRoundRect(r1, size * 0.25f, size * 0.25f, linkPaint)
                // Link 2
                val r2 = RectF(px - size * 0.05f, py - size * 0.15f, px + size * 0.45f, py + size * 0.35f)
                canvas.drawRoundRect(r2, size * 0.25f, size * 0.25f, linkPaint)
                // Connection bar
                canvas.drawLine(px - size * 0.1f, py - size * 0.1f, px + size * 0.1f, py + size * 0.1f, linkPaint)
            }
            "VCARD", "CONTACT" -> {
                // Identity card silhouette / User avatar
                iconPaint.style = Paint.Style.FILL
                // Head
                canvas.drawCircle(px, py - size * 0.2f, size * 0.25f, iconPaint)
                // Shoulders
                val shoulderPath = Path().apply {
                    moveTo(px - size * 0.5f, py + size * 0.4f)
                    quadTo(px, py + size * 0.05f, px + size * 0.5f, py + size * 0.4f)
                    lineTo(px - size * 0.5f, py + size * 0.4f)
                    close()
                }
                canvas.drawPath(shoulderPath, iconPaint)
            }
            "EMAIL" -> {
                // Sleek Envelope
                canvas.drawRect(px - size * 0.5f, py - size * 0.35f, px + size * 0.5f, py + size * 0.35f, iconPaint)
                // Lapels
                canvas.drawLine(px - size * 0.5f, py - size * 0.35f, px, py + size * 0.05f, iconPaint)
                canvas.drawLine(px + size * 0.5f, py - size * 0.35f, px, py + size * 0.05f, iconPaint)
            }
            "SMS" -> {
                // Sleek chat bubble
                iconPaint.style = Paint.Style.STROKE
                val msgRect = RectF(px - size * 0.52f, py - size * 0.4f, px + size * 0.52f, py + size * 0.25f)
                canvas.drawRoundRect(msgRect, size * 0.2f, size * 0.2f, iconPaint)
                iconPaint.style = Paint.Style.FILL
                val triPath = Path().apply {
                    moveTo(px - size * 0.2f, py + size * 0.25f)
                    lineTo(px - size * 0.35f, py + size * 0.55f)
                    lineTo(px - size * 0.05f, py + size * 0.25f)
                    close()
                }
                canvas.drawPath(triPath, iconPaint)
            }
            "GEO", "LOCATION" -> {
                // Map PIN marker
                iconPaint.style = Paint.Style.FILL
                val pinPath = Path().apply {
                    moveTo(px, py + size * 0.55f)
                    quadTo(px - size * 0.45f, py - size * 0.05f, px - size * 0.45f, py - size * 0.2f)
                    arcTo(RectF(px - size * 0.45f, py - size * 0.65f, px + size * 0.45f, py + size * 0.25f), 180f, 180f)
                    quadTo(px + size * 0.45f, py - size * 0.05f, px, py + size * 0.55f)
                    close()
                }
                canvas.drawPath(pinPath, iconPaint)
                // Center inner dot
                logoPaint.color = bgColor
                canvas.drawCircle(px, py - size * 0.2f, size * 0.15f, logoPaint)
            }
            "SOCIAL", "INSTAGRAM", "LINKEDIN", "X" -> {
                // Bold stylish "G" logo representing Architect or branding
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = fgColor
                    textSize = size * 1.1f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                val metric = textPaint.fontMetrics
                val offset = (metric.ascent + metric.descent) / 2f
                val symbol = when (assetType.uppercase()) {
                    "INSTAGRAM" -> "📸"
                    "LINKEDIN" -> "in"
                    "X" -> "𝕏"
                    else -> "★"
                }
                canvas.drawText(symbol, px, py - offset, textPaint)
            }
            else -> {
                // Standard default letter avatar
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = fgColor
                    textSize = size * 1.1f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                val metric = textPaint.fontMetrics
                val offset = (metric.ascent + metric.descent) / 2f
                val prefix = titlePrefix
                canvas.drawText(prefix, px, py - offset, textPaint)
            }
        }
    }
    
    private fun drawFrameBanner(
        canvas: Canvas, width: Int, height: Int, qrAreaHeight: Float,
        text: String, frameStyle: String, color: Int, textColor: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.FILL
            this.color = color
        }
        
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = textColor
            textSize = height * 0.045f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        
        when (frameStyle) {
            "MODERN_PIN" -> {
                // Draw pill shaped banner centered at bottom
                val padX = width * 0.1f
                val top = qrAreaHeight + height * 0.02f
                val bottom = height - height * 0.03f
                val rect = RectF(padX, top, width - padX, bottom)
                val rx = (bottom - top) / 2f
                canvas.drawRoundRect(rect, rx, rx, paint)
                
                // Text
                val metric = textPaint.fontMetrics
                val offset = (metric.ascent + metric.descent) / 2f
                canvas.drawText(text, width / 2f, rect.centerY() - offset, textPaint)
            }
            "SOLID_TAG" -> {
                // Full bottom width solid cover block
                canvas.drawRect(0f, qrAreaHeight, width.toFloat(), height.toFloat(), paint)
                
                // Text
                val rect = RectF(0f, qrAreaHeight, width.toFloat(), height.toFloat())
                val metric = textPaint.fontMetrics
                val offset = (metric.ascent + metric.descent) / 2f
                canvas.drawText(text, width / 2f, rect.centerY() - offset, textPaint)
            }
            else -> { // "SIMPLE_BANNER"
                // Simple outline or underline block
                val top = qrAreaHeight + height * 0.01f
                val bottom = qrAreaHeight + height * 0.02f
                canvas.drawRect(width * 0.25f, top, width * 0.75f, bottom, paint)
                
                // Text
                textPaint.color = color // Simple gets the primary foreground color
                val textY = height - height * 0.03f
                canvas.drawText(text.uppercase(), width / 2f, textY, textPaint)
            }
        }
    }
    
    private fun generateFallbackBitmap(size: Int, errorMsg: String): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.LTGRAY)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            textSize = size * 0.04f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("QR CODE ERROR", size / 2f, size * 0.4f, paint)
        paint.color = Color.BLACK
        paint.typeface = Typeface.DEFAULT
        paint.textSize = size * 0.035f
        canvas.drawText(errorMsg, size / 2f, size * 0.55f, paint)
        return bitmap
    }
}
