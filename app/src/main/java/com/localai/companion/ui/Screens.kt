package com.localai.companion.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.localai.companion.data.AppPreferences
import com.localai.companion.engine.AppState
import com.localai.companion.engine.CompanionEngine
import com.localai.companion.engine.VisionCapability
import com.localai.companion.network.LmStudioClient
import com.localai.companion.service.CompanionForegroundService
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun ChatScreen() {
    val history by CompanionEngine.chatHistory.collectAsState()
    val appState by CompanionEngine.appState.collectAsState()
    val isLive by CompanionEngine.liveActive.collectAsState()

    var text by remember { mutableStateOf("") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPreferences(context) }
    val lmClient = remember { LmStudioClient(prefs) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
            reverseLayout = true
        ) {
            items(history.reversed()) { msg ->
                val isUser = msg.role == "user"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = if (isUser) {
                        Arrangement.End
                    } else {
                        Arrangement.Start
                    }
                ) {
                    Surface(
                        color = if (isUser) {
                            SurfaceGraphite
                        } else {
                            DarkGraphite
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = if (!isUser) {
                            androidx.compose.foundation.BorderStroke(
                                1.dp,
                                SurfaceGraphite
                            )
                        } else {
                            null
                        }
                    ) {
                        Text(
                            text = msg.content,
                            modifier = Modifier.padding(12.dp),
                            color = Color.White
                        )
                    }
                }
            }
        }

        if (appState == AppState.PROCESSING) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = PurpleAccent
            )
        }

        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("Message AI...")
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceGraphite,
                    unfocusedContainerColor = SurfaceGraphite
                ),
                maxLines = 4
            )

            Spacer(
                modifier = Modifier.width(8.dp)
            )

            IconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        val currentText = text
                        text = ""

                        if (
                            isLive &&
                            CompanionEngine.onTextTurnRequested != null
                        ) {
                            CompanionEngine.onTextTurnRequested?.invoke(
                                currentText
                            )
                        } else {
                            scope.launch {
                                if (!CompanionEngine.requestMutex.tryLock()) {
                                    Toast.makeText(
                                        context,
                                        "Request in progress...",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    return@launch
                                }

                                try {
                                    CompanionEngine.addMessage(
                                        "user",
                                        currentText
                                    )

                                    CompanionEngine.setAppState(
                                        AppState.PROCESSING
                                    )

                                    val jsonHistory = JSONArray()

                                    CompanionEngine.chatHistory.value
                                        .dropLast(1)
                                        .forEach { message ->
                                            jsonHistory.put(
                                                JSONObject().apply {
                                                    put(
                                                        "role",
                                                        message.role
                                                    )
                                                    put(
                                                        "content",
                                                        message.content
                                                    )
                                                }
                                            )
                                        }

                                    val result = lmClient.sendChat(
                                        currentText,
                                        jsonHistory,
                                        null,
                                        null
                                    )

                                    if (result.isSuccess) {
                                        CompanionEngine.addMessage(
                                            "assistant",
                                            result.getOrNull() ?: ""
                                        )

                                        CompanionEngine.setAppState(
                                            AppState.IDLE
                                        )
                                    } else {
                                        CompanionEngine.addMessage(
                                            "system",
                                            "Error: ${
                                                result.exceptionOrNull()?.message
                                            }"
                                        )

                                        CompanionEngine.setAppState(
                                            AppState.ERROR
                                        )
                                    }
                                } finally {
                                    CompanionEngine.requestMutex.unlock()
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.background(
                    CyanAccent,
                    RoundedCornerShape(12.dp)
                )
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send",
                    tint = Color.Black
                )
            }
        }
    }
}

