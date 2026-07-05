package com.example.ui.viewer

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.AnnotationEntity
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    viewModel: ViewerViewModel,
    uri: Uri,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val context = LocalContext.current

    // Trigger loading file on active entry
    LaunchedEffect(uri) {
        viewModel.loadDocument(uri)
    }

    // Scroll listener for lazy loading adjacent pages
    LaunchedEffect(listState.firstVisibleItemIndex) {
        viewModel.onPageChanged(listState.firstVisibleItemIndex)
    }

    // Modal state controllers
    var showWatermarkDialog by remember { mutableStateOf(false) }
    var showTranslateDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    
    var watermarkText by remember { mutableStateOf("") }
    var compressionValue by remember { mutableFloatStateOf(100f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.fileName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (state.pageCount > 0) "Page ${state.currentPageIndex + 1} of ${state.pageCount}" else "Viewer",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Search in Doc
                    IconButton(onClick = { /* Search dialog */ }) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search text")
                    }
                    // Night Mode
                    IconButton(onClick = { viewModel.toggleNightMode() }) {
                        Icon(
                            imageVector = if (state.isNightMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Theme Toggle"
                        )
                    }
                    // Edit Menu (Split / Compress)
                    IconButton(onClick = { showSaveDialog = true }) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = "Edit Actions")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            // Document life-cycle Action Toolbar (highlight, pen drawing, delete, signs)
            Surface(
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.toggleContinuousScroll() }) {
                        Icon(
                            imageVector = if (state.isContinuousScroll) Icons.Default.Layers else Icons.Default.LayersClear,
                            contentDescription = "Scroll Mode",
                            tint = if (state.isContinuousScroll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    IconButton(onClick = { 
                        // Simulate adding highlight annotation
                        val annotation = AnnotationEntity(
                            fileUri = uri.toString(),
                            pageIndex = state.currentPageIndex,
                            type = "HIGHLIGHT",
                            color = 0x55FFFF00.toInt(),
                            thickness = 25f,
                            pointsJson = "100,150,500,150",
                            text = null,
                            x = 100f,
                            y = 150f,
                            width = 400f,
                            height = 30f
                        )
                        viewModel.addAnnotation(annotation)
                    }) {
                        Icon(imageVector = Icons.Default.BorderColor, contentDescription = "Highlight Text")
                    }

                    IconButton(onClick = { 
                        // Simulated signature pad interaction
                        viewModel.signDocument(150f, 400f)
                    }) {
                        Icon(imageVector = Icons.Default.Gesture, contentDescription = "Place Signature")
                    }

                    IconButton(onClick = { viewModel.clearAnnotationsOnCurrentPage() }) {
                        Icon(imageVector = Icons.Default.LayersClear, contentDescription = "Clear Annotations")
                    }

                    IconButton(onClick = { viewModel.deleteCurrentPage() }) {
                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Delete Current Page", tint = Color.Red)
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.toggleAiPanel() },
                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                text = { Text("AI Assistant") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(bottom = 60.dp)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main content body (Conditional rendering based on file type)
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (state.fileType) {
                    "pdf" -> {
                        PdfPagesViewer(
                            state = state,
                            listState = listState,
                            viewModel = viewModel,
                            scale = scale,
                            onScaleChanged = { scale = it },
                            offset = offset,
                            onOffsetChanged = { offset = it }
                        )
                    }
                    "image" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(if (state.isNightMode) Color.Black else Color.LightGray),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = state.documentUri,
                                contentDescription = "Image Document",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                    "text", "office" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .background(if (state.isNightMode) Color.Black else Color.White)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = state.textContent,
                                color = if (state.isNightMode) Color.White else Color.Black,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                    "zip" -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Text(
                                    text = "Files inside ZIP",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            itemsIndexed(state.zipEntries) { index, entry ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.extractAndLoadZipEntry(uri, entry)
                                        },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(imageVector = Icons.Default.InsertDriveFile, contentDescription = null)
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(text = entry, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Top-left Debug Performance Instrumentation Overlay
            PerformanceOverlay(metrics = state.metrics)

            // Undo Toast Banner (Countdown trash pattern)
            AnimatedVisibility(
                visible = state.undoAvailable,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 84.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = state.undoMessage,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            fontSize = 13.sp
                        )
                        Button(
                            onClick = { viewModel.executeUndo() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Undo", fontSize = 12.sp)
                        }
                    }
                }
            }

            // AI Bottom Sheet/Sidebar Panel
            if (state.isAiPanelExpanded) {
                AiAssistantPanel(
                    state = state,
                    viewModel = viewModel,
                    onDismiss = { viewModel.toggleAiPanel() },
                    onTranslateRequest = { showTranslateDialog = true }
                )
            }
        }
    }

    // Save/Modify Document Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Compile and Compress PDF") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Apply vector layers, compression settings, and save a fully flattened copy.")
                    
                    OutlinedTextField(
                        value = watermarkText,
                        onValueChange = { watermarkText = it },
                        label = { Text("Watermark Text (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Image Compression")
                            Text("${compressionValue.toInt()}%")
                        }
                        Slider(
                            value = compressionValue,
                            onValueChange = { compressionValue = it },
                            valueRange = 20f..100f
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showSaveDialog = false
                    viewModel.applyWatermarkAndCompress(
                        watermark = watermarkText.ifBlank { null },
                        compressionQuality = compressionValue.toInt()
                    )
                }) {
                    Text("Apply & Flatten")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Translation Language Selection Dialog
    if (showTranslateDialog) {
        AlertDialog(
            onDismissRequest = { showTranslateDialog = false },
            title = { Text("Translate Document") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Translate the document contents instantly using Gemini AI.")
                    val languages = listOf("Spanish", "French", "German", "Japanese", "Chinese")
                    languages.forEach { lang ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.translateDocument(lang)
                                    showTranslateDialog = false
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text(text = lang, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTranslateDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun PdfPagesViewer(
    state: ViewerUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    viewModel: ViewerViewModel,
    scale: Float,
    onScaleChanged: (Float) -> Unit,
    offset: Offset,
    onOffsetChanged: (Offset) -> Unit
) {
    val stateTransform = rememberTransformableState { zoomChange, offsetChange, _ ->
        onScaleChanged((scale * zoomChange).coerceIn(1f, 4f))
        onOffsetChanged(offset + offsetChange)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(if (state.isNightMode) Color.DarkGray else Color.Gray)
            .transformable(stateTransform)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            ),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed((0 until state.pageCount).toList()) { index, pageIdx ->
            // Trigger lazy render when visible
            viewModel.renderPageIfNeeded(pageIdx)

            val bitmap = state.pdfPageBitmaps[pageIdx]
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.707f) // Standard letter aspect ratio
                    .testTag("pdf_page_$pageIdx"),
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Page ${index + 1}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PerformanceOverlay(metrics: PerformanceMetrics) {
    Box(
        modifier = Modifier
            .padding(16.dp)
            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = "⚡ QUICKDOC INSTRUMENTATION", color = Color.Green, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(text = "File Open Time: ${metrics.fileOpenTimeMs}ms", color = Color.White, fontSize = 10.sp)
            Text(text = "First Page Render: ${metrics.firstPageRenderTimeMs}ms", color = Color.White, fontSize = 10.sp)
            Text(text = "Memory Footprint: ${metrics.memoryUsedMb}MB", color = Color.White, fontSize = 10.sp)
            if (metrics.aiResponseLatencyMs > 0) {
                Text(text = "AI Response Latency: ${metrics.aiResponseLatencyMs}ms", color = Color.Cyan, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun AiAssistantPanel(
    state: ViewerUiState,
    viewModel: ViewerViewModel,
    onDismiss: () -> Unit,
    onTranslateRequest: () -> Unit
) {
    var queryText by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss)
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxHeight(0.75f)
                .fillMaxWidth()
                .clickable(enabled = false) {}, // Prevent dismiss click propagation
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(text = "QuickDoc AI Assistant", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close AI Panel")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Feature Action Hub Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.generateSummary() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Summarize", fontSize = 12.sp)
                    }
                    Button(
                        onClick = onTranslateRequest,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Translate", fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            viewModel.triggerSmartRename { suggestedName ->
                                viewModel.renameActiveFile(suggestedName)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text("Smart Rename", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Output / Conversation Workspace
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (state.isAiLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Display summary if generated
                            if (state.aiSummary.isNotEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(text = "Document Summary", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(text = state.aiSummary, fontSize = 13.sp, lineHeight = 18.sp)
                                    }
                                }
                            }

                            // Display translations if generated
                            if (state.translatedText.isNotEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(text = "AI Translation", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(text = state.translatedText, fontSize = 13.sp, lineHeight = 18.sp)
                                    }
                                }
                            }

                            // Chat message loops
                            state.chatHistory.forEach { msg ->
                                val bubbleColor = if (msg.sender == "user") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            start = if (msg.sender == "user") 48.dp else 0.dp,
                                            end = if (msg.sender == "user") 0.dp else 48.dp
                                        )
                                        .background(bubbleColor, RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = msg.text,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp,
                                            color = if (msg.sender == "user") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (msg.citations.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Citations: Pages ${msg.citations.joinToString()}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Send text question bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask document any question...") },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp)
                    )
                    IconButton(
                        onClick = {
                            if (queryText.isNotBlank()) {
                                viewModel.sendChatMessage(queryText)
                                queryText = ""
                            }
                        },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                    }
                }
            }
        }
    }
}
