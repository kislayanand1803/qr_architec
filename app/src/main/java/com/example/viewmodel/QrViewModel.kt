package com.example.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

class QrViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = QrRepository(db.qrDao())

    // UI screen state
    var currentTab by mutableStateOf("library") // "library", "creator", "analytics", "advanced"
        private set

    fun setTab(tab: String) {
        currentTab = tab
    }

    // List of QR codes and scan logs
    val qrCodesState: StateFlow<List<QrCode>> = repository.allQrCodes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scanLogsState: StateFlow<List<ScanLog>> = repository.allScanLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search and organization filters
    var searchQuery by mutableStateOf("")
    var selectedTypeFilter by mutableStateOf("") // Empty means ALL
    var selectedTagFilter by mutableStateOf("") // Empty means ALL
    var selectedSortOrder by mutableStateOf("DATE_DESC") // "DATE_DESC", "DATE_ASC", "TITLE_ASC", "SCANS_DESC"

    // Selection management for bulk actions
    var selectedQrIds = mutableStateMapOf<Int, Boolean>()

    // Selected QR Code for Edit mode / Analytics details
    var selectedQrForDetail by mutableStateOf<QrCode?>(null)
        private set

    fun selectQrForDetail(qr: QrCode?) {
        selectedQrForDetail = qr
    }

    // Interactive Creator Workshop States
    var creatorTitle by mutableStateOf("Awesome Campaign QR")
    var creatorContent by mutableStateOf("https://google.com")
    var creatorType by mutableStateOf("URL") // URL, TEXT, WIFI, VCARD, EMAIL, SMS, GEO, SOCIAL
    var creatorTag by mutableStateOf("Campaign")
    
    // Wifi helper states
    var wifiSsid by mutableStateOf("HQ-Incite-Guest")
    var wifiPassword by mutableStateOf("AccessGranted77")
    var wifiEncryption by mutableStateOf("WPA") // WPA, WEP, nopass

    // vCard states
    var contactName by mutableStateOf("Jane Doe")
    var contactPhone by mutableStateOf("+1 555-019-922")
    var contactEmail by mutableStateOf("jane.doe@qrarc.co")
    var contactOrg by mutableStateOf("Inc. Corp")

    // QR Styling Live States
    var creatorFgColor by mutableStateOf(-16777216) // Default black
    var creatorBgColor by mutableStateOf(-1)        // Default white
    var creatorIsGradient by mutableStateOf(false)
    var creatorGradientColor2 by mutableStateOf(-12328193) // Emerald Radial gradient color
    var creatorGradientType by mutableStateOf("LINEAR") // LINEAR, RADIAL
    var creatorTransparentBg by mutableStateOf(false)
    var creatorModuleShape by mutableStateOf("SQUARE") // SQUARE, CIRCLE, ROUNDED, DIAMOND
    var creatorEyeStyle by mutableStateOf("CLASSIC") // CLASSIC, CIRCULAR, LEAF, SHARP
    var creatorLogoAsset by mutableStateOf<String?>(null) // null, "WIFI", "URL", "CONTACT", "EMAIL", "SMS", "GEO", "SOCIAL"
    var creatorLogoScale by mutableStateOf(0.18f)
    var creatorLogoPadding by mutableStateOf(4)
    var creatorFrameText by mutableStateOf("")
    var creatorFrameStyle by mutableStateOf("NONE") // NONE, MODERN_PIN, SIMPLE_BANNER, SOLID_TAG
    var creatorErrorLevel by mutableStateOf("M") // L, M, Q, H

    // Advanced dynamic redirection
    var creatorIsDynamic by mutableStateOf(false)
    var creatorRedirectUrl by mutableStateOf("")
    var creatorUtmSource by mutableStateOf("")
    var creatorUtmMedium by mutableStateOf("")
    var creatorUtmCampaign by mutableStateOf("")

    // Advanced features
    var creatorPassword by mutableStateOf("")
    var creatorPasswordHint by mutableStateOf("")
    var creatorScheduledActivate by mutableStateOf<Long?>(null)
    var creatorScheduledDeactivate by mutableStateOf<Long?>(null)

    // Editing mode reference
    var editingQrCodeId by mutableStateOf<Int?>(null)
        private set

    init {
        viewModelScope.launch {
            repository.seedDemoDataIfEmpty()
        }
    }

    // Load an existing QR code into the Creator Workshop
    fun loadForEditing(qr: QrCode) {
        editingQrCodeId = qr.id
        creatorTitle = qr.title
        creatorType = qr.type
        creatorTag = qr.tag ?: ""
        creatorFgColor = qr.foregroundColor
        creatorBgColor = qr.backgroundColor
        creatorIsGradient = qr.isGradient
        creatorGradientColor2 = qr.gradientColor2
        creatorGradientType = qr.gradientType
        creatorTransparentBg = qr.transparentBackground
        creatorModuleShape = qr.moduleShape
        creatorEyeStyle = qr.eyeStyle
        creatorLogoAsset = qr.logoAsset
        creatorLogoScale = qr.logoScale
        creatorLogoPadding = qr.logoPaddingDp
        creatorFrameText = qr.frameText ?: ""
        creatorFrameStyle = qr.frameStyle
        creatorErrorLevel = qr.errorCorrectionLevel
        creatorIsDynamic = qr.isDynamic
        creatorRedirectUrl = qr.dynamicRedirectUrl ?: ""
        creatorUtmSource = qr.utmSource ?: ""
        creatorUtmMedium = qr.utmMedium ?: ""
        creatorUtmCampaign = qr.utmCampaign ?: ""
        creatorPassword = qr.password ?: ""
        creatorPasswordHint = qr.passwordHint ?: ""
        creatorScheduledActivate = qr.scheduledActivateDate
        creatorScheduledDeactivate = qr.scheduledDeactivateDate

        // Extract credentials if types match
        if (qr.type == "WIFI") {
            try {
                // Parsing e.g. WIFI:S:HQ-Incite-Guest;T:WPA;P:AccessGranted77;;
                val content = qr.content
                wifiSsid = content.substringAfter("S:", "").substringBefore(";")
                wifiPassword = content.substringAfter("P:", "").substringBefore(";")
                wifiEncryption = content.substringAfter("T:", "").substringBefore(";")
            } catch (e: Exception) {
                creatorContent = qr.content
            }
        } else if (qr.type == "VCARD") {
            try {
                val content = qr.content
                contactName = content.substringAfter("FN:", "").substringBefore("\n")
                contactPhone = content.substringAfter("TEL;CELL:", "").substringBefore("\n").substringAfter("TEL:", "")
                contactEmail = content.substringAfter("EMAIL:", "").substringBefore("\n")
                contactOrg = content.substringAfter("ORG:", "").substringBefore("\n")
            } catch (e: Exception) {
                creatorContent = qr.content
            }
        } else {
            creatorContent = qr.content
        }

        setTab("creator")
    }

    fun resetCreatorFields() {
        editingQrCodeId = null
        creatorTitle = "New QR Architect Design"
        creatorContent = "https://yourdomain.com/landing"
        creatorType = "URL"
        creatorTag = "Personal"
        creatorFgColor = -16777216
        creatorBgColor = -1
        creatorIsGradient = false
        creatorGradientColor2 = -12328193
        creatorGradientType = "LINEAR"
        creatorTransparentBg = false
        creatorModuleShape = "SQUARE"
        creatorEyeStyle = "CLASSIC"
        creatorLogoAsset = null
        creatorLogoScale = 0.18f
        creatorLogoPadding = 4
        creatorFrameText = ""
        creatorFrameStyle = "NONE"
        creatorErrorLevel = "M"
        creatorIsDynamic = false
        creatorRedirectUrl = ""
        creatorUtmSource = ""
        creatorUtmMedium = ""
        creatorUtmCampaign = ""
        creatorPassword = ""
        creatorPasswordHint = ""
        creatorScheduledActivate = null
        creatorScheduledDeactivate = null
    }

    // Build final QR encoded string based on Type selection
    fun compileContent(): String {
        return when (creatorType) {
            "WIFI" -> {
                "WIFI:S:$wifiSsid;T:$wifiEncryption;P:$wifiPassword;H:false;;"
            }
            "VCARD" -> {
                "BEGIN:VCARD\nVERSION:3.0\nN:;${contactName};;;\nFN:${contactName}\nORG:${contactOrg}\nTEL;CELL:${contactPhone}\nEMAIL:${contactEmail}\nEND:VCARD"
            }
            "URL" -> {
                var url = creatorContent.trim()
                if (creatorIsDynamic && creatorRedirectUrl.isNotBlank()) {
                    url = creatorRedirectUrl.trim()
                }
                // Append UTMs if requested
                if (creatorUtmSource.isNotBlank() || creatorUtmMedium.isNotBlank() || creatorUtmCampaign.isNotBlank()) {
                    val separator = if (url.contains("?")) "&" else "?"
                    val params = mutableListOf<String>()
                    if (creatorUtmSource.isNotBlank()) params.add("utm_source=${creatorUtmSource.trim()}")
                    if (creatorUtmMedium.isNotBlank()) params.add("utm_medium=${creatorUtmMedium.trim()}")
                    if (creatorUtmCampaign.isNotBlank()) params.add("utm_campaign=${creatorUtmCampaign.trim()}")
                    url += separator + params.joinToString("&")
                }
                url
            }
            else -> creatorContent
        }
    }

    fun saveQrRepresentation() {
        val finalContent = compileContent()
        
        viewModelScope.launch {
            val qr = QrCode(
                id = editingQrCodeId ?: 0,
                title = creatorTitle.ifBlank { "QR Code ${System.currentTimeMillis() % 1000}" },
                content = finalContent,
                type = creatorType,
                creationDate = System.currentTimeMillis(),
                foregroundColor = creatorFgColor,
                backgroundColor = creatorBgColor,
                isGradient = creatorIsGradient,
                gradientColor2 = creatorGradientColor2,
                gradientType = creatorGradientType,
                transparentBackground = creatorTransparentBg,
                moduleShape = creatorModuleShape,
                eyeStyle = creatorEyeStyle,
                logoAsset = creatorLogoAsset,
                logoScale = creatorLogoScale,
                logoPaddingDp = creatorLogoPadding,
                frameText = creatorFrameText.ifBlank { null },
                frameStyle = creatorFrameStyle,
                errorCorrectionLevel = creatorErrorLevel,
                isDynamic = creatorIsDynamic,
                dynamicRedirectUrl = if (creatorIsDynamic) creatorRedirectUrl else null,
                utmSource = creatorUtmSource.ifBlank { null },
                utmMedium = creatorUtmMedium.ifBlank { null },
                utmCampaign = creatorUtmCampaign.ifBlank { null },
                password = creatorPassword.ifBlank { null },
                passwordHint = creatorPasswordHint.ifBlank { null },
                scheduledActivateDate = creatorScheduledActivate,
                scheduledDeactivateDate = creatorScheduledDeactivate,
                tag = creatorTag.ifBlank { null }
            )
            repository.insertQrCode(qr)
            resetCreatorFields()
            setTab("library")
        }
    }

    // Quick toggling of single status
    fun toggleActivation(qr: QrCode) {
        viewModelScope.launch {
            val updated = qr.copy(isActive = !qr.isActive)
            repository.updateQrCode(updated)
            if (selectedQrForDetail?.id == qr.id) {
                selectedQrForDetail = updated
            }
        }
    }

    // Dynamic URL redirection on-the-fly
    fun changeRedirectUrl(qr: QrCode, newUrl: String) {
        viewModelScope.launch {
            val oldRedirect = qr.dynamicRedirectUrl ?: qr.content
            val updatedHistory = qr.redirectHistoryJson + "\nChanged from $oldRedirect to $newUrl at ${System.currentTimeMillis()}"
            val updated = qr.copy(
                dynamicRedirectUrl = newUrl,
                redirectHistoryJson = updatedHistory
            )
            repository.updateQrCode(updated)
            if (selectedQrForDetail?.id == qr.id) {
                selectedQrForDetail = updated
            }
        }
    }

    // Create a exact clone copy of any QR
    fun duplicateQr(qr: QrCode) {
        viewModelScope.launch {
            val copy = qr.copy(
                id = 0,
                title = "Copy of ${qr.title}",
                creationDate = System.currentTimeMillis(),
                scanCount = 0
            )
            repository.insertQrCode(copy)
        }
    }

    fun deleteQr(qr: QrCode) {
        viewModelScope.launch {
            repository.deleteQrCode(qr)
            if (selectedQrForDetail?.id == qr.id) {
                selectedQrForDetail = null
            }
        }
    }

    // Bulk action implementations
    fun bulkToggleActive(activate: Boolean) {
        val targetIds = selectedQrIds.filter { it.value }.keys.toList()
        viewModelScope.launch {
            qrCodesState.value.forEach { qr ->
                if (qr.id in targetIds) {
                    repository.updateQrCode(qr.copy(isActive = activate))
                }
            }
            selectedQrIds.clear()
        }
    }

    fun bulkTag(tagName: String) {
        val targetIds = selectedQrIds.filter { it.value }.keys.toList()
        viewModelScope.launch {
            qrCodesState.value.forEach { qr ->
                if (qr.id in targetIds) {
                    repository.updateQrCode(qr.copy(tag = tagName))
                }
            }
            selectedQrIds.clear()
        }
    }

    fun bulkDelete() {
        val targetIds = selectedQrIds.filter { it.value }.keys.toList()
        viewModelScope.launch {
            targetIds.forEach { id ->
                repository.deleteQrCodeById(id)
            }
            selectedQrIds.clear()
            selectedQrForDetail = null
        }
    }

    // Live contrast safety check tester
    fun calculateContrastSafety(): Pair<String, Boolean> {
        if (creatorTransparentBg) return Pair("Acceptable (Uses frame background at scan)", true)
        
        // simple luminance estimate
        fun getLuminance(color: Int): Double {
            val r = android.graphics.Color.red(color) / 255.0
            val g = android.graphics.Color.green(color) / 255.0
            val b = android.graphics.Color.blue(color) / 255.0
            return 0.2126 * r + 0.7152 * g + 0.0722 * b
        }
        
        val fLum = getLuminance(creatorFgColor)
        val bLum = getLuminance(creatorBgColor)
        
        val diff = Math.abs(fLum - bLum)
        return if (diff < 0.25) {
            Pair("CRITICAL WARNING: Foreground and background have matching/low contrast! Scanners will struggle to read this code.", false)
        } else if (diff < 0.45) {
            Pair("CAUTION: Low contrast. May fail outdoors or with low-tier camera modules.", true)
        } else {
            Pair("OPTIMAL: High-contrast. Highly reliable scanning assured.", true)
        }
    }

    // Simulated QR Trigger logs for live demonstration
    fun registerSimulatedScan(qrId: Int) {
        viewModelScope.launch {
            repository.recordScan(qrId, isSimulated = false)
            qrCodesState.value.find { it.id == qrId }?.let { qr ->
                if (selectedQrForDetail?.id == qrId) {
                    selectedQrForDetail = qr.copy(scanCount = qr.scanCount + 1)
                }
            }
        }
    }

    // Webhooks POST API Simulation logger
    var webhookLogs = mutableStateListOf<String>()
        private set

    fun fireWebhookSimulation(qr: QrCode) {
        val endpoint = "https://api.zapier.com/hooks/catch/128381831/ae"
        val payload = """
        {
          "event": "qr_scanned",
          "qr_id": ${qr.id},
          "title": "${qr.title}",
          "destination": "${qr.dynamicRedirectUrl ?: qr.content}",
          "timestamp": ${System.currentTimeMillis()},
          "metadata": {
            "device": "Android Mobile Emulator",
            "ip": "203.0.113.195",
            "country": "United States",
            "is_dynamic": ${qr.isDynamic}
          }
        }
        """.trimIndent()
        
        webhookLogs.add(0, "SUCCESS [HTTP 200] Sent payload to $endpoint:\n$payload")
        if (webhookLogs.size > 15) webhookLogs.removeLast()
    }

    // Batch CSV generator tool
    var csvInputText by mutableStateOf(
        "Promo Badge,https://deal.co/badge1,Marketing,Sales\nWiFi Badge,WIFI:S:StaffNet;T:WEP;P:Open99;H:false;;,HQ Net,HR\nCustom Message,Congratulations! You found the hidden code!,Fun,Game"
    )
    
    var batchGenerateResultMsg by mutableStateOf("")

    fun processCsvBatchGeneration() {
        if (csvInputText.isBlank()) {
            batchGenerateResultMsg = "Please input valid comma-separated rows!"
            return
        }
        viewModelScope.launch {
            var count = 0
            val rows = csvInputText.split("\n")
            rows.forEach { row ->
                val cols = row.split(",")
                if (cols.isNotEmpty() && cols[0].isNotBlank()) {
                    val title = cols[0].trim()
                    val data = if (cols.size > 1) cols[1].trim() else "https://qrarc.co"
                    val tag = if (cols.size > 2) cols[2].trim() else "CSV Import"
                    val folder = if (cols.size > 3) cols[3].trim() else "Batch"
                    
                    val qr = QrCode(
                        title = title,
                        content = data,
                        type = if (data.startsWith("WIFI:")) "WIFI" else "URL",
                        tag = tag,
                        subFolder = folder,
                        foregroundColor = -16777216,
                        backgroundColor = -1
                    )
                    repository.insertQrCode(qr)
                    count++
                }
            }
            batchGenerateResultMsg = "Successfully generated and added $count custom QR Architect designs!"
            csvInputText = ""
        }
    }
}