@Composable
fun LiveScreen(
    onRequestScreen: () -> Unit,
    onRequestCamera: () -> Unit,
    onStartLive: () -> Unit,
    onStopLive: () -> Unit
) {
    val context = LocalContext.current

    val state by CompanionEngine.appState.collectAsState()
    val isLive by CompanionEngine.liveActive.collectAsState()
    val useCam by CompanionEngine.cameraEnabled.collectAsState()
    val useScr by CompanionEngine.screenEnabled.collectAsState()
    val visionCapability by CompanionEngine.visionCapability.collectAsState()

    val isVisionDisabled =
        visionCapability == VisionCapability.UNSUPPORTED

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (useCam) {
            CameraPreviewView(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color.Black.copy(alpha = 0.7f)
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(220.dp)
            ) {
                LiveOrb(state)
            }

            Spacer(
                modifier = Modifier.height(32.dp)
            )

            Text(
                text = state.name,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(
                modifier = Modifier.height(48.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                FilledIconToggleButton(
                    checked = useCam,
                    onCheckedChange = { checked ->
                        if (checked) {
                            onRequestCamera()
                        } else {
                            CompanionEngine.setCameraEnabled(false)

                            ContextCompat.startForegroundService(
                                context,
                                Intent(
                                    context,
                                    CompanionForegroundService::class.java
                                ).setAction("STOP_CAMERA")
                            )
                        }
                    },
                    enabled = isLive && !isVisionDisabled,
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        checkedContainerColor = CyanAccent,
                        checkedContentColor = Color.Black,
                        containerColor = SurfaceGraphite
                    )
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Camera Context"
                    )
                }

                FilledIconToggleButton(
                    checked = useScr,
                    onCheckedChange = { checked ->
                        if (checked) {
                            onRequestScreen()
                        } else {
                            CompanionEngine.setScreenEnabled(false)

                            ContextCompat.startForegroundService(
                                context,
                                Intent(
                                    context,
                                    CompanionForegroundService::class.java
                                ).setAction("STOP_SCREEN_CAPTURE")
                            )
                        }
                    },
                    enabled = isLive && !isVisionDisabled,
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        checkedContainerColor = PurpleAccent,
                        checkedContentColor = Color.Black,
                        containerColor = SurfaceGraphite
                    )
                ) {
                    Icon(
                        Icons.Default.ScreenShare,
                        contentDescription = "Screen Context"
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(48.dp)
            )

            Button(
                onClick = {
                    if (isLive) {
                        onStopLive()
                    } else {
                        onStartLive()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLive) {
                        ErrorRed
                    } else {
                        CyanAccent
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(56.dp)
            ) {
                Text(
                    text = if (isLive) {
                        "Stop Live"
                    } else {
                        "Start Live Session"
                    },
                    color = Color.Black,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
fun CameraPreviewView(
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER

                CompanionEngine.cameraSurfaceProvider =
                    surfaceProvider

                CompanionEngine.onSurfaceProviderChanged
                    ?.invoke(surfaceProvider)
            }
        },
        onRelease = {
            CompanionEngine.cameraSurfaceProvider = null
            CompanionEngine.onSurfaceProviderChanged
                ?.invoke(null)
        },
        modifier = modifier
            .clip(
                RoundedCornerShape(16.dp)
            )
            .border(
                2.dp,
                SurfaceGraphite,
                RoundedCornerShape(16.dp)
            )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember {
        AppPreferences(context)
    }

    val client = remember {
        LmStudioClient(prefs)
    }

    val scope = rememberCoroutineScope()

    var ip by remember {
        mutableStateOf(prefs.serverIp)
    }

    var port by remember {
        mutableStateOf(prefs.serverPort)
    }

    var models by remember {
        mutableStateOf(listOf<String>())
    }

    var expanded by remember {
        mutableStateOf(false)
    }

    var selectedModel by remember {
        mutableStateOf(prefs.selectedModel)
    }

    val visionCap by CompanionEngine
        .visionCapability
        .collectAsState()

    var tts by remember {
        mutableStateOf(prefs.useTts)
    }

    var overlay by remember {
        mutableStateOf(prefs.useOverlay)
    }

    var ttsRate by remember {
        mutableStateOf(prefs.ttsRate)
    }

    var ttsPitch by remember {
        mutableStateOf(prefs.ttsPitch)
    }

    val overlayPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            val granted =
                Settings.canDrawOverlays(context)

            overlay = granted
            prefs.useOverlay = granted
            CompanionEngine.setOverlayEnabled(granted)
        }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                text = "Local Server",
                style = MaterialTheme.typography.titleMedium,
                color = CyanAccent
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            OutlinedTextField(
                value = ip,
                onValueChange = {
                    ip = it
                    prefs.serverIp = it
                },
                label = {
                    Text("IP Address")
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = port,
                onValueChange = {
                    port = it
                    prefs.serverPort = it
                },
                label = {
                    Text("Port")
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Button(
                onClick = {
                    scope.launch {
                        val result =
                            client.getModels()

                        if (result.isSuccess) {
                            models =
                                result.getOrDefault(
                                    emptyList()
                                )

                            Toast.makeText(
                                context,
                                "Connected! Found ${models.size} models.",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            val err =
                                result.exceptionOrNull()?.message
                                    ?: "Unknown Error"

                            Toast.makeText(
                                context,
                                "Connection Failed: $err",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SurfaceGraphite
                )
            ) {
                Text("Test Connection & Fetch Models")
            }

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = {
                    expanded = !expanded
                }
            ) {
                OutlinedTextField(
                    value = selectedModel,
                    onValueChange = {},
                    readOnly = true,
                    label = {
                        Text("Selected Model")
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults
                            .TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = {
                        expanded = false
                    }
                ) {
                    if (models.isEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text("No models fetched")
                            },
                            onClick = {
                                expanded = false
                            }
                        )
                    } else {
                        models.forEach { model ->
                            DropdownMenuItem(
                                text = {
                                    Text(model)
                                },
                                onClick = {
                                    selectedModel = model
                                    prefs.selectedModel = model
                                    CompanionEngine.setSelectedModel(
                                        model
                                    )
                                    CompanionEngine.setVisionCapability(
                                        VisionCapability.UNKNOWN
                                    )
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = "Vision Capability: ${visionCap.name}",
                style = MaterialTheme.typography.bodySmall,
                color = if (
                    visionCap == VisionCapability.UNSUPPORTED
                ) {
                    ErrorRed
                } else {
                    Color.Gray
                }
            )

            Spacer(
                modifier = Modifier.height(32.dp)
            )

            Text(
                text = "Assistant Preferences",
                style = MaterialTheme.typography.titleMedium,
                color = CyanAccent
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Voice Output (TTS)",
                    modifier = Modifier.weight(1f)
                )

                Switch(
                    checked = tts,
                    onCheckedChange = {
                        tts = it
                        prefs.useTts = it
                    }
                )
            }

            Text(
                text = "Speech Rate: ${"%.1f".format(ttsRate)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Slider(
                value = ttsRate,
                onValueChange = {
                    ttsRate = it
                    prefs.ttsRate = it
                },
                valueRange = 0.5f..2.0f
            )

            Text(
                text = "Speech Pitch: ${"%.1f".format(ttsPitch)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Slider(
                value = ttsPitch,
                onValueChange = {
                    ttsPitch = it
                    prefs.ttsPitch = it
                },
                valueRange = 0.5f..2.0f
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Enable Background Overlay",
                    modifier = Modifier.weight(1f)
                )

                Switch(
                    checked = overlay,
                    onCheckedChange = { checked ->

                        if (
                            checked &&
                            !Settings.canDrawOverlays(context)
                        ) {
                            overlayPermissionLauncher.launch(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse(
                                        "package:${context.packageName}"
                                    )
                                )
                            )
                        } else {
                            overlay = checked
                            prefs.useOverlay = checked

                            CompanionEngine.setOverlayEnabled(
                                checked
                            )
                        }
                    }
                )
            }

            Spacer(
                modifier = Modifier.height(32.dp)
            )

            Button(
                onClick = {
                    CompanionEngine.clearHistory()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ErrorRed
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Clear Chat History",
                    color = Color.White
                )
            }
        }
    }
}
