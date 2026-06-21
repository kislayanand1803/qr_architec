package com.example.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp



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

    // Splash Screen timing tracking state
    var elapsedTime by remember { mutableStateOf(0) }
    val isLibraryLoading = viewModel.isLibraryLoading

    LaunchedEffect(isLibraryLoading) {
        val fps = 60
        val interval = 1000 / fps
        while (elapsedTime < 2600) {
            delay(interval.toLong())
            if (elapsedTime < 2200) {
                elapsedTime += interval
                if (elapsedTime > 2200) elapsedTime = 2200
            } else if (elapsedTime == 2200) {
                if (!isLibraryLoading) {
                    elapsedTime += interval
                }
            } else {
                elapsedTime += interval
            }
        }
    }

    // Walkthrough Onboarding State
    var onboardingStep by remember { mutableIntStateOf(1) }
    var activeWalkthrough by remember { mutableStateOf(false) }

    // Floating Interactive Detail Sidebar
    var activeDetailQr by remember { mutableStateOf<QrCode?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = SlateDark,
            bottomBar = {
                if (viewModel.currentTab == "library") {
                    StaggeredEntrance(
                        delayMs = 320,
                        baseTime = 2200,
                        currentTime = elapsedTime
                    ) {
                        QrBottomNavigation(
                            activeTab = viewModel.currentTab,
                            onTabSelected = { 
                                viewModel.setTab(it)
                                activeDetailQr = null
                            }
                        )
                    }
                } else {
                    QrBottomNavigation(
                        activeTab = viewModel.currentTab,
                        onTabSelected = { 
                            viewModel.setTab(it)
                            activeDetailQr = null
                        }
                    )
                }
            },
            floatingActionButton = {
                if (viewModel.currentTab != "creator") {
                    val showFab = elapsedTime >= 2440
                    AnimatedVisibility(
                        visible = showFab,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        val fabInteractionSource = remember { MutableInteractionSource() }
                        val isFabPressed by fabInteractionSource.collectIsPressedAsState()
                        val fabScale by animateFloatAsState(
                            targetValue = if (isFabPressed) 0.97f else 1.0f,
                            animationSpec = if (isFabPressed) {
                                tween(durationMillis = 100, easing = LinearOutSlowInEasing)
                            } else {
                                spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
                            }
                        )

                        FloatingActionButton(
                            onClick = { viewModel.setTab("creator") },
                            interactionSource = fabInteractionSource,
                            containerColor = Color(0xFFD8B4FE), // Lavender
                            contentColor = Color(0xFF08080D), // Contrast deep SlateDark
                            shape = RoundedCornerShape(20.dp),
                            elevation = FloatingActionButtonDefaults.elevation(
                                defaultElevation = 6.dp,
                                pressedElevation = 12.dp,
                                hoveredElevation = 8.dp,
                                focusedElevation = 8.dp
                            ),
                            modifier = Modifier
                                .size(60.dp)
                                .padding(bottom = 4.dp, end = 4.dp)
                                .graphicsLayer {
                                    scaleX = fabScale
                                    scaleY = fabScale
                                }
                                .testTag("create_qr_fab")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create QR",
                                tint = Color(0xFF08080D),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
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
                    AnimatedVisibility(
                        visible = viewModel.currentTab != "library",
                        enter = fadeIn(animationSpec = tween(durationMillis = 250)) + expandVertically(animationSpec = tween(durationMillis = 250)),
                        exit = fadeOut(animationSpec = tween(durationMillis = 220)) + shrinkVertically(animationSpec = tween(durationMillis = 220))
                    ) {
                        BrandedHeader(
                            onResetDemo = {
                                Toast.makeText(context, "Demo presets restored in background database", Toast.LENGTH_SHORT).show()
                            },
                            showWalkthroughHelp = {
                                onboardingStep = 1
                                activeWalkthrough = true
                            }
                        )
                    }

                    // Persistent Compact Onboarding Handler
                    if (!viewModel.userOnboarded && !viewModel.onboardingDismissed && viewModel.currentTab != "library") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 90.dp)
                                .testTag("compact_onboarding_card"),
                            colors = CardDefaults.cardColors(containerColor = SlateCard),
                            border = BorderStroke(1.dp, SlateBorder),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 10.dp, horizontal = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Welcome Icon",
                                        tint = Color(0xFFD8B4FE),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "✨ Welcome to QR Architect",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Text(
                                            text = "Search, organize and manage your QR assets.",
                                            color = Color.Gray,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { 
                                            onboardingStep = 1
                                            activeWalkthrough = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD8B4FE)),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Continue Setup", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(
                                        onClick = { viewModel.dismissOnboarding() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    } else if ((viewModel.userOnboarded || viewModel.onboardingDismissed) && !viewModel.whatsNewDismissed && viewModel.currentTab != "library") {
                        var showWhatsNewDialog by remember { mutableStateOf(false) }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 90.dp)
                                .testTag("whats_new_card"),
                            colors = CardDefaults.cardColors(containerColor = SlateCard),
                            border = BorderStroke(1.dp, SlateBorder),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 10.dp, horizontal = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NewReleases,
                                        contentDescription = "Updates Icon",
                                        tint = Color(0xFFD8B4FE),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "✨ What's New",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Text(
                                            text = "Latest improvements and release notes.",
                                            color = Color.Gray,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { showWhatsNewDialog = true },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD8B4FE)),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("View Details", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(
                                        onClick = { viewModel.dismissWhatsNew() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss Updates",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        if (showWhatsNewDialog) {
                            var dialogAnimateTrigger by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                dialogAnimateTrigger = true
                            }
                            val dialogScale by animateFloatAsState(
                                targetValue = if (dialogAnimateTrigger) 1.0f else 0.95f,
                                animationSpec = tween(durationMillis = 180, easing = EaseOutCubic)
                            )
                            val dialogAlpha by animateFloatAsState(
                                targetValue = if (dialogAnimateTrigger) 1.0f else 0.0f,
                                animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing)
                            )

                            AlertDialog(
                                onDismissRequest = { showWhatsNewDialog = false },
                                containerColor = SlateCard,
                                title = {
                                    Text("🚀 What's New in v2.4", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(
                                            text = "The QR Architect workspace has been upgraded with the following improvements:",
                                            color = Color.LightGray,
                                            fontSize = 13.sp
                                        )
                                        listOf(
                                            "🎨 Premium Brand Assets redrawn to align with Cupertino & Linear visual designs.",
                                            "⚙️ Brand identity accessible natively from the Advanced Developer screen.",
                                            "🔍 Scalable vector rendering diagnostics for responsive high-contrast favicons.",
                                            "⚡ Full client-side persistence for onboarding setup sequences."
                                        ).forEach { update ->
                                            Row(verticalAlignment = Alignment.Top) {
                                                Text("• ", color = Color(0xFFD8B4FE), fontSize = 14.sp)
                                                Text(update, color = Color.Gray, fontSize = 12.sp, lineHeight = 16.sp)
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showWhatsNewDialog = false }) {
                                        Text("Dismiss", color = Color(0xFFD8B4FE), fontWeight = FontWeight.Bold)
                                    }
                                },
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = dialogScale
                                        scaleY = dialogScale
                                        alpha = dialogAlpha
                                    }
                                    .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(18.dp))
                            )
                        }
                    }

                    // Walkthrough HUD overlay
                    if (activeWalkthrough && viewModel.currentTab != "library") {
                        OnboardingWalkthroughHUD(
                            step = onboardingStep,
                            onNext = { 
                                if (onboardingStep < 4) {
                                    onboardingStep++
                                } else {
                                    activeWalkthrough = false
                                    viewModel.completeOnboarding()
                                }
                            },
                            onSkip = { 
                                activeWalkthrough = false
                                viewModel.completeOnboarding()
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Render respective tab workspace with smooth, premium transitions
                    AnimatedContent(
                        targetState = viewModel.currentTab,
                        transitionSpec = {
                            val tabsList = listOf("library", "creator", "analytics", "advanced", "brand")
                            val fromIndex = tabsList.indexOf(initialState).coerceAtLeast(0)
                            val toIndex = tabsList.indexOf(targetState).coerceAtLeast(0)
                            val isForward = toIndex >= fromIndex

                            if (isForward) {
                                // Forward Navigation
                                val enterTransition = slideInHorizontally(
                                    animationSpec = tween(durationMillis = 250, easing = CubicBezierEasing(0.25f, 1.0f, 0.5f, 1.0f))
                                ) { 40 } + fadeIn(animationSpec = tween(durationMillis = 250))

                                val exitTransition = slideOutHorizontally(
                                    animationSpec = tween(durationMillis = 250, easing = CubicBezierEasing(0.25f, 1.0f, 0.5f, 1.0f))
                                ) { -40 } + fadeOut(animationSpec = tween(durationMillis = 250), targetAlpha = 0.9f)

                                enterTransition togetherWith exitTransition
                            } else {
                                // Back Navigation
                                val enterTransition = slideInHorizontally(
                                    animationSpec = tween(durationMillis = 220, easing = CubicBezierEasing(0.25f, 1.0f, 0.5f, 1.0f))
                                ) { -40 } + fadeIn(animationSpec = tween(durationMillis = 220), initialAlpha = 0.9f)

                                val exitTransition = slideOutHorizontally(
                                    animationSpec = tween(durationMillis = 220, easing = CubicBezierEasing(0.25f, 1.0f, 0.5f, 1.0f))
                                ) { 40 } + fadeOut(animationSpec = tween(durationMillis = 220))

                                enterTransition togetherWith exitTransition
                            }
                        },
                        label = "ScreenTransitions"
                    ) { targetTab ->
                        when (targetTab) {
                            "library" -> LibraryWorkspace(
                                qrList = qrList,
                                viewModel = viewModel,
                                onQrSelected = { qr -> viewModel.loadForEditing(qr) },
                                elapsedTime = elapsedTime
                            )
                            "creator" -> CreatorWorkshopWorkspace(
                                viewModel = viewModel,
                                onSaveClicked = {
                                    viewModel.saveQrRepresentation()
                                    Toast.makeText(context, "Successfully saved custom QR Architect!", Toast.LENGTH_SHORT).show()
                                }
                            )
                            "brand" -> BrandIdentityWorkspace(
                                viewModel = viewModel
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

        // Overlay the Premium Splash Screen if not fully completed
        if (elapsedTime < 2600) {
            PremiumSplashScreenOverlay(
                elapsedTime = elapsedTime,
                isLibraryLoading = isLibraryLoading
            )
        }
    }
}

@Composable
fun BrandedHeader(onResetDemo: () -> Unit, showWalkthroughHelp: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp, bottom = 12.dp, start = 6.dp, end = 6.dp),
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
    onQrSelected: (QrCode) -> Unit,
    elapsedTime: Int = 2600
) {
    val context = LocalContext.current
    val scanLogs by viewModel.scanLogsState.collectAsState()
    val availableTags = remember(qrList) {
        qrList.mapNotNull { it.tag }.filter { it.isNotBlank() }.distinct()
    }

    LaunchedEffect(viewModel.searchQuery, viewModel.selectedTypeFilter, viewModel.selectedTagFilter, viewModel.selectedSortOrder) {
        viewModel.triggerLibraryRefresh()
    }

    // State valuations at the proper @Composable context levels
    val activeCount = remember(qrList) { qrList.count { it.isActive } }
    
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = remember(hour) {
        when (hour) {
            in 0..11 -> "Good morning 👋"
            in 12..16 -> "Good afternoon 👋"
            else -> "Good evening 👋"
        }
    }

    val favoritesList = remember(qrList, viewModel.favoriteQrIds) {
        qrList.filter { viewModel.favoriteQrIds[it.id] == true }
    }

    val relevantLogs = remember(scanLogs, qrList) {
        scanLogs.take(3)
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // SECTION 1 & 2: Redesigned Premium Header & Greeting
        item {
            StaggeredEntrance(
                delayMs = 0,
                baseTime = 2200,
                currentTime = elapsedTime
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (elapsedTime >= 2600) {
                                            Brush.linearGradient(listOf(Color(0xFFD8B4FE), Color(0xFFC084FC)))
                                        } else {
                                            Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (elapsedTime >= 2600) {
                                    QrArchitectSymbol(
                                        modifier = Modifier.size(28.dp),
                                        primaryColor = Color(0xFF08080D)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "QR Architect",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 25.sp,
                                        color = Color.White,
                                        letterSpacing = (-0.6).sp
                                    )
                                )
                                Text(
                                    text = "Developer platform & campaign tools",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        letterSpacing = 0.2.sp
                                    )
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                Toast.makeText(context, "Walkthrough guide simulation active!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .background(SlateCard, CircleShape)
                                .border(BorderStroke(1.dp, SlateBorder), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.HelpOutline,
                                contentDescription = "Help Guide",
                                tint = Color(0xFFD8B4FE),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )
                    Text(
                        text = "$activeCount active QR triggers",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }

        // SECTION 3: Collapsible Announcement Card
        if (viewModel.showAnnouncement) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0x0EFFFFFF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Magic info icon",
                                    tint = Color(0xFFD8B4FE),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Welcome back",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                )
                                Text(
                                    text = "Search, organize and manage your dynamic QR campaigns programmatically.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.LightGray,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            TextButton(
                                onClick = { viewModel.dismissAnnouncement() },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
                            ) {
                                Text("Dismiss", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    viewModel.setTab("creator")
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFD8B4FE),
                                    contentColor = Color(0xFF08080D)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Continue Setup", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // SECTION 4: Hero Search Bar
        item {
            StaggeredEntrance(
                delayMs = 80,
                baseTime = 2200,
                currentTime = elapsedTime
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp), clip = true),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    OutlinedTextField(
                        value = viewModel.searchQuery,
                        onValueChange = { viewModel.searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Search QR codes, campaigns or destinations...",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search icon",
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (viewModel.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear search",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
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
            }
        }

        // SECTION 5: Quick Stats
        item {
            StaggeredEntrance(
                delayMs = 160,
                baseTime = 2200,
                currentTime = elapsedTime
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val campaignsCount = qrList.mapNotNull { it.tag }.filter { it.isNotBlank() }.distinct().size
                    val totalScans = qrList.sumOf { it.scanCount }
                    val recentLogsCount = scanLogs.size

                    val stats = listOf(
                        Triple("Active Codes", "$activeCount", Icons.Default.QrCode),
                        Triple("Campaigns", "$campaignsCount", Icons.Default.Campaign),
                        Triple("Scans Today", "${totalScans + 12}", Icons.Default.TrendingUp),
                        Triple("Recent Activities", "$recentLogsCount", Icons.Default.History)
                    )

                    stats.forEach { stat ->
                        Card(
                            modifier = Modifier.width(130.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = SlateCard),
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stat.first,
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(
                                        imageVector = stat.third,
                                        contentDescription = stat.first,
                                        tint = Color(0xFFD8B4FE).copy(alpha = 0.7f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stat.second,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // SECTION 6: Encoding Type Horizontal Rows
        item {
            StaggeredEntrance(
                delayMs = 240,
                baseTime = 2200,
                currentTime = elapsedTime
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    Text(
                        text = "Encoding type",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = Color.LightGray,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val filterTypes = listOf("ALL", "URL", "WIFI", "VCARD", "TEXT", "EMAIL", "PHONE")
                        filterTypes.forEach { type ->
                            val isSelected = (type == "ALL" && viewModel.selectedTypeFilter.isEmpty()) || (type == viewModel.selectedTypeFilter)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color(0xFFD8B4FE) else SlateCard)
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            if (isSelected) Color(0xFFD8B4FE) else SlateBorder
                                        ),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { viewModel.selectedTypeFilter = if (type == "ALL") "" else type }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = type,
                                    color = if (isSelected) Color(0xFF08080D) else Color.LightGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // SECTION 7: Campaign Tags Horizontal Chips
        if (availableTags.isNotEmpty()) {
            item {
                StaggeredEntrance(
                    delayMs = 240,
                    baseTime = 2200,
                    currentTime = elapsedTime
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                        Text(
                            text = "Campaign tags",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = Color.LightGray,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            ),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isAllTagsSelected = viewModel.selectedTagFilter.isEmpty()
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isAllTagsSelected) Color(0xFFD8B4FE) else SlateCard)
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            if (isAllTagsSelected) Color(0xFFD8B4FE) else SlateBorder
                                        ),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { viewModel.selectedTagFilter = "" }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "All Tags",
                                    color = if (isAllTagsSelected) Color(0xFF08080D) else Color.LightGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            availableTags.forEach { tag ->
                                val isSelected = viewModel.selectedTagFilter == tag
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) Color(0xFFD8B4FE) else SlateCard)
                                        .border(
                                            BorderStroke(
                                                1.dp,
                                                if (isSelected) Color(0xFFD8B4FE) else SlateBorder
                                            ),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { viewModel.selectedTagFilter = tag }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = tag,
                                        color = if (isSelected) Color(0xFF08080D) else Color.LightGray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // SECTION 8: Favorites Section (horizontally scrolling)
        if (favoritesList.isNotEmpty()) {
            item {
                StaggeredEntrance(
                    delayMs = 240,
                    baseTime = 2200,
                    currentTime = elapsedTime
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Favorites",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            )
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Favorite active",
                                tint = Color(0xFFD8B4FE),
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            favoritesList.forEach { qr ->
                                Card(
                                    modifier = Modifier
                                        .width(160.dp)
                                        .clickable { onQrSelected(qr) },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                                    border = BorderStroke(1.dp, Color(0xFFD8B4FE).copy(alpha = 0.2f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val favBitmap = remember(qr) {
                                            QrGenerator.generateQrBitmap(qr, 60).asImageBitmap()
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.White)
                                                .padding(2.dp)
                                        ) {
                                            Image(
                                                bitmap = favBitmap,
                                                contentDescription = qr.title,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = qr.title,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = qr.type,
                                                color = Color.Gray,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // SECTION 9: Sort and Filter Bar
        item {
            StaggeredEntrance(
                delayMs = 240,
                baseTime = 2200,
                currentTime = elapsedTime
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filteredList.size} Active Codes",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Sort drop button trigger
                        Row(
                            modifier = Modifier
                                .clickable {
                                    viewModel.selectedSortOrder = when (viewModel.selectedSortOrder) {
                                        "DATE_DESC" -> "DATE_ASC"
                                        "DATE_ASC" -> "TITLE_ASC"
                                        "TITLE_ASC" -> "SCANS_DESC"
                                        else -> "DATE_DESC"
                                    }
                                }
                                .clip(RoundedCornerShape(10.dp))
                                .background(SlateCard)
                                .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = when (viewModel.selectedSortOrder) {
                                    "DATE_ASC" -> "Oldest"
                                    "TITLE_ASC" -> "A - Z Index"
                                    "SCANS_DESC" -> "Popularity"
                                    else -> "Newest"
                                },
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort Trigger icon",
                                tint = Color(0xFFD8B4FE),
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        // Filter cog icon
                        IconButton(
                            onClick = {
                                viewModel.selectedTypeFilter = ""
                                viewModel.selectedTagFilter = ""
                                Toast.makeText(context, "Search constraints reset!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .background(SlateCard, RoundedCornerShape(10.dp))
                                .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(10.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings resetting gears",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // SECTION 10: The QR Codes Grid
        if (viewModel.isLibraryLoading) {
            items(3) {
                StaggeredEntrance(
                    delayMs = 240,
                    baseTime = 2200,
                    currentTime = elapsedTime
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            QrCodeSkeletonCard(shimmerBrush = shimmerBrush())
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            QrCodeSkeletonCard(shimmerBrush = shimmerBrush())
                        }
                    }
                }
            }
        } else if (filteredList.isEmpty()) {
            item {
                StaggeredEntrance(
                    delayMs = 240,
                    baseTime = 2200,
                    currentTime = elapsedTime
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(SlateCard, CircleShape)
                                .border(BorderStroke(1.dp, SlateBorder), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = "Empty list",
                                tint = Color.DarkGray,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No QR codes yet",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Create your first dynamic QR code to launch.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.setTab("creator") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD8B4FE),
                                contentColor = Color(0xFF08080D)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Create QR", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        } else {
            val chunkedList = filteredList.chunked(2)
            chunkedList.forEach { rowItems ->
                item {
                    StaggeredEntrance(
                        delayMs = 240,
                        baseTime = 2200,
                        currentTime = elapsedTime
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            rowItems.forEach { qr ->
                                Box(modifier = Modifier.weight(1f)) {
                                    SwipeableQrCardContainer(
                                        onSwipeRight = {
                                            viewModel.toggleFavorite(qr.id)
                                            Toast.makeText(context, if (viewModel.favoriteQrIds[qr.id] == true) "Pinned ${qr.title}!" else "Unpinned ${qr.title}!", Toast.LENGTH_SHORT).show()
                                        },
                                        onSwipeLeft = {
                                            viewModel.deleteQr(qr)
                                            Toast.makeText(context, "Deleted ${qr.title}!", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
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
                            if (rowItems.size < 2) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // SECTION 11: Recent Activity
        item {
            StaggeredEntrance(
                delayMs = 320,
                baseTime = 2200,
                currentTime = elapsedTime
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).padding(top = 16.dp)) {
                    Text(
                        text = "Recent Activity",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (relevantLogs.isNotEmpty()) {
                        relevantLogs.forEach { log ->
                            val matchingTitle = remember(qrList) {
                                qrList.find { it.id == log.qrId }?.title ?: "Campaign QR"
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = SlateCard),
                                border = BorderStroke(1.dp, SlateBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(Color(0x06FFFFFF), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.TrendingUp,
                                                contentDescription = "Event trigger icon",
                                                tint = Color(0xFFD8B4FE),
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "$matchingTitle scanned",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "OS: ${log.deviceOS} | IP: ${log.ipAddress}",
                                                color = Color.Gray,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    Text(
                                        text = formatTimeAgo(log.timestamp),
                                        color = Color.LightGray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    } else {
                        // Seed recent updates as dynamic activities
                        val staticRecentItems = listOf(
                            Pair("Marketing Card scanned", "2 minutes ago"),
                            Pair("HR Badge updated", "1 hour ago"),
                            Pair("WiFi QR created", "Yesterday")
                        )

                        staticRecentItems.forEach { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = SlateCard),
                                border = BorderStroke(1.dp, SlateBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(Color(0x06FFFFFF), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.TrendingUp,
                                                contentDescription = null,
                                                tint = Color(0xFFD8B4FE),
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                        Text(
                                            text = item.first,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Text(
                                        text = item.second,
                                        color = Color.LightGray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatTimeAgo(timeMs: Long): String {
    val diff = System.currentTimeMillis() - timeMs
    return when {
        diff < 60_000 -> "just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        else -> "${diff / 86400_000}d ago"
    }
}

@Composable
fun SwipeableQrCardContainer(
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    content: @Composable () -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    val animatedOffset by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SlateCard)
    ) {
        if (offsetX > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFD8B4FE).copy(alpha = 0.15f))
                    .padding(start = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Favorite, contentDescription = "Favorite", tint = Color(0xFFD8B4FE), modifier = Modifier.size(16.dp))
                    Text("Pin Favorite", color = Color(0xFFD8B4FE), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else if (offsetX < 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                    .padding(end = 14.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Delete Code", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                }
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX > 140) {
                                onSwipeRight()
                            } else if (offsetX < -140) {
                                onSwipeLeft()
                            }
                            offsetX = 0f
                        },
                        onDragCancel = {
                            offsetX = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount).coerceIn(-200f, 200f)
                        }
                    )
                }
        ) {
            content()
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
    val context = LocalContext.current
    var isMenuExpanded by remember { mutableStateOf(false) }
    val isFavorited = viewModel.favoriteQrIds[qr.id] == true

    val qrImageBitmap = remember(qr) {
        QrGenerator.generateQrBitmap(qr, 180).asImageBitmap()
    }

    val timeLabel = remember(qr.creationDate) {
        val diff = System.currentTimeMillis() - qr.creationDate
        when {
            diff < 15 * 60 * 1000 -> "Updated now"
            diff < 24 * 60 * 60 * 1000 -> "Updated today"
            diff < 48 * 60 * 60 * 1000 -> "Created yesterday"
            else -> {
                val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
                "Created " + sdf.format(Date(qr.creationDate))
            }
        }
    }

    val displayUrl = remember(qr.content) {
        qr.content.replace("https://", "").replace("http://", "").replace("www.", "").take(22) + 
            if (qr.content.length > 22) "..." else ""
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
            if (isSelected) Color(0xFFD8B4FE) else SlateBorder
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // First Row: SelectCheckbox and Pin Indicator, and⋮ Menu Trigger
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = onSelectedChange,
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFFD8B4FE),
                            uncheckedColor = Color.Gray,
                            checkmarkColor = SlateDark
                        ),
                        modifier = Modifier.size(24.dp)
                    )

                    if (isFavorited) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Pinned favorite",
                            tint = Color(0xFFD8B4FE),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { isMenuExpanded = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Quick Actions Menu",
                            tint = Color.Gray
                        )
                    }

                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = { isMenuExpanded = false },
                        modifier = Modifier.background(SlateCard).border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(8.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit", color = Color.White) },
                            onClick = {
                                isMenuExpanded = false
                                viewModel.loadForEditing(qr)
                                viewModel.setTab("creator")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate", color = Color.White) },
                            onClick = {
                                isMenuExpanded = false
                                viewModel.duplicateQr(qr)
                                Toast.makeText(context, "Duplicated code copied safely!", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share", color = Color.White) },
                            onClick = {
                                isMenuExpanded = false
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, qr.title)
                                    putExtra(Intent.EXTRA_TEXT, qr.content)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share QR Code info"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Analytics", color = Color.White) },
                            onClick = {
                                isMenuExpanded = false
                                viewModel.selectQrForDetail(qr)
                                viewModel.setTab("analytics")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color.Red) },
                            onClick = {
                                isMenuExpanded = false
                                viewModel.deleteQr(qr)
                                Toast.makeText(context, "Code removed", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Centered QR Code Preview
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(8.dp)
            ) {
                Image(
                    bitmap = qrImageBitmap,
                    contentDescription = "QR preview representation",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title and Shortened URL
            Text(
                text = qr.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = if (qr.type.equals("WIFI", ignoreCase = true)) "Wi-Fi Config" else displayUrl,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.Gray,
                    fontSize = 11.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Scans and updated date row
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = "Scan count indicator",
                        tint = if (qr.isActive) Color(0xFFD8B4FE) else Color.Gray,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "${qr.scanCount} scans",
                        color = if (qr.isActive) Color.White else Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = timeLabel,
                    color = Color.Gray,
                    fontSize = 9.sp,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Micro Status badge and Type Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(if (qr.isActive) Color(0xFFD8B4FE) else Color.Gray, CircleShape)
                    )
                    Text(
                        text = if (qr.isActive) "Active" else "Paused",
                        color = if (qr.isActive) Color(0xFFD8B4FE) else Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x0CFFFFFF))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = qr.type,
                        color = Color.LightGray,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
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
        shape = RoundedCornerShape(18.dp)
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

    var isEntering by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        isEntering = false
    }

    val previewScale by animateFloatAsState(
        targetValue = if (isEntering) 0.98f else 1.0f,
        animationSpec = tween(durationMillis = 300, easing = CubicBezierEasing(0.25f, 1.0f, 0.5f, 1.0f))
    )

    val previewOpacity by animateFloatAsState(
        targetValue = if (isEntering) 0.0f else 1.0f,
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)
    )

    var controlsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300) // Delay editing controls display until transition is complete
        controlsVisible = true
    }

    val controlsAlpha by animateFloatAsState(
        targetValue = if (controlsVisible) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 250, easing = EaseOutCubic)
    )

    val controlsOffsetY by animateDpAsState(
        targetValue = if (controlsVisible) 0.dp else 12.dp,
        animationSpec = tween(durationMillis = 250, easing = EaseOutCubic)
    )

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
        // ---------------- STICKY PREVIEW & ACTION PANEL ----------------
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = previewScale
                    scaleY = previewScale
                    alpha = previewOpacity
                }
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
                .graphicsLayer {
                    alpha = controlsAlpha
                    translationY = controlsOffsetY.toPx()
                }
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
        contentPadding = PaddingValues(bottom = 100.dp),
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
    var testPasswordText by remember { mutableStateOf("") }

    // Navigation and show control toggles
    var showWebhookLogs by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Page Header Title Segment - Sentence Case and SF Pro / Premium Dashboard Style
        item {
            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                Text(
                    text = "Advanced Workspace",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
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
                        fontSize = 14.sp
                    )
                )
            }
        }

        // Section 1: Automation Card
        item {
            val isActive = viewModel.webhookLogs.isNotEmpty()
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Header Row: Icon, Title on Left, Status Badge on Right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0x0AFFFFFF), CircleShape)
                                    .border(BorderStroke(1.dp, Color(0x14FFFFFF)), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ElectricBolt,
                                    contentDescription = null,
                                    tint = Color(0xFFD8B4FE),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Automation",
                                    style = TextStyle(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        letterSpacing = (-0.3).sp
                                    )
                                )
                                Text(
                                    text = "Manage webhooks and external integrations",
                                    style = TextStyle(
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        // Compact Pill Status Badge - Height 28dp, gray background for inactive state
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (isActive) Color(0xFF10B981).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                                .border(
                                    BorderStroke(1.dp, if (isActive) Color(0xFF10B981).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f)),
                                    RoundedCornerShape(999.dp)
                                )
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(if (isActive) Color(0xFF10B981) else Color(0xFF94A3B8), CircleShape)
                                )
                                Text(
                                    text = if (isActive) "Active" else "No activity",
                                    color = if (isActive) Color(0xFF10B981) else Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Description - max 2 lines, 70% opacity
                    Text(
                        text = "Connect QR events to external automation platforms.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Compact Information section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F1020), RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Row 1: Instance Status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Instance Status",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .background(if (isActive) Color(0xFF10B981) else Color(0xFF94A3B8), CircleShape)
                                )
                                Text(
                                    text = if (isActive) "Active" else "No activity",
                                    color = if (isActive) Color(0xFF10B981) else Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Divider(color = Color.White.copy(alpha = 0.04f), modifier = Modifier.fillMaxWidth())

                        // Row 2: Supported Platforms
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Supported Platforms",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("Zapier", "Make", "n8n").forEach { platform ->
                                    Box(
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = platform,
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Webhook Active logs (only visible if View Logs clicked)
                    if (showWebhookLogs) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(Color(0xFF07070D), RoundedCornerShape(12.dp))
                                .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            if (viewModel.webhookLogs.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Log streams are currently clean.",
                                        color = Color.DarkGray,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                    Column {
                                        viewModel.webhookLogs.forEach { log ->
                                            Text(
                                                text = log,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = Color(0xFFD8B4FE),
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Buttons section - 48px high with 14px border radius
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                Toast.makeText(context, "Webhook parameters initialized successfully", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, SlateBorder),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = "Webhook Settings",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = { showWebhookLogs = !showWebhookLogs },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD8B4FE),
                                contentColor = Color(0xFF08080D)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = if (showWebhookLogs) "Hide Logs" else "View Logs",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Section 2: Security Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Header row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0x0AFFFFFF), CircleShape)
                                .border(BorderStroke(1.dp, Color(0x14FFFFFF)), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFFD8B4FE),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Security",
                                style = TextStyle(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    letterSpacing = (-0.3).sp
                                )
                            )
                            Text(
                                text = "Protect dynamic QR assets",
                                style = TextStyle(
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Description text (max 2 lines with 70% opacity)
                    Text(
                        text = "Configure password protection and access controls.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4 Bullet Features listed cleanly
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F1020), RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("•", color = Color(0xFFD8B4FE), fontSize = 14.sp)
                                    Text("Password protection", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("•", color = Color(0xFFD8B4FE), fontSize = 14.sp)
                                    Text("Scan restrictions", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                }
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("•", color = Color(0xFFD8B4FE), fontSize = 14.sp)
                                    Text("Expiration rules", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("•", color = Color(0xFFD8B4FE), fontSize = 14.sp)
                                    Text("Secure access", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Filled lavender button: 48px height, 14px border radius
                    Button(
                        onClick = {
                            Toast.makeText(context, "Password protection suite loaded", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD8B4FE),
                            contentColor = Color(0xFF08080D)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "Manage Security",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Section 3: Developer Tools Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Header Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0x0AFFFFFF), CircleShape)
                                .border(BorderStroke(1.dp, Color(0x14FFFFFF)), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                tint = Color(0xFFD8B4FE),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Developer Tools",
                                style = TextStyle(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    letterSpacing = (-0.3).sp
                                )
                            )
                            Text(
                                text = "Developer integrations and advanced resources",
                                style = TextStyle(
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Description text (max 2 lines with 70% opacity)
                    Text(
                        text = "Access developer utilities and integration resources.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Minimal Navigation List rows (Height 72px)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Row 1: API Credentials
                        DevToolRow(
                            icon = Icons.Default.VpnKey,
                            title = "API Credentials",
                            subtitle = "Manage API keys securely",
                            onClick = {
                                Toast.makeText(context, "API Credentials verified. raw keys are hidden for safety.", Toast.LENGTH_SHORT).show()
                            }
                        )

                        // Row 2: Webhook Logs
                        DevToolRow(
                            icon = Icons.Default.Assignment,
                            title = "Webhook Logs",
                            subtitle = "View integration events",
                            onClick = {
                                showWebhookLogs = !showWebhookLogs
                                Toast.makeText(context, "Webhook logs " + (if (showWebhookLogs) "revealed" else "minimized") + " inside Automation card", Toast.LENGTH_SHORT).show()
                            }
                        )

                        // Row 3: Environment Variables
                        DevToolRow(
                            icon = Icons.Default.Settings,
                            title = "Environment Variables",
                            subtitle = "Configure workspace parameters",
                            onClick = {
                                Toast.makeText(context, "All configuration settings loaded securely via system build keys", Toast.LENGTH_SHORT).show()
                            }
                        )

                        // Row 4: Access Tokens
                        DevToolRow(
                            icon = Icons.Default.Security,
                            title = "Access Tokens",
                            subtitle = "Tokenized client access scopes",
                            onClick = {
                                Toast.makeText(context, "Access scopes active: ['READ_ANALYTICS', 'WRITE_CODES']", Toast.LENGTH_SHORT).show()
                            }
                        )

                        // Row 5: Brand Assets
                        DevToolRow(
                            icon = Icons.Default.Palette,
                            title = "Brand Assets",
                            subtitle = "Logos, typography and colors",
                            onClick = {
                                viewModel.setTab("brand")
                            }
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

@Composable
fun DevToolRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Color(0xFF0F1020), RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFFD8B4FE).copy(alpha = 0.08f), CircleShape)
                    .border(BorderStroke(1.dp, Color(0xFFD8B4FE).copy(alpha = 0.15f)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFD8B4FE),
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(18.dp)
        )
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
    val items = listOf(
        Pair("library", Triple("Library", Icons.Default.LibraryBooks, Icons.Default.LibraryBooks)),
        Pair("creator", Triple("Creator", Icons.Default.ColorLens, Icons.Default.ColorLens)),
        Pair("analytics", Triple("Analytics", Icons.Default.Leaderboard, Icons.Default.Leaderboard)),
        Pair("advanced", Triple("Advanced", Icons.Default.AutoAwesome, Icons.Default.AutoAwesome))
    )

    val activeIndex = items.indexOfFirst {
        activeTab == it.first || (it.first == "advanced" && activeTab == "brand")
    }.coerceAtLeast(0)

    var tabContentWidths by remember { mutableStateOf(mapOf<Int, Int>()) }

    BoxWithConstraints(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .fillMaxWidth()
            .height(78.dp)
            .background(Color(0xFF0D0D14), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
    ) {
        val density = LocalDensity.current
        val activeContentWidthPx = tabContentWidths[activeIndex] ?: 0
        val activeContentWidthDp = if (activeContentWidthPx == 0) 64.dp else with(density) { activeContentWidthPx.toDp() }

        // Width dynamically adapts to icon + label with 18dp padding on each side (36dp total), minimum 72dp
        val targetPillWidth = (activeContentWidthDp + 36.dp).coerceAtLeast(72.dp)
        val animatedPillWidth by animateDpAsState(
            targetValue = targetPillWidth,
            animationSpec = tween(durationMillis = 220, easing = EaseOutCubic),
            label = "PillWidth"
        )

        val usableWidth = maxWidth - 32.dp
        val itemWidth = usableWidth / items.size
        val targetCenter = 16.dp + itemWidth * activeIndex + (itemWidth / 2)
        val animatedCenter by animateDpAsState(
            targetValue = targetCenter,
            animationSpec = tween(durationMillis = 220, easing = EaseOutCubic),
            label = "PillCenter"
        )

        val pillLeft = animatedCenter - (animatedPillWidth / 2)

        // 1. Sliding indicator pill behind the items with premium 10% opacity, 12dp blur shadow
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = pillLeft)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(999.dp),
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.10f),
                    spotColor = Color.Black.copy(alpha = 0.10f)
                )
                .size(width = animatedPillWidth, height = 42.dp)
                .background(Color(0xFFD8B4FE), RoundedCornerShape(999.dp))
        )

        // 2. Navigation Elements in Row to align content perfectly
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { idx, item ->
                val isSelected = idx == activeIndex

                // Subtle icon scale on selection: 1.0 -> 1.05 -> 1.0 (no bounce, 150ms)
                val iconScale by animateFloatAsState(
                    targetValue = 1.0f,
                    animationSpec = if (isSelected) {
                        keyframes {
                            durationMillis = 150
                            1.0f at 0 with LinearEasing
                            1.05f at 75 with FastOutSlowInEasing
                            1.0f at 150 with FastOutSlowInEasing
                        }
                    } else {
                        tween(durationMillis = 150)
                    },
                    label = "IconScale"
                )

                // Smooth color animations
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) SlateDark else Color.White.copy(alpha = 0.6f),
                    animationSpec = tween(durationMillis = 220, easing = EaseOutCubic),
                    label = "TextColor"
                )

                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) SlateDark else Color.White.copy(alpha = 0.6f),
                    animationSpec = tween(durationMillis = 220, easing = EaseOutCubic),
                    label = "IconColor"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onTabSelected(item.first)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Column(
                        modifier = Modifier
                            .onSizeChanged { size ->
                                tabContentWidths = tabContentWidths + (idx to size.width)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) item.second.second else item.second.third,
                            contentDescription = item.second.first,
                            tint = iconColor,
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                                .size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.second.first,
                            fontWeight = FontWeight.Medium,
                            color = textColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 0.2.sp,
                            style = TextStyle(
                                platformStyle = PlatformTextStyle(
                                    includeFontPadding = false
                                )
                            )
                        )
                    }
                }
            }
        }
    }
}

// Helpers for specific fallback items
@Composable
fun SlateLightBorder(): Color = SlateBorder.copy(alpha = 0.5f)

@Composable
fun Modifier.fillPadding(): Modifier = this.padding(vertical = 4.dp)

// ==========================================
// BRAND IDENTITY SYSTEM - QR ARCHITECT BRAND WORKSPACE
// ==========================================

@Composable
fun QrArchitectSymbol(
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFFD8B4FE),
    showGuides: Boolean = false,
    guideColor: Color = Color(0xFFD8B4FE).copy(alpha = 0.35f)
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val sizeMin = minOf(width, height)
        val scale = sizeMin / 100f
        
        // Render technical grids when blueprint mode is on
        if (showGuides) {
            // Precise crosshair alignments
            drawLine(
                color = guideColor.copy(alpha = 0.25f),
                start = Offset(50f * scale, 1f * scale),
                end = Offset(50f * scale, 99f * scale),
                strokeWidth = 0.75f * scale,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f * scale, 4f * scale), 0f)
            )
            drawLine(
                color = guideColor.copy(alpha = 0.25f),
                start = Offset(1f * scale, 50f * scale),
                end = Offset(99f * scale, 50f * scale),
                strokeWidth = 0.75f * scale,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f * scale, 4f * scale), 0f)
            )

            // Inner drafting radius circles
            drawCircle(
                color = guideColor.copy(alpha = 0.12f),
                radius = 45f * scale,
                center = Offset(50f * scale, 50f * scale),
                style = Stroke(width = 1f * scale)
            )
            drawCircle(
                color = guideColor.copy(alpha = 0.08f),
                radius = 25f * scale,
                center = Offset(50f * scale, 50f * scale),
                style = Stroke(width = 1f * scale)
            )
            
            // Grid-level division ticks
            val alignments = listOf(15f, 34f, 49f, 57f, 73f, 85f)
            alignments.forEach { pos ->
                drawLine(
                    color = guideColor.copy(alpha = 0.10f),
                    start = Offset(0f, pos * scale),
                    end = Offset(width, pos * scale),
                    strokeWidth = 0.5f * scale
                )
                drawLine(
                    color = guideColor.copy(alpha = 0.10f),
                    start = Offset(pos * scale, 0f),
                    end = Offset(pos * scale, height),
                    strokeWidth = 0.5f * scale
                )
            }
        }
        
        // --- DRAW MODULE 1 (TOP-LEFT): Outer Finder rounded square frame ---
        val outerStrokeWidth = 6f * scale
        val outerSizeLength = 34f * scale
        val outerCornerRadius = 8f * scale
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(15f * scale, 15f * scale),
            size = Size(outerSizeLength, outerSizeLength),
            cornerRadius = CornerRadius(outerCornerRadius, outerCornerRadius),
            style = Stroke(width = outerStrokeWidth)
        )
        
        // --- DRAW MODULE 2 (TOP-LEFT): Inner Solid square finder node ---
        val innerSizeLength = 14f * scale
        val innerCornerRadius = 3f * scale
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(25f * scale, 25f * scale),
            size = Size(innerSizeLength, innerSizeLength),
            cornerRadius = CornerRadius(innerCornerRadius, innerCornerRadius)
        )
        
        // --- DRAW MODULE 3 (TOP-RIGHT): Beam architectures ---
        // Upper long beam
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(57f * scale, 15f * scale),
            size = Size(28f * scale, 8f * scale),
            cornerRadius = CornerRadius(3.5f * scale, 3.5f * scale)
        )
        // Lower shorter alignment offset block
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(57f * scale, 27f * scale),
            size = Size(18f * scale, 8f * scale),
            cornerRadius = CornerRadius(3.5f * scale, 3.5f * scale)
        )
        // Anchor beacon block
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(79f * scale, 27f * scale),
            size = Size(6f * scale, 8f * scale),
            cornerRadius = CornerRadius(2.5f * scale, 2.5f * scale)
        )
        
        // --- DRAW MODULE 4 (BOTTOM-LEFT): Vertical structural pillars ---
        // Long vertical support column
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(15f * scale, 57f * scale),
            size = Size(8f * scale, 28f * scale),
            cornerRadius = CornerRadius(3.5f * scale, 3.5f * scale)
        )
        // Secondary offset pillar
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(27f * scale, 57f * scale),
            size = Size(8f * scale, 18f * scale),
            cornerRadius = CornerRadius(3.5f * scale, 3.5f * scale)
        )
        // Small base foundation block
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(27f * scale, 79f * scale),
            size = Size(8f * scale, 6f * scale),
            cornerRadius = CornerRadius(2.5f * scale, 2.5f * scale)
        )
        
        // --- DRAW MODULE 5 (BOTTOM-RIGHT): 2x2 cluster of precision nodes ---
        // Top-left precision module
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(57f * scale, 57f * scale),
            size = Size(11f * scale, 11f * scale),
            cornerRadius = CornerRadius(3f * scale, 3f * scale)
        )
        // Top-right architectural outline node
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(73f * scale, 57f * scale),
            size = Size(11f * scale, 11f * scale),
            cornerRadius = CornerRadius(3f * scale, 3f * scale),
            style = Stroke(width = 3.5f * scale)
        )
        // Bottom-left architectural outline node
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(57f * scale, 73f * scale),
            size = Size(11f * scale, 11f * scale),
            cornerRadius = CornerRadius(3f * scale, 3f * scale),
            style = Stroke(width = 3.5f * scale)
        )
        // Bottom-right terminal anchor module
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(73f * scale, 73f * scale),
            size = Size(11f * scale, 11f * scale),
            cornerRadius = CornerRadius(3f * scale, 3f * scale)
        )
        
        // Blueprint precision corner drafting tick marks
        if (showGuides) {
            val tick = 4f * scale
            // Top-left draft corner ticks
            drawLine(guideColor.copy(alpha = 0.5f), Offset(10f*scale, 15f*scale), Offset(10f*scale + tick, 15f*scale), 1f*scale)
            drawLine(guideColor.copy(alpha = 0.5f), Offset(15f*scale, 10f*scale), Offset(15f*scale, 10f*scale + tick), 1f*scale)
            // Bottom-right draft corner ticks
            drawLine(guideColor.copy(alpha = 0.5f), Offset(90f*scale, 85f*scale), Offset(90f*scale - tick, 85f*scale), 1f*scale)
            drawLine(guideColor.copy(alpha = 0.5f), Offset(85f*scale, 90f*scale), Offset(85f*scale, 90f*scale - tick), 1f*scale)
        }
    }
}

@Composable
fun BrandIdentityWorkspace(viewModel: QrViewModel) {
    BrandIdentityWorkspaceRedesigned(viewModel)
}

@Composable
fun BrandIdentityWorkspaceRedesigned(viewModel: QrViewModel) {
    val context = LocalContext.current
    var blueprintGuidesActive by remember { mutableStateOf(true) }
    var selectedBrandColor by remember { mutableStateOf(Color(0xFFD8B4FE)) } // soft lavender default
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp)
    ) {
        // Breadcrumb Navigation Back to Advanced Node
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setTab("advanced") }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back arrow symbol",
                    tint = Color(0xFFD8B4FE),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Back to Advanced Settings",
                    color = Color(0xFFD8B4FE),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.1).sp
                )
            }
        }

        // Screen Header Title & Subtitle Segment
        item {
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(
                    text = "Brand Assets",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontSize = 26.sp,
                        letterSpacing = (-0.8).sp
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Manage logos, colors, typography and exports.",
                    style = TextStyle(
                        color = Color.Gray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    )
                )
            }
        }

        // QUICK OVERVIEW - Equal Height Stat Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val stats = listOf(
                    Pair("Logo Variants", "8"),
                    Pair("Color Palette", "4"),
                    Pair("Typography", "2"),
                    Pair("Export Assets", "16")
                )
                stats.forEach { stat ->
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(78.dp),
                        colors = CardDefaults.cardColors(containerColor = SlateCard),
                        border = BorderStroke(1.dp, SlateBorder),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stat.second,
                                color = Color(0xFFD8B4FE),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stat.first,
                                color = Color.Gray,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // CORE BRAND PRINCIPLES - 2-Column Grid
        item {
            Column {
                Text(
                    text = "Core design principles",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val principles = listOf(
                        Pair("Precision", "Perfect alignment and grid systems"),
                        Pair("Simplicity", "Minimal and memorable design"),
                        Pair("Architecture", "Structured and modular layouts"),
                        Pair("Intelligence", "Clear and intuitive experience"),
                        Pair("Reliability", "Professional and trustworthy"),
                        Pair("Innovation", "Modern and evolving specs")
                    )
                    for (i in principles.indices step 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (j in 0..1) {
                                if (i + j < principles.size) {
                                    val item = principles[i + j]
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(72.dp),
                                        colors = CardDefaults.cardColors(containerColor = SlateCard),
                                        border = BorderStroke(1.dp, SlateBorder),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = item.first,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = item.second,
                                                color = Color.Gray,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // LOGO ASSETS - Horizontally Scrolling Section
        item {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp, start = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Logo Assets",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(
                        modifier = Modifier.clickable { blueprintGuidesActive = !blueprintGuidesActive },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (blueprintGuidesActive) Color(0xFFD8B4FE) else Color.DarkGray, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Drafting Guides",
                            color = if (blueprintGuidesActive) Color.White else Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val logoVariants = listOf(
                        Triple("App Icon", "App Launcher Asset", Color(0xFFD8B4FE)),
                        Triple("Primary Logo", "Wordmark Identity", Color(0xFFD8B4FE)),
                        Triple("Monochrome Logo", "Single-Ink Brandmark", Color.White),
                        Triple("Light Mode Logo", "Light Canvas Target", Color(0xFF08080D))
                    )
                    
                    logoVariants.forEach { item ->
                        Card(
                            modifier = Modifier
                                .width(200.dp)
                                .height(230.dp),
                            colors = CardDefaults.cardColors(containerColor = SlateCard),
                            border = BorderStroke(1.dp, SlateBorder),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                // Symbol Preview window Box
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (item.first == "Light Mode Logo") Color.White else Color(0xFF0D0E15))
                                        .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (item.first == "Primary Logo") {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            QrArchitectSymbol(
                                                modifier = Modifier.size(32.dp),
                                                primaryColor = item.third,
                                                showGuides = blueprintGuidesActive
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "QR ARCH",
                                                color = item.third,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = 0.5.sp
                                            )
                                        }
                                    } else {
                                        QrArchitectSymbol(
                                            modifier = Modifier.size(44.dp),
                                            primaryColor = item.third,
                                            showGuides = blueprintGuidesActive
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Information details
                                Text(
                                    text = item.first,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.second,
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                
                                // Chips
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("SVG", "PNG", "PDF").forEach { format ->
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0x0AFFFFFF), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(format, color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                // Action Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            Toast.makeText(context, "Opened high-res ${item.first} viewer", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f).height(28.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x0CFFFFFF), contentColor = Color.White),
                                        contentPadding = PaddingValues(0.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("View", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = {
                                            Toast.makeText(context, "${item.first} format export queued!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f).height(28.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD8B4FE).copy(alpha = 0.12f), contentColor = Color(0xFFD8B4FE)),
                                        contentPadding = PaddingValues(0.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("Export", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // COLOR PALETTE SECTION
        item {
            Column {
                Text(
                    text = "Color Palette",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, SlateBorder),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Tap any color token to copy its HEX value scale:",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 14.dp)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val colors = listOf(
                                Triple("Primary Lavender", Color(0xFFD8B4FE), "#D8B4FE"),
                                Triple("Background", Color(0xFF08080D), "#08080D"),
                                Triple("Surface", Color(0xFF11111B), "#11111B"),
                                Triple("Secondary Purple", Color(0xFFC084FC), "#C084FC")
                            )
                            colors.forEach { item ->
                                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            val clip = android.content.ClipData.newPlainText("Color Hex", item.third)
                                            clipboardManager.setPrimaryClip(clip)
                                            Toast.makeText(context, "${item.first} (${item.third}) copied!", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(CircleShape)
                                            .background(item.second)
                                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (item.first == "Primary Lavender") {
                                            selectedBrandColor = item.second // let preview bind naturally
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = item.first,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.third,
                                        color = Color.Gray,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // TYPOGRAPHY SPECIFICATIONS
        item {
            Column {
                Text(
                    text = "Typography",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, SlateBorder),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text("Primary Font: Inter", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Secondary Font: SF Pro Link", color = Color.Gray, fontSize = 11.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFD8B4FE).copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("TOKEN VALUE", color = Color(0xFFD8B4FE), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        // Text specimen view box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF080911), RoundedCornerShape(12.dp))
                                .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(12.dp))
                                .padding(18.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "QR Architect",
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Aa Bb Cc 123",
                                    color = Color(0xFFD8B4FE),
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    letterSpacing = 1.5.sp
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        Button(
                            onClick = {
                                Toast.makeText(context, "Font tokens: Inter Medium 14sp, SF Pro Bold 18sp active", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x0CFFFFFF), contentColor = Color.White),
                            border = BorderStroke(1.dp, SlateBorder),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("View Tokens", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ASSET EXPORTS PACKAGES
        item {
            Column {
                Text(
                    text = "Asset Exports",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, SlateBorder),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Download isolated individual variants or full assets archives:",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("SVG", "PNG", "PDF", "ZIP Package").forEach { formatName ->
                                Button(
                                    onClick = {
                                        Toast.makeText(context, "Exporting $formatName payload package", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x0AFFFFFF), contentColor = Color.White),
                                    border = BorderStroke(1.dp, SlateBorder),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(formatName, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Main Corporate Kit Filled lavender style
                        Button(
                            onClick = {
                                Toast.makeText(context, "Brand Kit bundle compilation completed (14.2 MB ZIP exported)!", Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD8B4FE), // Filled lavender style
                                contentColor = Color(0xFF08080D)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download folder package symbol",
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Download Brand Kit",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BrandIdentityWorkspaceOld(viewModel: QrViewModel) {
    val context = LocalContext.current
    var blueprintGuidesActive by remember { mutableStateOf(true) }
    var selectedBrandColor by remember { mutableStateOf(Color(0xFFD8B4FE)) } // soft lavender default
    
    val colorPalettes = listOf(
        Pair("Lavender", Color(0xFFD8B4FE)),
        Pair("Azure Slate", Color(0xFF38BDF8)),
        Pair("Pure White", Color(0xFFFFFFFF)),
        Pair("Coral Sunset", Color(0xFFFB7185)),
        Pair("Emerald Tech", Color(0xFF34D399))
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp)
    ) {
        // SECTION 1: ELEGANT WORKSPACE PROLOGUE HEADER
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("brand_header_card"),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0x0CFFFFFF)).padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(6.dp).background(Color(0xFFD8B4FE), CircleShape))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "BRAND SPECIFICATION MANUAL",
                            color = Color(0xFFD8B4FE),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "QR Architect™ Identity",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "The physical architecture of code. A premium visual system uniting dynamic connectivity blocks with structural engineering blueprints.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Core Brand Values Bento Layout
                    Text(
                        text = "CORE BRAND VALUES",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val row1 = listOf(
                            Pair("Precision", "Perfect grid spacing & alignments"),
                            Pair("Simplicity", "Flat memorable vector geometry")
                        )
                        val row2 = listOf(
                            Pair("Architecture", "Structured blueprint inspiration"),
                            Pair("Intelligence", "Clean analytic core nodes")
                        )
                        val row3 = listOf(
                            Pair("Reliability", "Symmetrical corporate trust balance"),
                            Pair("Innovation", "Evolving code-block aesthetics")
                        )

                        listOf(row1, row2, row3).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                row.forEach { item ->
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = SlateDark.copy(alpha = 0.5f)),
                                        border = BorderStroke(1.dp, SlateBorder)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(
                                                text = item.first,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = item.second,
                                                fontSize = 9.sp,
                                                color = Color.Gray,
                                                lineHeight = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // SECTION 2: APP ICON DEVELOPMENT TOKEN SANDBOX (Requirement 1)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("app_icon_sandbox_card"),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "1. THE APP launch ICON SYMBOL",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Clean centered symbol without textual noise. Perfect geometric layout centered on a luxury workspace.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // The Interactive Icon Showcase Token
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(32.dp)) // iOS / Android Icon Squircle corner
                            .background(SlateDark)
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(32.dp))
                            .padding(24.dp)
                    ) {
                        QrArchitectSymbol(
                            modifier = Modifier.fillMaxSize(),
                            primaryColor = selectedBrandColor,
                            showGuides = blueprintGuidesActive,
                            guideColor = selectedBrandColor.copy(alpha = 0.35f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // CONTROL PANEL
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Blueprint Blueprint Guides",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Switch(
                            checked = blueprintGuidesActive,
                            onCheckedChange = { blueprintGuidesActive = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFD8B4FE),
                                checkedTrackColor = Color(0x33D8B4FE)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "SWAP SPECIFICATION ACCENTS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        colorPalettes.forEach { item ->
                            val isChosen = selectedBrandColor == item.second
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isChosen) Color(0x1AFFFFFF) else Color.Transparent)
                                    .border(BorderStroke(1.dp, if (isChosen) item.second else SlateBorder), RoundedCornerShape(8.dp))
                                    .clickable { selectedBrandColor = item.second }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(modifier = Modifier.size(12.dp).background(item.second, CircleShape))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = item.first.substringBefore(" "), color = Color.White, fontSize = 8.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }

        // SECTION 3: MAIN TYPOGRAPHIC LOGO PRESENTATION (Requirement 2)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("main_logo_card"),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "2. MAIN CORPORATE LOGO",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Horizontal combination of the blueprint symbol with premium technical wordmark typography.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // The Horizontal Typography Logo Core Representation
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SlateDark)
                            .border(BorderStroke(1.dp, SlateBorder))
                            .padding(vertical = 24.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            QrArchitectSymbol(
                                modifier = Modifier.size(36.dp),
                                primaryColor = selectedBrandColor,
                                showGuides = blueprintGuidesActive
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "QR",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = selectedBrandColor,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "ARCHITECT",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    letterSpacing = 4.sp // beautiful modern spacing like Linear/Stripe
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            Toast.makeText(context, "Vector SVG Logo specifications copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x0CFFFFFF), contentColor = Color.White),
                        border = BorderStroke(1.dp, SlateBorder),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Path", modifier = Modifier.size(13.dp))
                            Text("Copy Vector Asset specifications Schema", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // SECTION 4: MONOCHROME VERSIONS (Requirement 3)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("monochrome_card"),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "3. SPECIFICATION MONOCHROME VERSIONS",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Flat monochrome vector values optimized for high contrast printing and architectural plotting.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Monochrome LIGHT (Black Logo on White Background)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .border(BorderStroke(1.dp, Color.LightGray))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "MONO LIGHT (SOLID BLACK)",
                                color = Color.Gray,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            QrArchitectSymbol(
                                modifier = Modifier.size(50.dp),
                                primaryColor = Color.Black,
                                showGuides = false
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Value: #000000",
                                color = Color.DarkGray,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Monochrome DARK (White Logo on Solid Black Background)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF000000))
                                .border(BorderStroke(1.dp, Color(0xFF1F1E2E)))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "MONO DARK (SOLID WHITE)",
                                color = Color.Gray,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            QrArchitectSymbol(
                                modifier = Modifier.size(50.dp),
                                primaryColor = Color.White,
                                showGuides = false
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Value: #FFFFFF",
                                color = Color.LightGray,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // SECTION 5: DARK MODE vs LIGHT MODE THEME SAMPLES (Requirement 4 & 5)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("theme_previews_card"),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "4 & 5. CONTRAST THEME SAMPLES",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Dynamic comparisons displaying the logo inside full application mock frames.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // DARK MODE SPEC SHEET (Lavender on Near-Black #08080D)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF08080D))
                                .border(BorderStroke(1.dp, Color(0xFF161421)))
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("SYSTEM: DARK MODE SPECIFIED", color = Color(0xFFD8B4FE), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0x11FFFFFF)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                    Text("ACCENT #D8B4FE", color = Color.White, fontSize = 7.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                QrArchitectSymbol(modifier = Modifier.size(24.dp), primaryColor = Color(0xFFD8B4FE))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("QR ", color = Color(0xFFD8B4FE), fontSize = 14.sp, fontWeight = FontWeight.Black)
                                Text("ARCHITECT", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            }
                        }

                        // LIGHT MODE SPEC SHEET (Dark Indigo on Pure White #FFFFFF)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .border(BorderStroke(1.dp, Color(0xFFE2E8F0)))
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("SYSTEM: LIGHT MODE SPECIFIED", color = Color(0xFF4F46E5), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0x0C000000)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                    Text("ACCENT #4F46E5", color = Color.DarkGray, fontSize = 7.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                QrArchitectSymbol(modifier = Modifier.size(24.dp), primaryColor = Color(0xFF4F46E5))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("QR ", color = Color(0xFF4F46E5), fontSize = 14.sp, fontWeight = FontWeight.Black)
                                Text("ARCHITECT", color = Color(0xFF1E293B), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            }
                        }
                    }
                }
            }
        }

        // SECTION 6: RESPONSIVE LEGIBILITY & FAVICON WORK (Requirement 6)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("favicon_samples_card"),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "6. SCALABILITY & FAVICON WORK",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "The geometry remains fully crisp and recognizable even when scaled down to a tiny 16×16 pixels favicon container.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // THE BROWSER URL BAR MOCK (Shows Favicon in real-world use)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF181825))
                            .border(BorderStroke(1.dp, SlateBorder))
                    ) {
                        // Browser top tab bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .background(Color(0xFF0F0F15))
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Red, Yellow, Green browser window controls
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFFEF4444), CircleShape))
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFFF59E0B), CircleShape))
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFF10B981), CircleShape))
                            
                            Spacer(modifier = Modifier.width(12.dp))

                            // Active simulated tab
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(Color(0xFF181825))
                                    .height(22.dp)
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 16x16 MICRO FAVICON RENDERING
                                QrArchitectSymbol(
                                    modifier = Modifier.size(10.dp), // Tiny scaled rendering
                                    primaryColor = Color(0xFFD8B4FE),
                                    showGuides = false
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Console Hub - QR Architect",
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }

                        // Browser address URL input field
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF0F0F15))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Secure", tint = Color(0xFF34D399), modifier = Modifier.size(8.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "https://qrarchitect.io/console/overview",
                                color = Color.Gray,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // SIDE-BY-SIDE LEGIBILITY PREVIEWS
                    Text(
                        text = "CANONICAL RESOLUTION RENDER TEST",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        listOf(
                            Pair("16×16 Favicon", 16),
                            Pair("32×32 Widget", 32),
                            Pair("64×64 Navigation", 64),
                            Pair("128×128 Splash", 128)
                        ).forEach { scaleSpec ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size((scaleSpec.second / 1.5).coerceIn(24.0, 72.0).dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SlateDark)
                                        .border(BorderStroke(1.dp, SlateBorder))
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    QrArchitectSymbol(
                                        modifier = Modifier.fillMaxSize(0.7f),
                                        primaryColor = selectedBrandColor,
                                        showGuides = false
                                    )
                                }
                                Text(
                                    text = scaleSpec.first,
                                    color = Color.LightGray,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StaggeredEntrance(
    delayMs: Int,
    baseTime: Int,
    currentTime: Int,
    content: @Composable () -> Unit
) {
    val targetActive = currentTime >= (baseTime + delayMs)
    val alpha by animateFloatAsState(
        targetValue = if (targetActive) 1f else 0f,
        animationSpec = tween(500, easing = EaseOutCubic)
    )
    val offsetY by animateDpAsState(
        targetValue = if (targetActive) 0.dp else 12.dp,
        animationSpec = tween(500, easing = EaseOutCubic)
    )
    
    Box(
        modifier = Modifier
            .graphicsLayer(alpha = alpha)
            .offset(y = offsetY)
    ) {
        content()
    }
}

@Composable
fun PremiumSplashScreenOverlay(
    elapsedTime: Int,
    isLibraryLoading: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ambient")
    val context = LocalContext.current
    
    // Ambient breathing/floating particle background
    val radialGradientAlpha by animateFloatAsState(
        targetValue = if (elapsedTime >= 200) 0.14f else 0f,
        animationSpec = tween(800, easing = EaseInOutSine)
    )
    
    val ambientDx by infiniteTransition.animateFloat(
        initialValue = -40f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(6500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dx"
    )
    val ambientDy by infiniteTransition.animateFloat(
        initialValue = -30f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(8500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dy"
    )
    
    val particles = remember {
        List(14) {
            FloatingParticle(
                xPercent = kotlin.random.Random.nextFloat(),
                yPercent = kotlin.random.Random.nextFloat(),
                size = kotlin.random.Random.nextFloat() * 3f + 1.5f,
                speed = kotlin.random.Random.nextFloat() * 0.04f + 0.015f
            )
        }
    }
    
    val particleTransitionVal by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particles"
    )

    // Phases
    val phase2Scale by animateFloatAsState(
        targetValue = if (elapsedTime >= 400) 1.0f else 0.85f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
    )
    val phase2Opacity by animateFloatAsState(
        targetValue = if (elapsedTime >= 400) 1.0f else 0f,
        animationSpec = tween(650, easing = EaseInOutCubic)
    )

    // Staggered Title and Subtitle text offsets
    val textStagger1 by animateFloatAsState(
        targetValue = if (elapsedTime >= 1000) 1.0f else 0f,
        animationSpec = tween(600, easing = EaseOutCubic)
    )
    val textStagger2 by animateFloatAsState(
        targetValue = if (elapsedTime >= 1200) 1.0f else 0f,
        animationSpec = tween(600, easing = EaseOutCubic)
    )

    // Phase 5 Morph transition
    val morphProgress by animateFloatAsState(
        targetValue = if (elapsedTime >= 2200) 1.0f else 0f,
        animationSpec = tween(400, easing = CubicBezierEasing(0.25f, 1f, 0.5f, 1f))
    )

    // Background overlay alpha (Fades out the dark screen to reveal the library)
    val bgAlpha = 1f - morphProgress

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(alpha = bgAlpha)
            .background(Color(0xFF08080D))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val width = maxWidth
        val height = maxHeight

        // Render ambient effects on canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Breathing radial glow
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFD8B4FE).copy(alpha = radialGradientAlpha), Color.Transparent),
                    center = Offset(size.width / 2f + ambientDx * density, size.height / 2f + ambientDy * density),
                    radius = size.width * 0.75f
                )
            )

            // Dynamic float-up particles
            particles.forEach { particle ->
                val yPos = ((particle.yPercent * size.height) - (particleTransitionVal * particle.speed * 8f)) % size.height
                val finalY = if (yPos < 0f) yPos + size.height else yPos
                drawCircle(
                    color = Color(0xFFD8B4FE).copy(alpha = 0.08f),
                    radius = particle.size,
                    center = Offset(particle.xPercent * size.width, finalY)
                )
            }
        }

        // Shared Element Geometry Flight calculations
        val targetSize = 48.dp
        val startSize = 100.dp
        val currentSize = lerp(startSize, targetSize, morphProgress)
        val currentCorner = lerp(32.dp, 14.dp, morphProgress)

        // Center position vs Header position (interpolated)
        val startX = width / 2f - startSize / 2f
        val startY = height / 2f - startSize / 2f - 60.dp
        
        // Final position of the header icon on-screen
        val targetX = 20.dp
        val targetY = 12.dp

        val currentX = lerp(startX, targetX, morphProgress)
        val currentY = lerp(startY, targetY, morphProgress)

        // Title and Subtitle alpha fades as morph starts
        val titleAlpha = textStagger1 * (1f - morphProgress)
        val subtitleAlpha = textStagger2 * (1f - morphProgress)

        // Sequential mini QR pattern pieces around center (Phase 4)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-60).dp)
                .size(100.dp)
        ) {
            val qrBlocks = listOf(
                Offset(-18f, -18f),
                Offset(106f, -18f),
                Offset(-18f, 106f),
                Offset(106f, 106f),
                Offset(44f, -18f),
                Offset(-18f, 44f)
            )
            qrBlocks.forEachIndexed { idx, offset ->
                val blockAlpha by animateFloatAsState(
                    targetValue = if (elapsedTime >= (1600 + idx * 90)) 0.75f else 0f,
                    animationSpec = tween(400, easing = EaseInOutSine)
                )
                Box(
                    modifier = Modifier
                        .offset(x = offset.x.dp, y = offset.y.dp)
                        .size(11.dp)
                        .graphicsLayer(alpha = blockAlpha * (1f - morphProgress))
                        .background(Color(0xFFD8B4FE), RoundedCornerShape(3.dp))
                )
            }
        }

        // Animated Logo Box
        Box(
            modifier = Modifier
                .offset(x = currentX, y = currentY)
                .size(currentSize)
                .graphicsLayer(
                    scaleX = phase2Scale,
                    scaleY = phase2Scale,
                    alpha = phase2Opacity
                )
                .clip(RoundedCornerShape(currentCorner))
                .background(
                    if (morphProgress < 0.05f) {
                        Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                    } else {
                        Brush.linearGradient(
                            listOf(
                                Color(0xFFD8B4FE).copy(alpha = morphProgress),
                                Color(0xFFC084FC).copy(alpha = morphProgress)
                            )
                        )
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            QrArchitectSymbol(
                modifier = Modifier.fillMaxSize(0.6f + (0.05f * (1f - morphProgress))),
                primaryColor = Color(
                    red = lerp(0xD8 / 255f, 0x08 / 255f, morphProgress),
                    green = lerp(0xB4 / 255f, 0x08 / 255f, morphProgress),
                    blue = lerp(0xFE / 255f, 0x0D / 255f, morphProgress)
                ),
                showGuides = false
            )
        }

        // Title text and Subtitle column below the center Logo
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 65.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "QR Architect",
                color = Color.White,
                fontSize = 32.sp,
                style = TextStyle(
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = (-0.5).sp
                ),
                modifier = Modifier
                    .graphicsLayer(
                        alpha = titleAlpha,
                        translationY = lerp(8f, 0f, textStagger1)
                    )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Manage your dynamic codes",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.1.sp
                ),
                modifier = Modifier
                    .graphicsLayer(
                        alpha = subtitleAlpha,
                        translationY = lerp(8f, 0f, textStagger2)
                    )
            )

            // Extra Loading Section if database initialization takes longer than standard progress (Phase 5 wait)
            val isLoadingVisible = elapsedTime >= 2200 && isLibraryLoading
            val loadingAlpha by animateFloatAsState(
                targetValue = if (isLoadingVisible) 1.0f else 0f,
                animationSpec = tween(400, easing = EaseInOut)
            )

            if (loadingAlpha > 0f) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(top = 32.dp)
                        .graphicsLayer(alpha = loadingAlpha)
                ) {
                    Text(
                        text = "Preparing your workspace...",
                        color = Color(0xFFD8B4FE).copy(alpha = 0.75f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.4.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    PulsingDots()
                }
            }
        }
    }
}

@Composable
fun PulsingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_dots")
    val dotCount = 3
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until dotCount) {
            val pulseDelay = i * 250
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = pulseDelay, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$i"
            )
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.9f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = pulseDelay, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha_$i"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
                    .background(Color(0xFFD8B4FE), CircleShape)
            )
        }
    }
}

class FloatingParticle(val xPercent: Float, val yPercent: Float, val size: Float, val speed: Float)

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + fraction * (stop - start)


