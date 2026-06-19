package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "qr_codes")
data class QrCode(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val type: String, // URL, TEXT, VCARD, WIFI, EMAIL, SMS, GEO, CRYPTO, SOCIAL
    val creationDate: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val tag: String? = null,
    val subFolder: String? = null,
    val scanCount: Int = 0,
    
    // Design Styles
    val foregroundColor: Int = -16777216, // Black (#000000)
    val backgroundColor: Int = -1,        // White (#FFFFFF)
    val isGradient: Boolean = false,
    val gradientColor2: Int = -16777216,
    val gradientType: String = "LINEAR",  // LINEAR, RADIAL
    val transparentBackground: Boolean = false,
    val moduleShape: String = "SQUARE",   // SQUARE, CIRCLE, ROUNDED, DIAMOND
    val eyeStyle: String = "CLASSIC",     // CLASSIC, CIRCLE, LEAF, SHARP
    val logoAsset: String? = null,        // Builtin logos or icon descriptors
    val logoScale: Float = 0.2f,          // Centered logo scale (ratio to total qr width)
    val logoPaddingDp: Int = 4,           // Padding around center logo
    val frameText: String? = null,        // Outer label text e.g., "Scan Me"
    val frameStyle: String = "NONE",      // NONE, MODERN_PIN, SIMPLE_BANNER, SOLID_TAG
    val errorCorrectionLevel: String = "M", // L, M, Q, H
    
    // Dynamic Redirection & Settings
    val isDynamic: Boolean = false,
    val dynamicRedirectUrl: String? = null,
    val redirectHistoryJson: String = "[]", // Serialized list of changes
    val utmSource: String? = null,
    val utmMedium: String? = null,
    val utmCampaign: String? = null,
    val customDomain: String? = null,
    
    // Advanced Access Settings
    val password: String? = null,
    val passwordHint: String? = null,
    val scheduledActivateDate: Long? = null,
    val scheduledDeactivateDate: Long? = null
)

@Entity(tableName = "scan_logs")
data class ScanLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val qrId: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val location: String, // City, Country
    val deviceOS: String,  // Android, iOS, Windows, macOS
    val ipAddress: String,
    val userAgent: String
)
