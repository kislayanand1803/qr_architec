package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlin.random.Random

class QrRepository(private val qrDao: QrDao) {

    val allQrCodes: Flow<List<QrCode>> = qrDao.getAllQrCodes()
    val allScanLogs: Flow<List<ScanLog>> = qrDao.getAllScanLogs()

    fun getQrCodeById(id: Int): Flow<QrCode?> = qrDao.getQrCodeById(id)
    
    suspend fun getQrCodeByIdSuspend(id: Int): QrCode? = qrDao.getQrCodeByIdSuspend(id)

    suspend fun insertQrCode(qrCode: QrCode): Long = qrDao.insertQrCode(qrCode)

    suspend fun updateQrCode(qrCode: QrCode) = qrDao.updateQrCode(qrCode)

    suspend fun deleteQrCode(qrCode: QrCode) = qrDao.deleteQrCode(qrCode)

    suspend fun deleteQrCodeById(id: Int) = qrDao.deleteQrCodeById(id)

    fun getScanLogsForQr(qrId: Int): Flow<List<ScanLog>> = qrDao.getScanLogsForQr(qrId)

    suspend fun recordScan(qrId: Int, isSimulated: Boolean = true) {
        qrDao.incrementScanCount(qrId)
        
        // Setup random details for rich simulation
        val cities = listOf("San Francisco, USA", "New York, USA", "London, UK", "Tokyo, Japan", "Berlin, Germany", "Sydney, Australia", "Paris, France")
        val devices = listOf("Android", "iOS", "Windows", "macOS")
        val ip = "192.168.1.${Random.nextInt(1, 255)}"
        val userAgent = if (Random.nextBoolean()) "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)" else "Mozilla/5.0 (Linux; Android 14; Pixel 8)"
        
        val log = ScanLog(
            qrId = qrId,
            timestamp = System.currentTimeMillis() - if (isSimulated) Random.nextLong(0, 30L * 24 * 3600 * 1000) else 0L, // spread logs over last 30 days
            location = cities.random(),
            deviceOS = devices.random(),
            ipAddress = ip,
            userAgent = userAgent
        )
        qrDao.insertScanLog(log)
    }

    suspend fun seedDemoDataIfEmpty() {
        val count = allQrCodes.first().size
        if (count > 0) return // Already seeded
        
        // Seed standard items
        val webQrId = qrDao.insertQrCode(QrCode(
            title = "Corporate Website",
            content = "https://qrarc.co/company-portal",
            type = "URL",
            tag = "Brand Kit",
            subFolder = "Marketing",
            foregroundColor = -16358485, // #065F46 (Emerald Dark)
            backgroundColor = -1,        // White
            moduleShape = "ROUNDED",
            eyeStyle = "LEAF",
            isDynamic = true,
            dynamicRedirectUrl = "https://aistudio.google.com",
            utmSource = "qr_architect",
            utmMedium = "print_sticker",
            utmCampaign = "relaunch_2026"
        )).toInt()

        val wifiQrId = qrDao.insertQrCode(QrCode(
            title = "HQ Guest Wi-Fi",
            content = "WIFI:S:HQ-Incite-Guest;T:WPA;P:AccessGranted77;H:false;;",
            type = "WIFI",
            tag = "Office HQ",
            foregroundColor = -15102551, // #1E1B4B (Deep Purple-Navy)
            backgroundColor = -1,
            moduleShape = "CIRCLE",
            eyeStyle = "CIRCULAR"
        )).toInt()

        val contactQrId = qrDao.insertQrCode(QrCode(
            title = "Marketing Manager Card",
            content = "BEGIN:VCARD\nVERSION:3.0\nN:Doe;Jane;;;\nFN:Jane Doe\nORG:QR Architect\nTITLE:Marketing Director\nTEL;CELL:+1555019922\nEMAIL:jane.doe@qrarc.co\nURL:https://qrarc.co/jane-profile\nEND:VCARD",
            type = "VCARD",
            tag = "HR Essentials",
            foregroundColor = -16777216, // Black
            backgroundColor = -1,
            moduleShape = "SQUARE",
            eyeStyle = "CLASSIC"
        )).toInt()

        val promoQrId = qrDao.insertQrCode(QrCode(
            title = "Summer Sale Promo",
            content = "https://qrarc.co/deal-active",
            type = "URL",
            tag = "Q1 Campaign",
            subFolder = "Sales",
            foregroundColor = -14513364, // #22C55E Modern Mint
            backgroundColor = -16777216, // Black background! High contrast visual inversion
            isGradient = true,
            gradientColor2 = -12328193, // Radial emerald mix
            gradientType = "RADIAL",
            moduleShape = "DIAMOND",
            eyeStyle = "SHARP",
            frameText = "SCAN TO UNLOCK VIP CODE",
            frameStyle = "MODERN_PIN",
            isDynamic = true,
            dynamicRedirectUrl = "https://google.com/search?q=summer+deals",
            scheduledActivateDate = System.currentTimeMillis() - 7 * 24 * 3600 * 1000,
            scheduledDeactivateDate = System.currentTimeMillis() + 14 * 24 * 3600 * 1000
        )).toInt()

        // Seed some history of scans for these demo codes
        // Let's seed 10 to 25 random logs spread out across 30 days
        for (i in 1..28) {
            recordScan(webQrId, isSimulated = true)
        }
        for (i in 1..8) {
            recordScan(wifiQrId, isSimulated = true)
        }
        for (i in 1..15) {
            recordScan(contactQrId, isSimulated = true)
        }
        for (i in 1..42) {
            recordScan(promoQrId, isSimulated = true)
        }
    }
}
