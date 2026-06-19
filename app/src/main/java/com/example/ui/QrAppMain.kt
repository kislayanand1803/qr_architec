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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
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
val SlateDark = Color(0xFF08080D)     // #08080D - Deep dark space background
val SlateCard = Color(0xFF11111B)     // #11111B - Soft elevated card background
val SlateBorder = Color(0x0DFFFFFF)   // #0DFFFFFF (rgba 255,255,255,0.05) - Subtle contrast border
val EmeraldPrime = Color(0xFFD8B4FE)  // #D8B4FE - Vibrant Light Purple (Accent)
val IndigoAccent = Color(0xFFE9D5FF)  // #E9D5FF - Soft bright purple for labels

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
            .padding(horizontal = 6.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(EmeraldPrime, Color(0xFFC084FC)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "QR Architect Logo",
                    tint = SlateDark,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp)) // Increased spacing
            Column {
                Text(
                    text = "QR Architect",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.ExtraBold, // Larger and bolder
                        fontSize = 26.sp,
                        color = Color.White,
                        letterSpacing = (-0.5).sp
                    )
                )
                Text(
                    text = "Manage your dynamic codes",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.Gray, // Smaller and gray
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.1.sp
                    )
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = showWalkthroughHelp,
                modifier = Modifier
                    .size(36.dp)
                    .background(SlateCard, CircleShape) // Rounded icon button
                    .border(BorderStroke(1.dp, SlateBorder), CircleShape)
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
        // Group 1: Standalone clean search card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            border = BorderStroke(1.dp, SlateBorder)
        ) {
            OutlinedTextField(
                value = viewModel.searchQuery,
                onValueChange = { viewModel.searchQuery = it },
                placeholder = { Text("Search QR codes by title or destination...", color = Color(0xFF938F99), fontSize = 13.sp) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon", tint = Color(0xFF938F99), modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (viewModel.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = Color(0xFF938F99), modifier = Modifier.size(20.dp))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("search_field"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                shape = RoundedCornerShape(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Group 2: Categories Filter Section
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "ENCODING TYPE",
                style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
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
                            containerColor = SlateCard,
                            labelColor = Color(0xFFCAC4D0)
                        ),
                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = isSelected, borderColor = SlateBorder, selectedBorderColor = EmeraldPrime)
                    )
                }
            }
        }

        if (availableTags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))

            // Group 3: Campaigns & Tags Section
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "CAMPAIGNS & TAGS",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isAllTagsSelected = viewModel.selectedTagFilter.isEmpty()
                    FilterChip(
                        selected = isAllTagsSelected,
                        onClick = { viewModel.selectedTagFilter = "" },
                        label = { Text("ALL TAGS", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldPrime,
                            selectedLabelColor = SlateDark,
                            containerColor = SlateCard,
                            labelColor = Color(0xFFCAC4D0)
                        ),
                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = isAllTagsSelected, borderColor = SlateBorder, selectedBorderColor = EmeraldPrime)
                    )
                    availableTags.forEach { tag ->
                        val isSelected = viewModel.selectedTagFilter == tag
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectedTagFilter = tag },
                            label = { Text(tag.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldPrime,
                                selectedLabelColor = SlateDark,
                                containerColor = SlateCard,
                                labelColor = Color(0xFFCAC4D0)
                            ),
                            border = FilterChipDefaults.filterChipBorder(enabled = true, selected = isSelected, borderColor = SlateBorder, selectedBorderColor = EmeraldPrime)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))

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
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Smaller Informational Card showing active code count with light indicator dot
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(EmeraldPrime, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${filteredList.size} codes active",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            // Sorting order pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable {
                        viewModel.selectedSortOrder = when (viewModel.selectedSortOrder) {
                            "DATE_DESC" -> "DATE_ASC"
                            "DATE_ASC" -> "TITLE_ASC"
                            "TITLE_ASC" -> "SCANS_DESC"
                            else -> "DATE_DESC"
                        }
                    }
                    .background(SlateCard, RoundedCornerShape(10.dp))
                    .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sort,
                    contentDescription = "Sort Icon",
                    tint = EmeraldPrime,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when (viewModel.selectedSortOrder) {
                        "DATE_ASC" -> "Date (Oldest)"
                        "TITLE_ASC" -> "A - Z Alphabet"
                        "SCANS_DESC" -> "Popularity"
                        else -> "Date (Newest)"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = IndigoAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
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
    val context = LocalContext.current
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

    // Chart Time period filter (7 Days, 30 Days, 90 Days)
    var chartRangeFilter by remember { mutableStateOf("7D") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Dropdown campaign filters
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Analytics Overview",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )

                // Campaign selection dropdown raw
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Campaign: ", color = Color.Gray, fontSize = 12.sp)
                    Box(modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SlateCard)
                        .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(8.dp))
                        .clickable {
                            // Cycle through campaigns
                            val idx = uniqueTags.indexOf(selectedCampaignFilter)
                            val nextIdx = (idx + 1) % uniqueTags.size
                            selectedCampaignFilter = uniqueTags[nextIdx]
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = selectedCampaignFilter.uppercase(),
                                color = EmeraldPrime,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Change Campaign",
                                tint = EmeraldPrime,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // 1. TOP KPI CARDS
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnalyticsStatCard(
                    title = "Total Scans",
                    valStr = "${activeList.sumOf { it.scanCount }}",
                    sub = "All active codes",
                    color = EmeraldPrime, // Color accent only for important metrics
                    modifier = Modifier.weight(1f)
                )
                AnalyticsStatCard(
                    title = "Tracking Designs",
                    valStr = "${activeList.size}",
                    sub = "Stored insights",
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                AnalyticsStatCard(
                    title = "Triggered Hooks",
                    valStr = "${activeLogs.size}",
                    sub = "Zapier alerts",
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 2. SCAN ACTIVITY HERO CHART CARD
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Scan Activity",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 18.sp
                                )
                            )
                            Text(
                                text = when (chartRangeFilter) {
                                    "7D" -> "Last 7 Days"
                                    "30D" -> "Last 30 Days"
                                    else -> "Last 90 Days"
                                },
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        // Style [7D ▼] Switcher
                        Row(
                            modifier = Modifier
                                .background(SlateDark, RoundedCornerShape(10.dp))
                                .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(10.dp))
                                .clickable {
                                    chartRangeFilter = when (chartRangeFilter) {
                                        "7D" -> "30D"
                                        "30D" -> "90D"
                                        else -> "7D"
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (chartRangeFilter) {
                                    "7D" -> "7D"
                                    "30D" -> "30D"
                                    else -> "90D"
                                },
                                color = EmeraldPrime,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Change timeline filter",
                                tint = EmeraldPrime,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Chart data configuration
                    val points = remember(chartRangeFilter) {
                        when (chartRangeFilter) {
                            "7D" -> listOf(0.12f, 0.45f, 0.3f, 0.85f, 0.6f, 0.95f, 0.75f)
                            "30D" -> listOf(0.2f, 0.4f, 0.35f, 0.6f, 0.5f, 0.75f, 0.65f, 0.85f, 0.7f, 0.95f)
                            else -> listOf(0.3f, 0.5f, 0.45f, 0.65f, 0.55f, 0.78f, 0.7f, 0.92f, 0.85f, 0.98f)
                        }
                    }
                    val xLabels = remember(chartRangeFilter) {
                        when (chartRangeFilter) {
                            "7D" -> listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                            "30D" -> listOf("Jun 1", "Jun 5", "Jun 10", "Jun 15", "Jun 20", "Jun 25", "Jun 30")
                            else -> listOf("Apr 1", "Apr 15", "May 1", "May 15", "Jun 1", "Jun 15", "Jun 30")
                        }
                    }

                    // Pure visual high-fidelity Curve Canvas with translucent gradient fill
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    ) {
                        val stroke = Stroke(width = 3.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        val w = size.width
                        val h = size.height
                        
                        // Thin grid lines
                        for (i in 0..4) {
                            val gy = h * (i / 4f)
                            drawLine(Color.White.copy(alpha = 0.03f), start = Offset(0f, gy), end = Offset(w, gy))
                        }

                        val path = Path()
                        val fillPath = Path()
                        
                        val pointCount = points.size
                        val stepX = w / (pointCount - 1).toFloat()
                        
                        points.forEachIndexed { idx, value ->
                            val px = idx * stepX
                            val py = h - (h * value * 0.80f) - 12.dp.toPx() // Clean padding inside chart canvas
                            
                            if (idx == 0) {
                                path.moveTo(px, py)
                                fillPath.moveTo(px, py)
                            } else {
                                val prevX = (idx - 1) * stepX
                                val prevY = h - (h * points[idx - 1] * 0.80f) - 12.dp.toPx()
                                
                                val controlX1 = prevX + (stepX / 2f)
                                val controlY1 = prevY
                                val controlX2 = prevX + (stepX / 2f)
                                val controlY2 = py
                                
                                path.cubicTo(controlX1, controlY1, controlX2, controlY2, px, py)
                                fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, px, py)
                            }
                        }
                        
                        // Close visual area fill to bottom edges
                        fillPath.lineTo(w, h)
                        fillPath.lineTo(0f, h)
                        fillPath.close()
                        
                        // Draw vertical glow fill path
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(EmeraldPrime.copy(alpha = 0.15f), Color.Transparent)
                            )
                        )
                        
                        // Draw main line path
                        drawPath(path = path, color = EmeraldPrime, style = stroke)
                        
                        // Larger interactive data dots
                        points.forEachIndexed { idx, value ->
                            val px = idx * stepX
                            val py = h - (h * value * 0.80f) - 12.dp.toPx()
                            
                            // Glowing aura
                            drawCircle(
                                color = EmeraldPrime.copy(alpha = 0.25f),
                                radius = 7.dp.toPx(),
                                center = Offset(px, py)
                            )
                            // Core point dot
                            drawCircle(
                                color = EmeraldPrime,
                                radius = 4.dp.toPx(),
                                center = Offset(px, py)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    
                    // Cleaner smaller x-axis labels
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        xLabels.forEach { label ->
                            Text(
                                text = label,
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // 3. TOP GEOLOCATIONS CARD
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Top Locations",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp
                            )
                        )
                        
                        Text(
                            text = "View All",
                            color = EmeraldPrime,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    Toast.makeText(context, "Full geographic expansion loaded!", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    val mockLocations = listOf(
                        Pair("San Francisco", 45),
                        Pair("New York", 28),
                        Pair("London", 18),
                        Pair("Tokyo", 9)
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        mockLocations.forEach { loc ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = loc.first,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${loc.second}%",
                                        color = Color.Gray,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                // Thinner, modern progress bars
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .background(Color(0x14FFFFFF), CircleShape)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(loc.second / 100f)
                                            .height(6.dp)
                                            .background(EmeraldPrime, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. DEVICE ANALYTICS CARD
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Device Distribution",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Android device distribution
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(SlateDark, RoundedCornerShape(16.dp))
                                .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(16.dp))
                                .padding(18.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = "Android platform",
                                    tint = EmeraldPrime,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "Android",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "62.5%",
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(Color(0x14FFFFFF), CircleShape)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.625f)
                                        .height(4.dp)
                                        .background(EmeraldPrime, CircleShape)
                                )
                            }
                        }

                        // iOS device distribution
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(SlateDark, RoundedCornerShape(16.dp))
                                .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(16.dp))
                                .padding(18.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhoneIphone,
                                    contentDescription = "iOS platform",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "iOS",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "37.5%",
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(Color(0x14FFFFFF), CircleShape)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.375f)
                                        .height(4.dp)
                                        .background(EmeraldPrime, CircleShape)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. TRAFFIC HEATMAP MATRIX
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Traffic Heatmap",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                    )
                    Text(
                        text = "Average activity by time of day",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    val hours = listOf("00h", "04h", "08h", "12h", "16h", "20h")
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Day names on top
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.width(36.dp)) // Hour labels gap offset
                            days.forEach { day ->
                                Text(
                                    text = day,
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        
                        // Hourly row sets
                        hours.forEachIndexed { hIdx, hr ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = hr,
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(36.dp)
                                )
                                
                                days.forEachIndexed { dIdx, _ ->
                                    val intensity = remember(hIdx, dIdx) {
                                        val base = if (hIdx in 2..4) 0.65f else 0.18f
                                        val dayMultiplier = if (dIdx in 1..4) 1.2f else 0.7f
                                        (base * dayMultiplier).coerceIn(0.05f, 1.0f)
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1.5f) // Wider cells for nice modern grid alignment
                                            .padding(2.dp)
                                            .clip(RoundedCornerShape(4.dp)) // Rounded squares/rectangles
                                            .background(EmeraldPrime.copy(alpha = intensity))
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Legend indicator
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Low", color = Color.Gray, fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .width(70.dp)
                                .height(5.dp)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(EmeraldPrime.copy(alpha = 0.05f), EmeraldPrime)
                                    ),
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("High", color = Color.Gray, fontSize = 11.sp)
                    }
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(24.dp))
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = valStr,
                color = color,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = sub,
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
    }
}

// ADVANCED COOPERATION SETTINGS (Password protection, Webhooks simulation logs, CSV merging generator)
@Composable
fun AdvancedSectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badgeText: String? = null,
    badgeColor: Color = Color.Gray
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(Color(0x0AFFFFFF), CircleShape)
                    .border(BorderStroke(1.dp, Color(0x14FFFFFF)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFD8B4FE), // Lavender accent
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        letterSpacing = (-0.3).sp
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                )
            }
        }
        
        if (badgeText != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeColor.copy(alpha = 0.1f))
                    .border(BorderStroke(1.dp, badgeColor.copy(alpha = 0.2f)), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(badgeColor, CircleShape)
                    )
                    Text(
                        text = badgeText.uppercase(),
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun AdvancedPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(50.dp)
            .fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFD8B4FE), // Premium Filled lavender
            contentColor = Color(0xFF08080D) // Contrast text
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF08080D),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AdvancedSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    textColor: Color = Color(0xFFCAC4D0)
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .height(50.dp)
            .fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = textColor
        ),
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

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

    // Navigation and show control toggles
    var showWebhookLogs by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp) // Perfect spacing between sections
    ) {
        // Page Header Title Segment
        item {
            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                Text(
                    text = "Advanced workspace",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontSize = 24.sp,
                        letterSpacing = (-0.5).sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Developer tools and automation features",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                )
            }
        }

        // 1. Webhook activity Card Section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) { // 20px dynamic card internal padding
                    AdvancedSectionHeader(
                        icon = Icons.Default.ElectricBolt,
                        title = "Webhook activity",
                        subtitle = "Monitor external automation events",
                        badgeText = if (viewModel.webhookLogs.isNotEmpty()) "Active" else "No activity",
                        badgeColor = if (viewModel.webhookLogs.isNotEmpty()) EmeraldPrime else Color.Gray
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Every time a dynamic QR Architect code is scanned on a customer device, a webhook alert can trigger instantly. Webhooks route automatically to integrated automation platforms.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F1020), RoundedCornerShape(14.dp))
                            .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Instance Status",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(if (viewModel.webhookLogs.isNotEmpty()) EmeraldPrime else Color.Gray, CircleShape)
                            )
                            Text(
                                text = if (viewModel.webhookLogs.isNotEmpty()) "Events Active" else "No events received",
                                color = if (viewModel.webhookLogs.isNotEmpty()) EmeraldPrime else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (showWebhookLogs) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .background(Color(0xFF0F1020), RoundedCornerShape(14.dp))
                                        .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(14.dp))
                                        .padding(16.dp)
                        ) {
                            if (viewModel.webhookLogs.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Log streams are currently clean.", 
                                        color = Color.DarkGray, 
                                        fontSize = 12.sp, 
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                    Column {
                                        viewModel.webhookLogs.forEachIndexed { idx, log ->
                                            Text(
                                                text = log,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = Color(0xFFD8B4FE), // Lavender text for logs
                                                modifier = Modifier.padding(bottom = 6.dp)
                                            )
                                            if (idx < viewModel.webhookLogs.size - 1) {
                                                Divider(color = Color(0x0DFFFFFF), modifier = Modifier.padding(vertical = 4.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    AdvancedSecondaryButton(
                        text = if (showWebhookLogs) "Hide logs" else "View logs",
                        icon = if (showWebhookLogs) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        onClick = { showWebhookLogs = !showWebhookLogs }
                    )
                }
            }
        }

        // 2. Batch generator Card Section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    AdvancedSectionHeader(
                        icon = Icons.Default.Description,
                        title = "Batch generator",
                        subtitle = "Generate QR cards from CSV templates"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Input row definitions in comma-separated structures (Title,Content,CampaignTag,SubFolder). Ideal for generating cards or coupons in automated batch operations.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Code editor layout
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F1020), RoundedCornerShape(14.dp))
                            .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "template.csv",
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF64748B),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "UTF-8",
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF64748B),
                                fontSize = 10.sp
                            )
                        }

                        OutlinedTextField(
                            value = viewModel.csvInputText,
                            onValueChange = { viewModel.csvInputText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .testTag("csv_input_field"),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                color = Color.White,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(12.dp),
                            placeholder = {
                                Text(
                                    text = "Promo Badge,https://deal.co/badge1,Marketing,Sales",
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF475569),
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    AdvancedPrimaryButton(
                        text = "Generate batch",
                        icon = Icons.Default.Check,
                        onClick = { viewModel.processCsvBatchGeneration() }
                    )

                    if (viewModel.batchGenerateResultMsg.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F1020), RoundedCornerShape(14.dp))
                                .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(14.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = viewModel.batchGenerateResultMsg,
                                fontFamily = FontFamily.Monospace,
                                color = EmeraldPrime,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 3. Security tools Card Section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    AdvancedSectionHeader(
                        icon = Icons.Default.Lock,
                        title = "Security tools",
                        subtitle = "Protected access and encrypted links",
                        badgeText = "Protected",
                        badgeColor = EmeraldPrime
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Test instant credentials decryption on secure codes set up with physical access blocks. Input standard passphrase to decrypt simulated database payloads.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = testPasswordText,
                            onValueChange = { testPasswordText = it },
                            placeholder = { Text("Input passcode (AccessGranted77)", fontSize = 13.sp) },
                            singleLine = true,
                            leadingIcon = { 
                                Icon(
                                    imageVector = Icons.Default.Lock, 
                                    contentDescription = "Lock icon", 
                                    tint = Color(0xFFD8B4FE), 
                                    modifier = Modifier.size(18.dp)
                                ) 
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0x33D8B4FE),
                                unfocusedBorderColor = SlateBorder,
                                focusedContainerColor = SlateDark,
                                unfocusedContainerColor = SlateDark
                            ),
                            shape = RoundedCornerShape(14.dp)
                        )

                        Button(
                            onClick = {
                                if (testPasswordText == "AccessGranted77") {
                                    revealedContentAlert = "SUCCESS: Decrypted payload revealed! Redirecting destination URL is: https://qrarc.co/deal-active"
                                } else {
                                    revealedContentAlert = "FAILED: Incorrect passcode verification! Connection rejected."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD8B4FE), // Filled lavender
                                contentColor = Color(0xFF08080D)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Text("Decrypt", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    revealedContentAlert?.let { alert ->
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F1020), RoundedCornerShape(14.dp))
                                .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(14.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = alert,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = if (alert.startsWith("SUCCESS")) EmeraldPrime else Color(0xFFEF4444),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 4. API credentials Card Section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    AdvancedSectionHeader(
                        icon = Icons.Default.Key,
                        title = "API credentials",
                        subtitle = "Manage tokens and secret scopes"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "For development teams programmatically synchronizing dynamic QR campaigns. Rotate credentials workspace seeds to cycle tokens dynamically.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = if (showApiKey) inputSecretKey else "sec_k_****************4183",
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = "Vpn key symbol", tint = Color(0xFFD8B4FE), modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (showApiKey) "Hide API Key" else "Show API Key",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = Color.White
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0x33D8B4FE),
                            unfocusedBorderColor = SlateBorder,
                            focusedContainerColor = Color(0xFF0F1020),
                            unfocusedContainerColor = Color(0xFF0F1020)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AdvancedSecondaryButton(
                            text = "Copy key",
                            icon = Icons.Default.ContentCopy,
                            onClick = {
                                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("API Key", inputSecretKey)
                                clipboardManager.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied workspace token!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        )

                        AdvancedPrimaryButton(
                            text = "Rotate key",
                            icon = Icons.Default.Refresh,
                            onClick = {
                                val keys = "sec_k_" + java.util.UUID.randomUUID().toString().replace("-", "").take(16)
                                inputSecretKey = keys
                                generatedApiAlertMsg = "Credentials seed rotated successfully! Active scopes: ['READ_ANALYTICS', 'PUT_REDIRECTS', 'WRITE_CODES']"
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x0AFFFFFF), RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning icon",
                            tint = Color(0xFFEF4444).copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Treat this key like a password. Never share it in public code repositories.",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    generatedApiAlertMsg?.let { msg ->
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F1020), RoundedCornerShape(14.dp))
                                .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(14.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = msg,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = EmeraldPrime,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 5. Team settings Card Section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    AdvancedSectionHeader(
                        icon = Icons.Default.People,
                        title = "Team settings",
                        subtitle = "Assign roles and collaborate with members",
                        badgeText = "Beta",
                        badgeColor = Color(0xFFD8B4FE)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Invite campaign coordinators, sales representatives, or marketing designers to generate and analyze dynamic QR triggers seamlessly.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F1020), RoundedCornerShape(14.dp))
                            .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0x14FFFFFF), CircleShape)
                                    .border(BorderStroke(1.dp, Color(0x0AFFFFFF)), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("A", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Anand Kislay", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("anandkislay1803@gmail.com", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                        Text("Owner", color = Color(0xFFD8B4FE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    AdvancedSecondaryButton(
                        text = "Invite team member",
                        icon = Icons.Default.Add,
                        onClick = {
                            Toast.makeText(context, "Collaboration invites enabled on premium instances!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // 6. Developer utilities Card Section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    AdvancedSectionHeader(
                        icon = Icons.Default.Settings,
                        title = "Developer utilities",
                        subtitle = "Workspace diagnostic reports and telemetry logger"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Manage local SDK telemetry diagnostic settings or flush localized offline records databases to perform clean instances setup operations.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F1020), RoundedCornerShape(14.dp))
                            .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Telemetry & analytics logging", color = Color.White, fontSize = 13.sp)
                        var isTelemetryActive by remember { mutableStateOf(true) }
                        Switch(
                            checked = isTelemetryActive,
                            onCheckedChange = { isTelemetryActive = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF08080D),
                                checkedTrackColor = Color(0xFFD8B4FE),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = SlateDark
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AdvancedSecondaryButton(
                            text = "Download schema",
                            icon = Icons.Default.Download,
                            onClick = {
                                Toast.makeText(context, "JSON Schema download initiated!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        )

                        AdvancedSecondaryButton(
                            text = "Clear cache",
                            icon = Icons.Default.DeleteOutline,
                            textColor = Color(0xFFEF4444), // Danger outline action
                            onClick = {
                                Toast.makeText(context, "Offline schema caches cleared!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
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
            .width(360.dp) // Wider sidebar for a less cramped, premium presentation
            .border(
                BorderStroke(1.dp, SlateBorder),
                RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
            )
            .testTag("detail_sidebar"),
        color = SlateDark, // Main background is deep #08080D
        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ==========================================
            // STICKY HEADER LAYOUT (Kept fixed at the top)
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title and close row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "QR CODE DETAILS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = EmeraldPrime,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .background(SlateCard, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close detailed insights panel",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // QR Preview Card (Hero Section)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(20.dp),
                            clip = false,
                            spotColor = Color.Black
                        )
                        .testTag("detail_qr_preview_hero"),
                    colors = CardDefaults.cardColors(containerColor = SlateCard), // slightly lighter bg card
                    border = BorderStroke(1.dp, SlateBorder), // outer subtle border
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // QR Box (Increased size & rounded corner padding)
                        Box(
                            modifier = Modifier
                                .size(170.dp) // Increased size
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = fullSizeBitmap,
                                contentDescription = "Full visual QR",
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // QR Name/Title below preview with proper bold typography
                        Text(
                            text = qr.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (!qr.tag.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = qr.tag!!.uppercase(),
                                color = EmeraldPrime,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                // Primary Action Button: TEST SCAN (height 48dp, radius 14dp)
                Button(
                    onClick = onTriggerScanSimulation,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPrime,
                        contentColor = SlateDark
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sim_scan_button"),
                    shape = RoundedCornerShape(14.dp) // 14dp radius per requirements
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneIphone,
                        contentDescription = "Test scan button symbol",
                        modifier = Modifier.size(16.dp),
                        tint = SlateDark
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TEST SCAN",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateDark
                    )
                }

                // Row for Download and Save Design (Fixed elements)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Download
                    Button(
                        onClick = {
                            val rendered = QrGenerator.generateQrBitmap(qr, 512)
                            val savedUri = saveBitmapToGallery(context, rendered, qr.title)
                            if (savedUri != null) {
                                Toast.makeText(context, "PNG design exported successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Could not write to local storage.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SlateDark,
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, SlateBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("download_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download Design",
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("DOWNLOAD", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    // Save Design
                    Button(
                        onClick = {
                            if (qr.type == "URL") {
                                viewModel.changeRedirectUrl(qr, editingUrl)
                                Toast.makeText(context, "Design and routing details synced!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "All parameters are already active!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF161525),
                            contentColor = EmeraldPrime
                        ),
                        border = BorderStroke(1.dp, SlateBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("detail_save_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save Design details",
                            modifier = Modifier.size(16.dp),
                            tint = EmeraldPrime
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SAVE DESIGN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldPrime)
                    }
                }
            }

            // Divider separating Sticky and Scrollable
            Divider(color = SlateBorder, thickness = 1.dp)

            // ==========================================
            // SCROLLABLE CONTENT AREA (Independently scrolling)
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Status Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, SlateBorder),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "STATUS",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (qr.isActive) "🟢 Active" else "🔴 Paused",
                                color = if (qr.isActive) EmeraldPrime else Color.Red,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                        }

                        Switch(
                            checked = qr.isActive,
                            onCheckedChange = { viewModel.toggleActivation(qr) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmeraldPrime,
                                checkedTrackColor = EmeraldPrime.copy(alpha = 0.4f),
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = Color.DarkGray
                            )
                        )
                    }
                }

                // Analytics Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, SlateBorder),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "ANALYTICS",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Divider(color = SlateBorder, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Scans (30 Days)", color = Color.Gray, fontSize = 12.sp)
                            Text(
                                text = "${qr.scanCount}",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Encoding Format", color = Color.Gray, fontSize = 12.sp)
                            Text(
                                text = "${qr.errorCorrectionLevel}-Level",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Created Date", color = Color.Gray, fontSize = 12.sp)
                            val format = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                            val formattedDate = format.format(Date(qr.creationDate))
                            Text(
                                text = formattedDate,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Styling/Dynamic Redirection details (URLs only)
                if (qr.type == "URL") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SlateCard),
                        border = BorderStroke(1.dp, SlateBorder),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "STYLING CONFIGURATION",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = editingUrl,
                                onValueChange = { editingUrl = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontSize = 12.sp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldPrime,
                                    unfocusedBorderColor = SlateBorder,
                                    focusedContainerColor = SlateDark,
                                    unfocusedContainerColor = SlateDark
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    viewModel.changeRedirectUrl(qr, editingUrl)
                                    Toast.makeText(context, "Destination updated successfully", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrime, contentColor = SlateDark),
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .height(36.dp)
                                    .testTag("routing_update_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("UPDATE DESTINATION", fontSize = 10.sp, color = SlateDark, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Secondary Action: CLONE QR (Height 48dp, Radius 14dp)
                Button(
                    onClick = {
                        viewModel.duplicateQr(qr)
                        Toast.makeText(context, "Clone replicated successfully!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("duplicate_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileCopy,
                        contentDescription = "Duplicate design clone",
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clone QR", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }

                // Secondary Action: EDIT STYLE (Height 48dp, Radius 14dp)
                Button(
                    onClick = {
                        viewModel.loadForEditing(qr)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("edit_load_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ColorLens,
                        contentDescription = "Edit Style palette",
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Style", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Danger Zone Action: DELETE QR (Height 48dp, Radius 14dp, Red outline)
                Button(
                    onClick = {
                        viewModel.deleteQr(qr)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.Red
                    ),
                    border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("delete_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete design asset",
                        modifier = Modifier.size(16.dp),
                        tint = Color.Red
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete QR", fontSize = 14.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                }
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
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(82.dp) // Generous height for premium spacing
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                clip = false
            )
            .border(
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
            ),
        tonalElevation = 0.dp // Keeps SlateCard pure dark without unwanted light elevation tint
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
                        tint = if (isSelected) SlateDark else Color(0xFF94A3B8), // Premium Slate color for unselected icon
                        modifier = Modifier.size(26.dp) // Large premium icon size
                    )
                },
                label = {
                    Text(
                        text = item.second.first,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else Color(0xFF64748B), // Brighter white for active, soft slate for inactive
                        fontSize = 12.sp,
                        letterSpacing = 0.2.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color(0xFFD8B4FE), // Beautiful rounded lavender/purple pill
                    selectedIconColor = SlateDark,
                    unselectedIconColor = Color(0xFF64748B)
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
