package com.localai.companion

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.localai.companion.data.AppPreferences
import com.localai.companion.engine.CompanionEngine
import com.localai.companion.service.CompanionForegroundService
import com.localai.companion.ui.ChatScreen
import com.localai.companion.ui.CompanionTheme
import com.localai.companion.ui.DarkGraphite
import com.localai.companion.ui.LiveScreen
import com.localai.companion.ui.SettingsScreen
import com.localai.companion.ui.SurfaceGraphite

class MainActivity : ComponentActivity() {

    private val screenCaptureLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (
                result.resultCode == Activity.RESULT_OK &&
                result.data != null &&
                CompanionEngine.liveActive.value
            ) {
                ContextCompat.startForegroundService(
                    this,
                    Intent(
                        this,
                        CompanionForegroundService::class.java
                    ).apply {
                        action = "START_SCREEN_CAPTURE"
                        putExtra(
                            "resultCode",
                            result.resultCode
                        )
                        putExtra(
                            "data",
                            result.data
                        )
                    }
                )
            } else {
                CompanionEngine.setScreenEnabled(false)
            }
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (
                granted &&
                CompanionEngine.liveActive.value
            ) {
                ContextCompat.startForegroundService(
                    this,
                    Intent(
                        this,
                        CompanionForegroundService::class.java
                    ).setAction("START_CAMERA")
                )
            } else {
                CompanionEngine.setCameraEnabled(false)
            }
        }

    private val micPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                CompanionEngine.setLiveActive(true)

                ContextCompat.startForegroundService(
                    this,
                    Intent(
                        this,
                        CompanionForegroundService::class.java
                    ).setAction("START_LIVE")
                )
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        val prefs = AppPreferences(this)

        CompanionEngine.setOverlayEnabled(
            prefs.useOverlay
        )

        CompanionEngine.setSelectedModel(
            prefs.selectedModel
        )

        setContent {
            CompanionTheme {

                var selectedTab by remember {
                    mutableIntStateOf(1)
                }

                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            containerColor = SurfaceGraphite
                        ) {

                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        Icons.Default.Chat,
                                        contentDescription = "Chat"
                                    )
                                },
                                label = {
                                    Text("Chat")
                                },
                                selected = selectedTab == 0,
                                onClick = {
                                    selectedTab = 0
                                }
                            )

                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        Icons.Default.GraphicEq,
                                        contentDescription = "Live"
                                    )
                                },
                                label = {
                                    Text("Live")
                                },
                                selected = selectedTab == 1,
                                onClick = {
                                    selectedTab = 1
                                }
                            )

                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = "Settings"
                                    )
                                },
                                label = {
                                    Text("Settings")
                                },
                                selected = selectedTab == 2,
                                onClick = {
                                    selectedTab = 2
                                }
                            )
                        }
                    }
                ) { padding ->

                    Box(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                            .background(DarkGraphite)
                    ) {

                        when (selectedTab) {

                            0 -> {
                                ChatScreen()
                            }

                            1 -> {
                                LiveScreen(
                                    onRequestScreen = {

                                        if (
                                            CompanionEngine.liveActive.value
                                        ) {
                                            val mediaProjectionManager =
                                                getSystemService(
                                                    MEDIA_PROJECTION_SERVICE
                                                ) as MediaProjectionManager

                                            screenCaptureLauncher.launch(
                                                mediaProjectionManager
                                                    .createScreenCaptureIntent()
                                            )
                                        }
                                    },

                                    onRequestCamera = {
                                        cameraPermissionLauncher.launch(
                                            android.Manifest.permission.CAMERA
                                        )
                                    },

                                    onStartLive = {
                                        micPermissionLauncher.launch(
                                            android.Manifest.permission.RECORD_AUDIO
                                        )
                                    },

                                    onStopLive = {
                                        CompanionEngine
                                            .onStopLiveRequested
                                            ?.invoke()
                                    }
                                )
                            }

                            2 -> {
                                SettingsScreen()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        CompanionEngine.setAppInForeground(
            true
        )
    }

    override fun onStop() {
        super.onStop()

        CompanionEngine.setAppInForeground(
            false
        )
    }
}
