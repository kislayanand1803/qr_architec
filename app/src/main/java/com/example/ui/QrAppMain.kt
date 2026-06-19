package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.QrCode
import com.example.data.QrGenerator
import com.example.data.ScanLog
import com.example.viewmodel.QrViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Environment
import android.os.Build
import android.net.Uri
import java.io.OutputStream
import java.io.File
import java.io.FileOutputStream

// Bento Grid Design Theme Colors (Elegant Dark Theme)
val SlateDark = Color(0xFF0F0D15)     // #0F0D15 - Deep dark space background
val SlateCard = Color(0xFF1D1A22)     // #1D1A22 - Soft elevated card background
val SlateBorder = Color(0xFF2E2B35)   // #2E2B35 - Dark contrast border
val EmeraldPrime = Color(0xFFD0BCFF)  // #D0BCFF - Vibrant M3 lavender highlight
val IndigoAccent = Color(0xFFF3EDF7)  // #F3EDF7 - Radiant purple-white for prominent text

fun saveBitmapToGallery(context: Context, bitmap: Bitmap, title: String): Uri? {
    val resolver = context.contentResolver
    val cleanTitle = title.trim().replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "QR_Code" }
    val filename = "${cleanTitle}_${System.currentTimeMillis()}.png"
    
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/QR_Generator")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    
    var imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    
    if (imageUri == null) {
        try {
            val directory = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
            val fallbackFile = File(directory, filename)
            FileOutputStream(fallbackFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            return Uri.fromFile(fallbackFile)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    try {
        resolver.openOutputStream(imageUri).use { outputStream ->
            if (outputStream != null) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            } else {
                throw java.io.IOException("Failed to get output stream.")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(imageUri, contentValues, null, null)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        try {
            resolver.delete(imageUri, null, null)
        } catch (delEx: Exception) {
            // ignore
        }
        
        try {
            val directory = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
            val fallbackFile = File(directory, filename)
            FileOutputStream(fallbackFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            return Uri.fromFile(fallbackFile)
        } catch (e2: Exception) {
            e2.printStackTrace()
            return null
        }
    }
    return imageUri
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QrAppMain(viewModel: QrViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val qrList by viewModel.qrCodesState.collectAsState()
    val scanLogs by viewModel.scanLogsState.collectAsState()

    // Walkthrough Onboarding State
    var showOnboarding by remember { mutableStateOf(true) }
    var onboardingStep by remember { mutableIntStateOf(1) }

    // Floating Interactive Detail Sidebar
    var activeDetailQr by remember { mutableStateOf<QrCode?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = SlateDark,
        bottomBar = {
            QrBottomNavigation(
                activeTab = viewModel.currentTab,
                onTabSelected = { 
                    viewModel.setTab(it)
                    activeDetailQr = null
                }
            )
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Main Content Stream
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp)
            ) {
                // Main Branded Navbar
                BrandedHeader(
                    onResetDemo = {
                        Toast.makeText(context, "Demo presets restored in background database", Toast.LENGTH_SHORT).show()
                    },
                    showWalkthroughHelp = {
                        onboardingStep = 1
                        showOnboarding = true
                    }
                )

                // Walkthrough HUD overlay
                if (showOnboarding) {
                    OnboardingWalkthroughHUD(
                        step = onboardingStep,
                        onNext = { if (onboardingStep < 4) onboardingStep++ else showOnboarding = false },
                        onSkip = { showOnboarding = false }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Render respective tab workspace
                when (viewModel.currentTab) {
                    "library" -> LibraryWorkspace(
                        qrList = qrList,
                        viewModel = viewModel,
                        onQrSelected = { activeDetailQr = it }
                    )
                    "creator" -> CreatorWorkshopWorkspace(
                        viewModel = viewModel,
                        onSaveClicked = {
                            viewModel.saveQrRepresentation()
                            Toast.makeText(context, "Successfully saved custom QR Architect!", Toast.LENGTH_SHORT).show()
                        }
                    )
                    "analytics" -> CampaignAnalyticsWorkspace(
                        qrList = qrList,
                        scanLogs = scanLogs,
                        viewModel = viewModel
                    )
                    "advanced" -> AdvancedSettingsWorkspace(
                        viewModel = viewModel
                    )
                }
            }

            // Quick Actions Detail Sidebar Drawer (Displays on right side if chosen)
            AnimatedVisibility(
                visible = activeDetailQr != null,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            ) {
                activeDetailQr?.let { qr ->
                    // Re-calculate referencing local list to keep live metrics accurate
                    val latestQr = qrList.find { it.id == qr.id } ?: qr
                    DetailActionsSidebar(
                        qr = latestQr,
                        allLogs = scanLogs.filter { it.qrId == qr.id },
                        onDismiss = { activeDetailQr = null },
                        viewModel = viewModel,
                        onTriggerScanSimulation = {
                            viewModel.registerSimulatedScan(qr.id)
                            viewModel.fireWebhookSimulation(qr)
                            Toast.makeText(context, "Scan simulation completed! Webhook fired [HTTP 200]!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun BrandedHeader(onResetDemo: () -> Unit, showWalkthroughHelp: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(EmeraldPrime, IndigoAccent))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "QR Architect Logo",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "QR Architect",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        color = IndigoAccent,
                        letterSpacing = 0.5.sp
                    )
                )
                Text(
                    text = "Manage your dynamic codes",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color(0xFFCAC4D0),
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.2.sp
                    )
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = showWalkthroughHelp,
                modifier = Modifier
                    .size(36.dp)
                    .background(SlateCard, RoundedCornerShape(8.dp))
                    .testTag("help_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.HelpOutline,
                    contentDescription = "Help Guide walkthrough",
                    tint = EmeraldPrime,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun OnboardingWalkthroughHUD(step: Int, onNext: () -> Unit, onSkip: () -> Unit) {
    val text = when (step) {
        1 -> "Welcome to QR Architect! Search, catalog, and bulk manage your codes from this central dashboard dashboard instantly."
        2 -> "Head to 'Creator Workshop' to craft QR codes, swap modular pixel templates, adjust center brand icons, and add frame tags."
        3 -> "Use 'Dynamic Routing' on URL-types to change redirection anytime without ever reprinting your labels!"
        else -> "Open the Webhooks simulated triggers to sync scan events to automated workflows like Zapier or Make!"
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_hud"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Assistant guide",
                        tint = EmeraldPrime,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Interactive Onboarding Guide (Step $step of 4)",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = IndigoAccent,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                TextButton(onClick = onSkip) {
                    Text("Skip", color = Color(0xFFCAC4D0), fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFFCAC4D0),
                    lineHeight = 20.sp
                )
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onNext,
                modifier = Modifier
                    .align(Alignment.End)
                    .height(34.dp)
                    .testTag("onboarding_next_button"),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrime)
            ) {
                Text(
                    text = if (step == 4) "Get Started" else "Next Step",
                    color = SlateDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// LIBRARY WORKSPACE (Dashboard)
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryWorkspace(
    qrList: List<QrCode>,
    viewModel: QrViewModel,
    onQrSelected: (QrCode) -> Unit
) {
    // Collect specific tags to populate tags filter list
    val availableTags = qrList.mapNotNull { it.tag }.distinct()

    LaunchedEffect(viewModel.searchQuery, viewModel.selectedTypeFilter, viewModel.selectedTagFilter, viewModel.selectedSortOrder) {
        viewModel.triggerLibraryRefresh()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search & Filters Panel
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            border = BorderStroke(1.dp, SlateBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Search Input Field
                OutlinedTextField(
                    value = viewModel.searchQuery,
                    onValueChange = { viewModel.searchQuery = it },
                    placeholder = { Text("Search QR codes by title or destination...", color = Color(0xFF938F99)) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon", tint = Color(0xFF938F99)) },
                    trailingIcon = {
                        if (viewModel.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = Color(0xFF938F99))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("search_field"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = IndigoAccent,
                        unfocusedTextColor = IndigoAccent,
                        focusedBorderColor = EmeraldPrime,
                        unfocusedBorderColor = SlateBorder,
                        focusedContainerColor = SlateDark,
                        unfocusedContainerColor = SlateDark
                    ),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Content Filter Category Chips Row
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filterTypes = listOf("ALL", "URL", "WIFI", "VCARD", "TEXT")
                    filterTypes.forEach { type ->
                        val isSelected = (type == "ALL" && viewModel.selectedTypeFilter.isEmpty()) || (type == viewModel.selectedTypeFilter)
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectedTypeFilter = if (type == "ALL") "" else type },
                            label = { Text(type, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldPrime,
                                selectedLabelColor = SlateDark,
                                containerColor = SlateDark,
                                labelColor = Color(0xFFCAC4D0)
                            ),
                            border = FilterChipDefaults.filterChipBorder(enabled = true, selected = isSelected, borderColor = SlateBorder, selectedBorderColor = EmeraldPrime)
                        )
                    }
                }

                if (availableTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Tag filter chips
                        val isAllTagsSelected = viewModel.selectedTagFilter.isEmpty()
                        FilterChip(
                            selected = isAllTagsSelected,
                            onClick = { viewModel.selectedTagFilter = "" },
                            label = { Text("ALL TAGS", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = IndigoAccent,
                                selectedLabelColor = SlateDark,
                                containerColor = SlateDark,
                                labelColor = Color(0xFFCAC4D0)
                            ),
                            border = FilterChipDefaults.filterChipBorder(enabled = true, selected = isAllTagsSelected, borderColor = SlateBorder, selectedBorderColor = IndigoAccent)
                        )
                        availableTags.forEach { tag ->
                            val isSelected = viewModel.selectedTagFilter == tag
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectedTagFilter = tag },
                                label = { Text(tag, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = IndigoAccent,
                                    selectedLabelColor = SlateDark,
                                    containerColor = SlateDark,
                                    labelColor = Color(0xFFCAC4D0)
                                ),
                                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = isSelected, borderColor = SlateBorder, selectedBorderColor = IndigoAccent)
                            )
                        }
                    }
                }
            }
        }

        // Bulk Actions Bar (displayed only when items are checked)
        val selectedCount = viewModel.selectedQrIds.filter { it.value }.size
        AnimatedVisibility(
            visible = selectedCount > 0,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, EmeraldPrime)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$selectedCount selected items",
                        style = MaterialTheme.typography.bodyMedium.copy(color = IndigoAccent, fontWeight = FontWeight.Bold)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = { viewModel.bulkToggleActive(true) },
                            modifier = Modifier
                                .size(36.dp)
                                .background(SlateDark, RoundedCornerShape(6.dp))
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Active selection", tint = EmeraldPrime, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { viewModel.bulkToggleActive(false) },
                            modifier = Modifier
                                .size(36.dp)
                                .background(SlateDark, RoundedCornerShape(6.dp))
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause selection", tint = Color.Yellow, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { viewModel.bulkDelete() },
                            modifier = Modifier
                                .size(36.dp)
                                .background(SlateDark, RoundedCornerShape(6.dp)),
                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.Red)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete selection", tint = Color.Red, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Filter & Sort list computation
        val filteredList = remember(qrList, viewModel.searchQuery, viewModel.selectedTypeFilter, viewModel.selectedTagFilter, viewModel.selectedSortOrder) {
            qrList.filter { qr ->
                val matchQuery = qr.title.contains(viewModel.searchQuery, ignoreCase = true) || qr.content.contains(viewModel.searchQuery, ignoreCase = true)
                val matchType = viewModel.selectedTypeFilter.isEmpty() || qr.type.equals(viewModel.selectedTypeFilter, ignoreCase = true)
                val matchTag = viewModel.selectedTagFilter.isEmpty() || qr.tag == viewModel.selectedTagFilter
                matchQuery && matchType && matchTag
            }.sortedWith { q1, q2 ->
                when (viewModel.selectedSortOrder) {
                    "DATE_ASC" -> q1.creationDate.compareTo(q2.creationDate)
                    "TITLE_ASC" -> q1.title.compareTo(q2.title, ignoreCase = true)
                    "SCANS_DESC" -> q2.scanCount.compareTo(q1.scanCount)
                    else -> q2.creationDate.compareTo(q1.creationDate) // DATE_DESC
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${filteredList.size} codes located in system",
                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCAC4D0))
            )

            // Sorting order pill
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                viewModel.selectedSortOrder = when (viewModel.selectedSortOrder) {
                    "DATE_DESC" -> "DATE_ASC"
                    "DATE_ASC" -> "TITLE_ASC"
                    "TITLE_ASC" -> "SCANS_DESC"
                    else -> "DATE_DESC"
                }
            }) {
                Icon(Icons.Default.Sort, contentDescription = "Sort Icon", tint = EmeraldPrime, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = when (viewModel.selectedSortOrder) {
                        "DATE_ASC" -> "Date (OldestFirst)"
                        "TITLE_ASC" -> "Alphabetical A-Z"
                        "SCANS_DESC" -> "Scanner Count"
                        else -> "Date (NewestFirst)"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(color = IndigoAccent, fontWeight = FontWeight.Bold)
                )
            }
        }

        val slateShimmerBrush = shimmerBrush()

        // Empty/Loading Library check
        if (viewModel.isLibraryLoading) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(6) {
                    QrCodeSkeletonCard(shimmerBrush = slateShimmerBrush)
                }
            }
        } else if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "Empty Library indicator",
                        tint = SlateBorder,
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No codes found matching current search terms.",
                        style = MaterialTheme.typography.bodyLarge.copy(color = IndigoAccent, fontWeight = FontWeight.SemiBold),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Clear search fields or navigate to 'Creator' to build a custom QR code instantly.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCAC4D0)),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // LazyVerticalGrid for beautiful SaaS items cataloging!
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredList, key = { it.id }) { qr ->
                    QrCodeCatalogCard(
                        qr = qr,
                        isSelected = viewModel.selectedQrIds[qr.id] ?: false,
                        onSelectedChange = { checked ->
                            viewModel.selectedQrIds[qr.id] = checked
                        },
                        onTap = { onQrSelected(qr) },
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
fun QrCodeCatalogCard(
    qr: QrCode,
    isSelected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onTap: () -> Unit,
    viewModel: QrViewModel
) {
    // Cache the bitmap generation so scrolling stays ultra smooth
    val qrImageBitmap = remember(qr) {
        QrGenerator.generateQrBitmap(qr, 180).asImageBitmap()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() }
            .testTag("qr_card_${qr.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (qr.isActive) SlateCard else SlateCard.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            1.dp,
            if (isSelected) EmeraldPrime else SlateBorder
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Checkbox and Badge layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = onSelectedChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = EmeraldPrime,
                        uncheckedColor = Color.Gray
                    ),
                    modifier = Modifier.size(24.dp)
                )

                // Render micro status badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (qr.isActive) EmeraldPrime.copy(alpha = 0.15f) else Color(0xFFCAC4D0))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (qr.isActive) "ACTIVE" else "PAUSED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (qr.isActive) EmeraldPrime else Color(0xFF938F99),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Centered High-Fidelity QR image
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(6.dp)
            ) {
                Image(
                    bitmap = qrImageBitmap,
                    contentDescription = "QR Code thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metadata info
            Text(
                text = qr.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = IndigoAccent,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type label text with micro indicator
                Text(
                    text = qr.type,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFFCAC4D0),
                        fontSize = 11.sp
                    )
                )

                // Metric total count
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = "Scan count icon",
                        tint = if (qr.scanCount > 10) EmeraldPrime else Color.Gray,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${qr.scanCount} scans",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (qr.scanCount > 10) IndigoAccent else Color(0xFFCAC4D0),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            if (!qr.tag.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                // Tag small indicator pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(IndigoAccent.copy(alpha = 0.25f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = qr.tag.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = IndigoAccent,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun shimmerBrush(
    showShimmer: Boolean = true,
    targetValue: Float = 1000f
): Brush {
    return if (showShimmer) {
        val transition = rememberInfiniteTransition(label = "shimmer")
        val translateAnimation by transition.animateFloat(
            initialValue = 0f,
            targetValue = targetValue,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmer_animation"
        )
        val shimmerColors = listOf(
            Color(0xFF161421),
            Color(0xFF2E2B3D),
            Color(0xFF161421)
        )
        Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(translateAnimation - 350f, translateAnimation - 350f),
            end = Offset(translateAnimation, translateAnimation)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.Transparent)
        )
    }
}

@Composable
fun QrCodeSkeletonCard(shimmerBrush: Brush) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("qr_skeleton_card"),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerBrush)
                )
                Box(
                    modifier = Modifier
                        .size(width = 54.dp, height = 18.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(shimmerBrush)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(12.dp))
                    .background(shimmerBrush)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(45.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
                Box(
                    modifier = Modifier
                        .width(50.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush)
            )
        }
    }
}

// CREATOR WORKSPACE (QR Code customization portal)
@Composable
fun CreatorWorkshopWorkspace(
    viewModel: QrViewModel,
    onSaveClicked: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isSimulatingLaser by remember { mutableStateOf(false) }
    var scanSimulationMsg by remember { mutableStateOf<String?>(null) }
    var laserProgress by remember { mutableStateOf(0f) }

    // Simulates an asynchronous compilation shimmer effect on live content refresh
    var isPreviewLoading by remember { mutableStateOf(false) }
    val slateShimmerBrush = shimmerBrush()

    LaunchedEffect(
        viewModel.creatorTitle, viewModel.creatorContent, viewModel.creatorType,
        viewModel.creatorFgColor, viewModel.creatorBgColor, viewModel.creatorIsGradient,
        viewModel.creatorGradientColor2, viewModel.creatorGradientType, viewModel.creatorTransparentBg,
        viewModel.creatorModuleShape, viewModel.creatorEyeStyle, viewModel.creatorLogoAsset,
        viewModel.creatorLogoScale, viewModel.creatorLogoPadding, viewModel.creatorFrameText,
        viewModel.creatorFrameStyle, viewModel.creatorErrorLevel, viewModel.creatorIsDynamic,
        viewModel.creatorRedirectUrl, viewModel.creatorUtmSource, viewModel.creatorUtmMedium,
        viewModel.creatorUtmCampaign, viewModel.wifiSsid, viewModel.wifiPassword,
        viewModel.wifiEncryption, viewModel.contactName, viewModel.contactPhone,
        viewModel.contactEmail, viewModel.contactOrg
    ) {
        isPreviewLoading = true
        delay(400) // perceived performance delay simulation
        isPreviewLoading = false
    }

    // Recompiles the live WYSIWYG generator preview output reactively!
    val previewAndroidBitmap = remember(
        viewModel.creatorTitle, viewModel.creatorContent, viewModel.creatorType,
        viewModel.creatorFgColor, viewModel.creatorBgColor, viewModel.creatorIsGradient,
        viewModel.creatorGradientColor2, viewModel.creatorGradientType, viewModel.creatorTransparentBg,
        viewModel.creatorModuleShape, viewModel.creatorEyeStyle, viewModel.creatorLogoAsset,
        viewModel.creatorLogoScale, viewModel.creatorLogoPadding, viewModel.creatorFrameText,
        viewModel.creatorFrameStyle, viewModel.creatorErrorLevel, viewModel.creatorIsDynamic,
        viewModel.creatorRedirectUrl, viewModel.creatorUtmSource, viewModel.creatorUtmMedium,
        viewModel.creatorUtmCampaign, viewModel.wifiSsid, viewModel.wifiPassword,
        viewModel.wifiEncryption, viewModel.contactName, viewModel.contactPhone,
        viewModel.contactEmail, viewModel.contactOrg
    ) {
        val dummyCode = QrCode(
            title = viewModel.creatorTitle,
            content = viewModel.compileContent(),
            type = viewModel.creatorType,
            foregroundColor = viewModel.creatorFgColor,
            backgroundColor = viewModel.creatorBgColor,
            isGradient = viewModel.creatorIsGradient,
            gradientColor2 = viewModel.creatorGradientColor2,
            gradientType = viewModel.creatorGradientType,
            transparentBackground = viewModel.creatorTransparentBg,
            moduleShape = viewModel.creatorModuleShape,
            eyeStyle = viewModel.creatorEyeStyle,
            logoAsset = viewModel.creatorLogoAsset,
            logoScale = viewModel.creatorLogoScale,
            logoPaddingDp = viewModel.creatorLogoPadding,
            frameText = viewModel.creatorFrameText.ifBlank { null },
            frameStyle = viewModel.creatorFrameStyle,
            errorCorrectionLevel = viewModel.creatorErrorLevel,
            isDynamic = viewModel.creatorIsDynamic,
            dynamicRedirectUrl = if (viewModel.creatorIsDynamic) viewModel.creatorRedirectUrl else null,
            utmSource = viewModel.creatorUtmSource.ifBlank { null },
            utmMedium = viewModel.creatorUtmMedium.ifBlank { null },
            utmCampaign = viewModel.creatorUtmCampaign.ifBlank { null }
        )
        QrGenerator.generateQrBitmap(dummyCode, 480)
    }

    val previewBitmap = remember(previewAndroidBitmap) {
        previewAndroidBitmap.asImageBitmap()
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
        // ---------------- STICKY PREVIEW & ACTION PANEL ----------------
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: The WYSIWYG QR Preview Panel
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "LIVE PREVIEW",
                            style = MaterialTheme.typography.labelSmall.copy(color = EmeraldPrime, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = viewModel.creatorTitle.ifBlank { "Untitled QR" },
                            style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // QR DISPLAY BOX
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .let { baseMod ->
                                    if (isPreviewLoading) {
                                        baseMod.background(slateShimmerBrush)
                                    } else if (viewModel.creatorTransparentBg) {
                                        baseMod.background(Brush.linearGradient(listOf(Color.White, Color.LightGray)))
                                    } else {
                                        baseMod.background(Color(viewModel.creatorBgColor))
                                    }
                                }
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPreviewLoading) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(slateShimmerBrush)
                                )
                            } else {
                                Image(
                                    bitmap = previewBitmap,
                                    contentDescription = "Live generated preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )

                                // Real-time Glowing Laser Sweep Animation simulation
                                if (isSimulatingLaser) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(laserProgress)
                                            .drawBehind {
                                                drawLine(
                                                    color = EmeraldPrime,
                                                    start = Offset(0f, size.height),
                                                    end = Offset(size.width, size.height),
                                                    strokeWidth = 3.dp.toPx()
                                                )
                                            }
                                    )
                                }
                            }
                        }
                    }

                    // Right: Primary Action Buttons Column
                    Column(
                        modifier = Modifier.weight(1.1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // SAVE DESIGN TO LIBRARY
                        Button(
                            onClick = onSaveClicked,
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent, contentColor = SlateDark),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("save_design_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = "Commit to local database", tint = SlateDark, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SAVE DESIGN", color = SlateDark, fontWeight = FontWeight.Black, fontSize = 10.sp)
                        }

                        // DOWNLOAD PNG FILE
                        Button(
                            onClick = {
                                val savedUri = saveBitmapToGallery(context, previewAndroidBitmap, viewModel.creatorTitle)
                                if (savedUri != null) {
                                    if (savedUri.scheme == "file") {
                                        Toast.makeText(context, "PNG saved: ${savedUri.path}", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "PNG saved to Pictures/QR_Generator gallery!", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Failed to save QR code PNG.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldPrime,
                                contentColor = SlateDark
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("download_png_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download custom QR PNG icon",
                                tint = SlateDark,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "DOWNLOAD PNG",
                                fontWeight = FontWeight.Bold,
                                color = SlateDark,
                                fontSize = 10.sp
                            )
                        }

                        // TEST SCAN RELIABILITY
                        Button(
                            onClick = {
                                if (!isSimulatingLaser) {
                                    isSimulatingLaser = true
                                    laserProgress = 0f
                                    scanSimulationMsg = null
                                    scope.launch {
                                        animate(0f, 1f, animationSpec = tween(1200, easing = LinearEasing)) { v, _ ->
                                            laserProgress = v
                                        }
                                        delay(200)
                                        isSimulatingLaser = false
                                        val (msg, ok) = viewModel.calculateContrastSafety()
                                        scanSimulationMsg = msg
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSimulatingLaser) Color.DarkGray else EmeraldPrime
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("test_scan_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterCenterFocus,
                                contentDescription = "Test scan icon",
                                tint = SlateDark,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSimulatingLaser) "SCANNING..." else "TEST SCAN",
                                fontWeight = FontWeight.Bold,
                                color = SlateDark,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Diagnostics panel below Row, but inside the Sticky Card block
                scanSimulationMsg?.let { msg ->
                    Spacer(modifier = Modifier.height(10.dp))
                    val isWarning = msg.contains("WARNING") || msg.contains("CAUTION")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isWarning) Color(0x3DFBBF24) else Color(0x3D10B981))
                            .border(1.dp, if (isWarning) Color.Yellow else EmeraldPrime, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isWarning) Color.Yellow else Color.White,
                                lineHeight = 15.sp
                            )
                        )
                    }
                }
            }
        }

        // ---------------- SCROLLABLE CONFIGURATION CONTROLS ----------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // General Info Header
            Text(
                text = if (viewModel.editingQrCodeId != null) "Editing Design Plan" else "Visual Configuration Suite",
                style = MaterialTheme.typography.titleMedium.copy(color = IndigoAccent, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Title Label", color = Color(0xFFCAC4D0), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = viewModel.creatorTitle,
                        onValueChange = { viewModel.creatorTitle = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("input_title"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = IndigoAccent,
                            unfocusedTextColor = IndigoAccent,
                            focusedBorderColor = EmeraldPrime,
                            unfocusedBorderColor = SlateBorder,
                            focusedContainerColor = SlateDark,
                            unfocusedContainerColor = SlateDark
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Content Data Class", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    // Custom Content tabs
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("URL", "WIFI", "VCARD", "TEXT").forEach { tab ->
                            val isChosen = viewModel.creatorType == tab
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isChosen) EmeraldPrime else SlateDark)
                                    .border(1.dp, if (isChosen) EmeraldPrime else SlateBorder, RoundedCornerShape(6.dp))
                                    .clickable { viewModel.creatorType = tab }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tab,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isChosen) SlateDark else Color.LightGray
                                )
                            }
                        }
                    }

                    // Fields mapping depending on type clicked
                    when (viewModel.creatorType) {
                        "WIFI" -> {
                            OutlinedTextField(
                                value = viewModel.wifiSsid,
                                onValueChange = { viewModel.wifiSsid = it },
                                label = { Text("Network SSID") },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = IndigoAccent,
                                    unfocusedTextColor = IndigoAccent,
                                    focusedBorderColor = EmeraldPrime,
                                    unfocusedBorderColor = SlateBorder,
                                    focusedContainerColor = SlateDark,
                                    unfocusedContainerColor = SlateDark,
                                    focusedLabelColor = EmeraldPrime,
                                    unfocusedLabelColor = Color(0xFFCAC4D0)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = viewModel.wifiPassword,
                                onValueChange = { viewModel.wifiPassword = it },
                                label = { Text("Wireless Password") },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = IndigoAccent,
                                    unfocusedTextColor = IndigoAccent,
                                    focusedBorderColor = EmeraldPrime,
                                    unfocusedBorderColor = SlateBorder,
                                    focusedContainerColor = SlateDark,
                                    unfocusedContainerColor = SlateDark,
                                    focusedLabelColor = EmeraldPrime,
                                    unfocusedLabelColor = Color(0xFFCAC4D0)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf("WPA", "WEP", "nopass").forEach { op ->
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { viewModel.wifiEncryption = op }) {
                                        RadioButton(
                                            selected = viewModel.wifiEncryption == op,
                                            onClick = { viewModel.wifiEncryption = op },
                                            colors = RadioButtonDefaults.colors(selectedColor = EmeraldPrime)
                                        )
                                        Text(op, color = IndigoAccent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                        "VCARD" -> {
                            OutlinedTextField(
                                value = viewModel.contactName,
                                onValueChange = { viewModel.contactName = it },
                                label = { Text("Full Name") },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = IndigoAccent,
                                    unfocusedTextColor = IndigoAccent,
                                    focusedBorderColor = EmeraldPrime,
                                    unfocusedBorderColor = SlateBorder,
                                    focusedContainerColor = SlateDark,
                                    unfocusedContainerColor = SlateDark,
                                    focusedLabelColor = EmeraldPrime,
                                    unfocusedLabelColor = Color(0xFFCAC4D0)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = viewModel.contactPhone,
                                onValueChange = { viewModel.contactPhone = it },
                                label = { Text("Phone Number") },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = IndigoAccent,
                                    unfocusedTextColor = IndigoAccent,
                                    focusedBorderColor = EmeraldPrime,
                                    unfocusedBorderColor = SlateBorder,
                                    focusedContainerColor = SlateDark,
                                    unfocusedContainerColor = SlateDark,
                                    focusedLabelColor = EmeraldPrime,
                                    unfocusedLabelColor = Color(0xFFCAC4D0)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = viewModel.contactEmail,
                                onValueChange = { viewModel.contactEmail = it },
                                label = { Text("Email Address") },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = IndigoAccent,
                                    unfocusedTextColor = IndigoAccent,
                                    focusedBorderColor = EmeraldPrime,
                                    unfocusedBorderColor = SlateBorder,
                                    focusedContainerColor = SlateDark,
                                    unfocusedContainerColor = SlateDark,
                                    focusedLabelColor = EmeraldPrime,
                                    unfocusedLabelColor = Color(0xFFCAC4D0)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                        else -> { // URL & TEXT
                            OutlinedTextField(
                                value = viewModel.creatorContent,
                                onValueChange = { viewModel.creatorContent = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_content"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = IndigoAccent,
                                    unfocusedTextColor = IndigoAccent,
                                    focusedBorderColor = EmeraldPrime,
                                    unfocusedBorderColor = SlateBorder,
                                    focusedContainerColor = SlateDark,
                                    unfocusedContainerColor = SlateDark,
                                    focusedLabelColor = EmeraldPrime,
                                    unfocusedLabelColor = Color(0xFFCAC4D0),
                                    focusedPlaceholderColor = Color(0xFF938F99),
                                    unfocusedPlaceholderColor = Color(0xFF938F99)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            
                            // Dynamic Route section in-line
                            if (viewModel.creatorType == "URL") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Shuffle, contentDescription = "Dynamic Routing link icon", tint = EmeraldPrime, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Dynamic URL Redirection Module", color = IndigoAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Switch(
                                        checked = viewModel.creatorIsDynamic,
                                        onCheckedChange = { viewModel.creatorIsDynamic = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = EmeraldPrime)
                                    )
                                }
                                
                                if (viewModel.creatorIsDynamic) {
                                    OutlinedTextField(
                                        value = viewModel.creatorRedirectUrl,
                                        onValueChange = { viewModel.creatorRedirectUrl = it },
                                        placeholder = { Text("Destination Redirect URL (Editable anytime!)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = IndigoAccent,
                                            unfocusedTextColor = IndigoAccent,
                                            focusedBorderColor = EmeraldPrime,
                                            unfocusedBorderColor = SlateBorder,
                                            focusedContainerColor = SlateDark,
                                            unfocusedContainerColor = SlateDark,
                                            focusedLabelColor = EmeraldPrime,
                                            unfocusedLabelColor = Color(0xFFCAC4D0),
                                            focusedPlaceholderColor = Color(0xFF938F99),
                                            unfocusedPlaceholderColor = Color(0xFF938F99)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    
                                    // UTM Analytics parameters generator
                                    Text("Campaign UTM Parameters for Tracking", fontSize = 10.sp, color = Color(0xFFCAC4D0), modifier = Modifier.padding(top = 4.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedTextField(
                                            value = viewModel.creatorUtmSource,
                                            onValueChange = { viewModel.creatorUtmSource = it },
                                            placeholder = { Text("Source", color = Color(0xFF938F99)) },
                                            modifier = Modifier.weight(1f),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = IndigoAccent,
                                                unfocusedTextColor = IndigoAccent,
                                                focusedBorderColor = EmeraldPrime,
                                                unfocusedBorderColor = SlateBorder,
                                                focusedContainerColor = SlateDark,
                                                unfocusedContainerColor = SlateDark,
                                                focusedLabelColor = EmeraldPrime,
                                                unfocusedLabelColor = Color(0xFFCAC4D0),
                                                focusedPlaceholderColor = Color(0xFF938F99),
                                                unfocusedPlaceholderColor = Color(0xFF938F99)
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        OutlinedTextField(
                                            value = viewModel.creatorUtmCampaign,
                                            onValueChange = { viewModel.creatorUtmCampaign = it },
                                            placeholder = { Text("Campaign", color = Color(0xFF938F99)) },
                                            modifier = Modifier.weight(1f),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = IndigoAccent,
                                                unfocusedTextColor = IndigoAccent,
                                                focusedBorderColor = EmeraldPrime,
                                                unfocusedBorderColor = SlateBorder,
                                                focusedContainerColor = SlateDark,
                                                unfocusedContainerColor = SlateDark,
                                                focusedLabelColor = EmeraldPrime,
                                                unfocusedLabelColor = Color(0xFFCAC4D0),
                                                focusedPlaceholderColor = Color(0xFF938F99),
                                                unfocusedPlaceholderColor = Color(0xFF938F99)
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Organizing Tag folder name", color = Color(0xFFCAC4D0), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = viewModel.creatorTag,
                        onValueChange = { viewModel.creatorTag = it },
                        singleLine = true,
                        placeholder = { Text("e.g. Q1 Campaign, WI-Fi etc.", color = Color(0xFF938F99)) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("input_tag"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = IndigoAccent,
                            unfocusedTextColor = IndigoAccent,
                            focusedBorderColor = EmeraldPrime,
                            unfocusedBorderColor = SlateBorder,
                            focusedContainerColor = SlateDark,
                            unfocusedContainerColor = SlateDark,
                            focusedLabelColor = EmeraldPrime,
                            unfocusedLabelColor = Color(0xFFCAC4D0),
                            focusedPlaceholderColor = Color(0xFF938F99),
                            unfocusedPlaceholderColor = Color(0xFF938F99)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Design Customization expansion card
            Text("Aesthetics, Palette & Shapes", style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // COLOR SECTIONS
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Foreground Color", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val predefinedColors = listOf(-16777216, -16358485, -15102551, -14513364, -127027)
                                predefinedColors.forEach { col ->
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color(col))
                                            .border(1.dp, if (viewModel.creatorFgColor == col) Color.White else Color.Transparent, CircleShape)
                                            .clickable { viewModel.creatorFgColor = col }
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Background Base", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val preBgColors = listOf(-1, -16777216, -1118482, -328966)
                                preBgColors.forEach { col ->
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color(col))
                                            .border(1.dp, if (viewModel.creatorBgColor == col) Color.Blue else Color.Transparent, CircleShape)
                                            .clickable { 
                                                viewModel.creatorBgColor = col
                                                viewModel.creatorTransparentBg = false
                                            }
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Blue, Color.Red)))
                                        .border(2.dp, if (viewModel.creatorTransparentBg) Color.White else Color.Transparent, CircleShape)
                                        .clickable { viewModel.creatorTransparentBg = true }
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Transparent background selection", tint = Color.White, modifier = Modifier.size(10.dp).align(Alignment.Center))
                                }
                            }
                        }
                    }

                    // GRADIENTS TOGGLERS
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Render Gradient Fill", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Switch(
                            checked = viewModel.creatorIsGradient,
                            onCheckedChange = { viewModel.creatorIsGradient = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldPrime)
                        )
                    }

                    if (viewModel.creatorIsGradient) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf("LINEAR", "RADIAL").forEach { t ->
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { viewModel.creatorGradientType = t }) {
                                        RadioButton(selected = viewModel.creatorGradientType == t, onClick = { viewModel.creatorGradientType = t }, colors = RadioButtonDefaults.colors(selectedColor = EmeraldPrime))
                                        Text(t, color = Color.LightGray, fontSize = 11.sp)
                                    }
                                }
                            }
                            
                            // Select gradient secondary color
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(viewModel.creatorGradientColor2))
                                    .clickable {
                                        // cycle predefined metallic gradients
                                        viewModel.creatorGradientColor2 = when (viewModel.creatorGradientColor2) {
                                            -12328193 -> -34000 // Golden
                                            -34000 -> -16776961 // Blue
                                            else -> -12328193
                                        }
                                    }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(color = SlateBorder)
                    Spacer(modifier = Modifier.height(8.dp))

                    // CELL SHAPES SELECTION
                    Text("Cell Module Shapes", color = Color(0xFFCAC4D0), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val shapes = listOf("SQUARE", "CIRCLE", "ROUNDED", "DIAMOND")
                        shapes.forEach { shape ->
                            val isSelected = viewModel.creatorModuleShape == shape
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) IndigoAccent else SlateDark)
                                    .clickable { viewModel.creatorModuleShape = shape }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(shape, color = if (isSelected) SlateDark else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // FINDER EYES SELECTION
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Finder Corner Eye Shapes", color = Color(0xFFCAC4D0), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val eyes = listOf("CLASSIC", "CIRCULAR", "LEAF", "SHARP")
                        eyes.forEach { eye ->
                            val isSelected = viewModel.creatorEyeStyle == eye
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) IndigoAccent else SlateDark)
                                    .clickable { viewModel.creatorEyeStyle = eye }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(eye, color = if (isSelected) SlateDark else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // CENTER EMBEDDED LOGO SELECTOR
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Central Logo Overlay (Clears space to protect scans)", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val logos = listOf("NONE", "WIFI", "URL", "CONTACT", "EMAIL", "SMS", "GEO", "SOCIAL")
                        logos.forEach { asset ->
                            val isSelected = (asset == "NONE" && viewModel.creatorLogoAsset == null) || (viewModel.creatorLogoAsset == asset)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) EmeraldPrime else SlateDark)
                                    .clickable { viewModel.creatorLogoAsset = if (asset == "NONE") null else asset }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(asset, color = if (isSelected) SlateDark else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // THEMED OUTER FRAME CONFIGURATIONS
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Marketing Wrap Frame & Call-To-Action", color = Color(0xFFCAC4D0), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val frames = listOf("NONE", "MODERN_PIN", "SIMPLE_BANNER", "SOLID_TAG")
                        frames.forEach { frame ->
                            val isSelected = viewModel.creatorFrameStyle == frame
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) IndigoAccent else SlateDark)
                                    .clickable { viewModel.creatorFrameStyle = frame }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(frame.replace("_", " "), color = if (isSelected) SlateDark else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }

                    if (viewModel.creatorFrameStyle != "NONE") {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = viewModel.creatorFrameText,
                            onValueChange = { viewModel.creatorFrameText = it },
                            placeholder = { Text("e.g. SCAN ME / DISCOVER VIP DEALS", color = Color(0xFF938F99), fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = IndigoAccent,
                                unfocusedTextColor = IndigoAccent,
                                focusedBorderColor = EmeraldPrime,
                                unfocusedBorderColor = SlateBorder,
                                focusedContainerColor = SlateDark,
                                unfocusedContainerColor = SlateDark,
                                focusedLabelColor = EmeraldPrime,
                                unfocusedLabelColor = Color(0xFFCAC4D0),
                                focusedPlaceholderColor = Color(0xFF938F99),
                                unfocusedPlaceholderColor = Color(0xFF938F99)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // ERROR CELL SLIDER
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Error Correction Reliability", color = Color(0xFFCAC4D0), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${viewModel.creatorErrorLevel} Level (Restores up to ${
                                when(viewModel.creatorErrorLevel) {
                                    "L" -> "7%"
                                    "Q" -> "25%"
                                    "H" -> "30% (Best with logos)"
                                    else -> "15%"
                                }
                            } loss)",
                            color = EmeraldPrime,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf("L", "M", "Q", "H").forEach { lev ->
                            val isSel = viewModel.creatorErrorLevel == lev
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) EmeraldPrime else SlateDark)
                                    .clickable { viewModel.creatorErrorLevel = lev }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(lev, color = if (isSel) SlateDark else Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

// CAMPAIGN ANALYTICS WORKSPACE
@Composable
fun CampaignAnalyticsWorkspace(
    qrList: List<QrCode>,
    scanLogs: List<ScanLog>,
    viewModel: QrViewModel
) {
    var selectedCampaignFilter by remember { mutableStateOf("ALL") }
    val uniqueTags = remember(qrList) {
        listOf("ALL") + qrList.mapNotNull { it.tag }.distinct()
    }

    // Filter codes & logs referencing current tag folder selection
    val activeList = remember(qrList, selectedCampaignFilter) {
        if (selectedCampaignFilter == "ALL") qrList else qrList.filter { it.tag == selectedCampaignFilter }
    }
    val activeQrIds = remember(activeList) { activeList.map { it.id }.toSet() }
    val activeLogs = remember(scanLogs, activeQrIds) {
        scanLogs.filter { it.qrId in activeQrIds }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(vertical = 4.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Analytics & Reports Library",
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                )

                // Campaign selection dropdown row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Campaign: ", color = Color.Gray, fontSize = 11.sp)
                    Box(modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SlateCard)
                        .clickable {
                            // Cycle through campaigns
                            val idx = uniqueTags.indexOf(selectedCampaignFilter)
                            val nextIdx = (idx + 1) % uniqueTags.size
                            selectedCampaignFilter = uniqueTags[nextIdx]
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(selectedCampaignFilter.uppercase(), color = EmeraldPrime, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // Aggregate stats cards row
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AnalyticsStatCard(
                    title = "Total Scans",
                    valStr = "${activeList.sumOf { it.scanCount }}",
                    sub = "All active codes",
                    color = EmeraldPrime,
                    modifier = Modifier.weight(1f)
                )
                AnalyticsStatCard(
                    title = "Tracking Designs",
                    valStr = "${activeList.size}",
                    sub = "Stored locally",
                    color = IndigoAccent,
                    modifier = Modifier.weight(1f)
                )
                AnalyticsStatCard(
                    title = "Fired Hooks",
                    valStr = "${activeLogs.size}",
                    sub = "Zapier alerts",
                    color = Color.Yellow,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // PROGRAMMATIC LINE CHART CARD (Canvas Bezier line charting)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "SCAN FREQUENCY TIMELINE (LAST 7 PERIODS)",
                        style = MaterialTheme.typography.labelSmall.copy(color = EmeraldPrime, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Draw program paths inside standard canvas
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    ) {
                        val stroke = Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        // Mock peaks for timeline values
                        val points = listOf(0.1f, 0.45f, 0.3f, 0.85f, 0.6f, 0.95f, 0.75f)
                        val w = size.width
                        val h = size.height
                        
                        // Draw grid lines
                        for (i in 0..4) {
                            val gy = h * (i / 4f)
                            drawLine(Color.Gray.copy(alpha = 0.15f), start = Offset(0f, gy), end = Offset(w, gy))
                        }

                        val path = Path()
                        points.forEachIndexed { idx, value ->
                            val px = w * (idx / (points.size - 1).toFloat())
                            val py = h - (h * value)
                            if (idx == 0) {
                                path.moveTo(px, py)
                            } else {
                                val prevX = w * ((idx - 1) / (points.size - 1).toFloat())
                                val prevY = h - (h * points[idx - 1])
                                // Cubic bezier control curves
                                path.cubicTo(
                                    (prevX + px) / 2f, prevY,
                                    (prevX + px) / 2f, py,
                                    px, py
                                )
                            }
                            drawCircle(color = EmeraldPrime, radius = 5.dp.toPx(), center = Offset(px, py))
                        }
                        
                        drawPath(path = path, color = EmeraldPrime, style = stroke)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { label ->
                            Text(label, color = Color.Gray, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // LOCATION AND DEVICE PLATFORMS ROW
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Location Ranker Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.weight(1f).height(180.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("GEOLOCATION METRICS", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val mockLocations = listOf(
                            Pair("San Francisco", 45),
                            Pair("New York", 28),
                            Pair("London Office", 18),
                            Pair("Tokyo Japan", 9)
                        )
                        
                        mockLocations.forEach { loc ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(loc.first, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("${loc.second}%", color = EmeraldPrime, fontSize = 11.sp)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                // Horizontal fill progress
                                Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(SlateLightBorder(), CircleShape)) {
                                    Box(modifier = Modifier.fillMaxWidth(loc.second / 100f).height(4.dp).background(EmeraldPrime, CircleShape))
                                }
                            }
                        }
                    }
                }

                // Device platforms Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.weight(1f).height(180.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("SCANNER DEVICES & OS", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhoneAndroid, "Android Devices icon", tint = EmeraldPrime, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Android (Mobile/Tablet)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("62.5% density share", color = Color.LightGray, fontSize = 10.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhoneIphone, "Apple iOS scan icon", tint = IndigoAccent, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("iOS (iPhone/iPad)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("37.5% density share", color = Color.LightGray, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        // SCANS HEATMAP MATRIX (Canvas heatmap representation)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "DAILY ACTIVE TRAFFIC HEATMAP (TIME-OF-DAY MATRIX)",
                        style = MaterialTheme.typography.labelSmall.copy(color = EmeraldPrime, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val hours = listOf("00H-04H", "04H-08H", "08H-12H", "12H-16H", "16H-20H", "20H-24H")
                        hours.forEach { hr ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text(hr, fontSize = 8.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 4.dp))
                                // Daily 7 blocks
                                for (d in 0..6) {
                                    val intensity = remember { (1..10).random() / 10f }
                                    Box(
                                        modifier = Modifier
                                            .padding(vertical = 2.dp)
                                            .size(20.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(EmeraldPrime.copy(alpha = intensity))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
fun AnalyticsStatCard(
    title: String,
    valStr: String,
    sub: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, color = Color(0xFFCAC4D0), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(valStr, color = color, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(2.dp))
            Text(sub, color = Color(0xFFCAC4D0).copy(alpha = 0.8f), fontSize = 8.sp)
        }
    }
}

// ADVANCED COOPERATION SETTINGS (Password protection, Webhooks simulation logs, CSV merging generator)
@Composable
fun AdvancedSettingsWorkspace(
    viewModel: QrViewModel
) {
    val context = LocalContext.current
    var inputSecretKey by remember { mutableStateOf("sec_k_lqmzpwxv92984183") }
    var generatedApiAlertMsg by remember { mutableStateOf<String?>(null) }
    
    // Password portal test
    var testPasswordText by remember { mutableStateOf("") }
    var revealedContentAlert by remember { mutableStateOf<String?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(vertical = 4.dp)) {
        item {
            Text(
                text = "Advanced Tools & Team Workspace",
                style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Webhooks payload tracker
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ElectricBolt, "Webhook Zapier symbol", tint = Color.Yellow, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "LIVE WEBHOOK OUTBOX (ZAPIER / INTERGROMAT)",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Every time a dynamic QR Architect code is scanned on a customer device, a webhook alert can trigger instantly. Simulate events below by tapping on any item inside the Library drawer.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (viewModel.webhookLogs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SlateDark, RoundedCornerShape(8.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No webhooks fired yet in current instance. Scan demo QR codes.", color = Color.DarkGray, fontSize = 11.sp)
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(SlateDark, RoundedCornerShape(8.dp))
                                .verticalScroll(rememberScrollState())
                                .padding(8.dp)
                        ) {
                            viewModel.webhookLogs.forEach { log ->
                                Text(
                                    text = log,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = EmeraldPrime,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        }

        // CSV Batch generator block
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DriveFileRenameOutline, "CSV import file icon", tint = EmeraldPrime)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "BULK CSV GENERATOR MODULE",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Input row definitions in comma-separated strings (Title,Content,CampaignTag,SubFolder). Ideal for generating cards or coupons in batches.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = viewModel.csvInputText,
                        onValueChange = { viewModel.csvInputText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .testTag("csv_input_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = SlateDark,
                            unfocusedContainerColor = SlateDark,
                            unfocusedBorderColor = SlateBorder
                        ),
                        placeholder = { Text("CSV: Title,Content,Tag,Folder") }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.processCsvBatchGeneration() },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrime),
                        modifier = Modifier
                            .align(Alignment.End)
                            .height(38.dp)
                            .testTag("csv_process_button")
                    ) {
                        Text("BATCH REST API SYNCHRONIZE", color = SlateDark, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                    }

                    if (viewModel.batchGenerateResultMsg.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = viewModel.batchGenerateResultMsg,
                            color = EmeraldPrime,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Password protected portal simulation
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LockClock, "Lock Clock Icon", tint = Color.LightGray)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SECURITY TESTING portal",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Test standard decryption on codes setup with custom access locks. Input password to reveal the locked URL.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = testPasswordText,
                            onValueChange = { testPasswordText = it },
                            placeholder = { Text("Input passcode (Try: AccessGranted77)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = SlateDark, unfocusedContainerColor = SlateDark)
                        )

                        Button(
                            onClick = {
                                if (testPasswordText == "AccessGranted77") {
                                    revealedContentAlert = "SUCCESS: Decrypted payload revealed! Redirecting destination URL is: https://qrarc.co/deal-active"
                                } else {
                                    revealedContentAlert = "FAILED: Incorrect passcode verification! Connection rejected."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent, contentColor = SlateDark),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text("DECRYPT", color = SlateDark, fontWeight = FontWeight.Bold)
                        }
                    }

                    revealedContentAlert?.let { alert ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = alert,
                            color = if (alert.startsWith("SUCCESS")) EmeraldPrime else Color.Yellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // REST Simulated Keys Workspace
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Key, "Workspace API logo", tint = EmeraldPrime)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "REST COMPLEMENTARY API SEEDS & SCOPES",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "For teams programmatically generating labels. Rotate workspace credentials for dynamic access tokens.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = inputSecretKey,
                        onValueChange = { inputSecretKey = it },
                        readOnly = true,
                        leadingIcon = { Icon(Icons.Default.VpnKey, "Key symbol icon", tint = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, focusedContainerColor = SlateDark, unfocusedContainerColor = SlateDark)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val keys = "sec_k_" + UUID.randomUUID().toString().replace("-", "").take(16)
                            inputSecretKey = keys
                            generatedApiAlertMsg = "Flashed rotation completed! Stored scopes authorized: ['READ_ANALYTICS', 'PUT_REDIRECTS', 'WRITE_CODES']"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent, contentColor = SlateDark),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text("ROTATE SECRET SCOPES", color = SlateDark, fontWeight = FontWeight.Bold)
                    }

                    generatedApiAlertMsg?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = msg,
                            color = EmeraldPrime,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

// SLIDE OUT ACTION DRAWER / SIDEBAR (Individual item actions)
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailActionsSidebar(
    qr: QrCode,
    allLogs: List<ScanLog>,
    onDismiss: () -> Unit,
    viewModel: QrViewModel,
    onTriggerScanSimulation: () -> Unit
) {
    val context = LocalContext.current
    var editingUrl by remember(qr) { mutableStateOf(qr.dynamicRedirectUrl ?: qr.content) }
    var showRedirectionLog by remember { mutableStateOf(false) }

    val fullSizeBitmap = remember(qr) {
        QrGenerator.generateQrBitmap(qr, 320).asImageBitmap()
    }

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
            .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            .testTag("detail_sidebar"),
        color = SlateCard,
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "INTELLIGENT INSIGHTS",
                    style = MaterialTheme.typography.labelSmall.copy(color = EmeraldPrime, fontWeight = FontWeight.Bold)
                )

                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close detailed insights panel", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Centered QR Code Preview
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = fullSizeBitmap,
                    contentDescription = "Full visual QR",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                qr.title,
                style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Dynamic Action Status Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .background(SlateDark, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("ACTIVE STATE", color = Color.Gray, fontSize = 9.sp)
                    Text(
                        text = if (qr.isActive) "ACTIVE (LIVE)" else "PAUSED (OFF)",
                        color = if (qr.isActive) EmeraldPrime else Color.Yellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                Switch(
                    checked = qr.isActive,
                    onCheckedChange = { viewModel.toggleActivation(qr) },
                    colors = SwitchDefaults.colors(checkedThumbColor = EmeraldPrime)
                )
            }

            // SIMULATED SCAN TRIGGER BUTTON
            Button(
                onClick = onTriggerScanSimulation,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrime, contentColor = SlateDark),
                modifier = Modifier.fillMaxWidth().height(36.dp).testTag("sim_scan_button")
            ) {
                Icon(Icons.Default.PhoneIphone, "scan emulator launch", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("TRIGGER SIMULATED SCAN", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // DYNAMIC ROUTING PANEL
            if (qr.type == "URL") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateDark),
                    border = BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shuffle, "redirection node symbol", tint = EmeraldPrime, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("DYNAMIC ROUTE LINK", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = editingUrl,
                            onValueChange = { editingUrl = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontSize = 10.sp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldPrime, unfocusedBorderColor = SlateBorder)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = {
                                viewModel.changeRedirectUrl(qr, editingUrl)
                                Toast.makeText(context, "Redirect URL configured live! reprint unnecessary.", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent, contentColor = SlateDark),
                            modifier = Modifier.align(Alignment.End).height(32.dp).testTag("routing_update_button")
                        ) {
                            Text("UPDATE DESTINATION", fontSize = 9.sp, color = SlateDark, fontWeight = FontWeight.Bold)
                        }

                        // Redirection logs trail
                        if (qr.redirectHistoryJson != "[]") {
                            Text(
                                text = "Redirect Log History",
                                color = EmeraldPrime,
                                fontSize = 9.sp,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clickable { showRedirectionLog = !showRedirectionLog }
                            )

                            if (showRedirectionLog) {
                                Text(
                                    qr.redirectHistoryJson,
                                    fontSize = 8.sp,
                                    color = Color.LightGray,
                                    modifier = Modifier.padding(top = 4.dp),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // CORE METRICS DRILL DOWN
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateDark),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("LIFETIME ANALYTICS STATS", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Scans Count Last 30d:", color = Color.LightGray, fontSize = 10.sp)
                        Text("${qr.scanCount} totals", color = EmeraldPrime, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Divider(color = SlateBorder, modifier = Modifier.padding(vertical = 4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Asset Encoding Format:", color = Color.LightGray, fontSize = 10.sp)
                        Text(qr.errorCorrectionLevel + " Level", color = Color.White, fontSize = 10.sp)
                    }
                    Divider(color = SlateBorder, modifier = Modifier.padding(vertical = 4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Created stamp:", color = Color.LightGray, fontSize = 10.sp)
                        val format = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                        Text(format.format(Date(qr.creationDate)), color = Color.LightGray, fontSize = 10.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // COPY, RESTORE AND DESTRUCTION ROW
            Button(
                onClick = {
                    viewModel.duplicateQr(qr)
                    Toast.makeText(context, "Duplicated design replica, check catalog!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SlateDark),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth().height(40.dp).testTag("duplicate_button")
            ) {
                Icon(Icons.Default.FileCopy, "duplicate clone", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("CLONE REPLICA ASSET", fontSize = 10.sp, color = Color.White)
            }

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = {
                    viewModel.loadForEditing(qr)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SlateDark),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth().height(40.dp).testTag("edit_load_button")
            ) {
                Icon(Icons.Default.Edit, "Edit load", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("EDIT STYLING DETAILS", fontSize = 10.sp, color = Color.White)
            }

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = {
                    viewModel.deleteQr(qr)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x27EF4444)),
                border = BorderStroke(1.dp, Color.Red),
                modifier = Modifier.fillMaxWidth().height(40.dp).testTag("delete_button")
            ) {
                Icon(Icons.Default.Delete, "destruction icon", tint = Color.Red, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("DELETE FROM LIST", fontSize = 10.sp, color = Color.Red)
            }
        }
    }
}

// STANDARD BOTTOM NAVIGATION TAB SELECT CELL
@Composable
fun QrBottomNavigation(
    activeTab: String,
    onTabSelected: (String) -> Unit
) {
    NavigationBar(
        containerColor = SlateCard,
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(62.dp)
    ) {
        val items = listOf(
            Pair("library", Triple("Library", Icons.Default.LibraryBooks, Icons.Default.LibraryBooks)),
            Pair("creator", Triple("Creator", Icons.Default.ColorLens, Icons.Default.ColorLens)),
            Pair("analytics", Triple("Analytics", Icons.Default.Leaderboard, Icons.Default.Leaderboard)),
            Pair("advanced", Triple("Advanced", Icons.Default.AutoAwesome, Icons.Default.AutoAwesome))
        )

        items.forEach { item ->
            val isSelected = activeTab == item.first
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(item.first) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.second.second else item.second.third,
                        contentDescription = item.second.first,
                        tint = if (isSelected) SlateDark else Color(0xFF938F99),
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = item.second.first,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) EmeraldPrime else Color(0xFF938F99),
                        fontSize = 11.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = EmeraldPrime,
                    selectedIconColor = SlateDark,
                    unselectedIconColor = Color(0xFF938F99)
                )
            )
        }
    }
}

// Helpers for specific fallback items
@Composable
fun SlateLightBorder(): Color = SlateBorder.copy(alpha = 0.5f)

@Composable
fun Modifier.fillPadding(): Modifier = this.padding(vertical = 4.dp)
