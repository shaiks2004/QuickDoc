package com.example.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("quickdoc_settings", Context.MODE_PRIVATE) }
    
    var apiKey by remember { mutableStateOf(sharedPrefs.getString("custom_api_key", "") ?: "") }
    var showPassword by remember { mutableStateOf(false) }
    
    var cacheLimit by remember { mutableFloatStateOf(sharedPrefs.getFloat("cache_limit_mb", 250f)) }
    var isContinuousDefault by remember { mutableStateOf(sharedPrefs.getBoolean("continuous_default", true)) }
    var isAiEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("ai_features_enabled", true)) }
    
    var infoMsg by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section: API Key Configuration (Secure override)
            Text(
                text = "SECURITY & AI PROFILES",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Gemini API Configuration",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Enter a custom API Key to override default parameters. This key is stored securely in SharedPreferences.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { 
                            apiKey = it
                            sharedPrefs.edit().putString("custom_api_key", it).apply()
                        },
                        label = { Text("Gemini API Key") },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Visibility"
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_field")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (apiKey.isNotBlank()) {
                                    infoMsg = "Custom API Key Applied successfully!"
                                } else {
                                    infoMsg = "Using default BuildConfig keys"
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Key")
                        }
                        
                        OutlinedButton(
                            onClick = {
                                apiKey = ""
                                sharedPrefs.edit().remove("custom_api_key").apply()
                                infoMsg = "Cleared API Key Override"
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset Default")
                        }
                    }
                }
            }

            // Section: Viewer Performance Defaults
            Text(
                text = "VIEWER PERFORMANCE PREFERENCES",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Default open mode switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Continuous Scroll by Default", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Loads pages in a fluid layout. Turn off for paginated style", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isContinuousDefault,
                            onCheckedChange = {
                                isContinuousDefault = it
                                sharedPrefs.edit().putBoolean("continuous_default", it).apply()
                            },
                            modifier = Modifier.testTag("continuous_scroll_switch")
                        )
                    }

                    Divider()

                    // AI Features Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable AI Assist Features", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Launches summaries, document chatting (RAG), and translations panel", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isAiEnabled,
                            onCheckedChange = {
                                isAiEnabled = it
                                sharedPrefs.edit().putBoolean("ai_features_enabled", it).apply()
                            }
                        )
                    }
                }
            }

            // Section: Storage Cache Optimization
            Text(
                text = "STORAGE CACHE ENGINE",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cache Limit: ${cacheLimit.toInt()} MB", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Controls low-res thumbnail memory buffers", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Slider(
                        value = cacheLimit,
                        onValueChange = {
                            cacheLimit = it
                            sharedPrefs.edit().putFloat("cache_limit_mb", it).apply()
                        },
                        valueRange = 50f..1000f,
                        steps = 19
                    )

                    Button(
                        onClick = {
                            // Clear Cache folders
                            try {
                                context.cacheDir.deleteRecursively()
                                infoMsg = "Document render cache cleared!"
                            } catch (e: Exception) {
                                infoMsg = "Failed to clear directories: ${e.message}"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear All Render Caches", color = Color.White)
                    }
                }
            }

            // Disclaimer / Play Store Compliance Privacy info
            Text(
                text = "PRIVACY & AI COMPLIANCE DISCLOSURE",
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )
            Text(
                text = "QuickDoc Viewer uses the local Android PdfRenderer API for native offline document loading. Optional AI features (Summary, Translation, Chat) process document texts via the Google Gemini API REST endpoints. No original files are uploaded or stored externally.",
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // Info snackbar
        if (infoMsg != null) {
            AlertDialog(
                onDismissRequest = { infoMsg = null },
                confirmButton = {
                    Button(onClick = { infoMsg = null }) {
                        Text("OK")
                    }
                },
                title = { Text("QuickDoc Settings") },
                text = { Text(infoMsg!!) }
            )
        }
    }
}
