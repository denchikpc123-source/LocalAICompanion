package com.localai.companion.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.localai.companion.data.AppPreferences
import com.localai.companion.engine.AppState
import com.localai.companion.engine.CompanionEngine
import com.localai.companion.engine.VisionCapability
import com.localai.companion.media.CameraManager
import com.localai.companion.media.ScreenCaptureManager
import com.localai.companion.media.VoiceManager
import com.localai.companion.network.LmStudioClient
import com.localai.companion.ui.OverlayView
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CancellationException

class CompanionForegroundService : LifecycleService() {

    private lateinit var prefs: AppPreferences
    private lateinit var lmClient: LmStudioClient
    private lateinit var voiceManager: VoiceManager
    private lateinit var screenManager: ScreenCaptureManager
    private lateinit var cameraManager: CameraManager

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var isOverlayAttached = false

    private var currentTurnJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        prefs = AppPreferences(this)
        lmClient = LmStudioClient(prefs)

        screenManager = ScreenCaptureManager(this)
        cameraManager = CameraManager(this)

        voiceManager = VoiceManager(
            context = this,

            onSpeechResult = { text ->
                handleTurn(text)
            },

            onTtsStart = { utteranceId ->
                val turnId = utteranceId?.toIntOrNull() ?: -1

                if (
                    CompanionEngine.isCurrentTurn(turnId) &&
                    CompanionEngine.liveActive.value
                ) {
                    if (
                        CompanionEngine.appState.value != AppState.LISTENING &&
                        CompanionEngine.appState.value != AppState.PROCESSING
                    ) {
                        CompanionEngine.setAppState(AppState.SPEAKING)
                    }
                }
            },

            onTtsDone = { utteranceId ->
                val turnId = utteranceId?.toIntOrNull() ?: -1

                if (
                    CompanionEngine.isCurrentTurn(turnId) &&
                    CompanionEngine.liveActive.value
                ) {
                    CompanionEngine.setAppState(AppState.LISTENING)
                    voiceManager.startListening()
                }
            },

            onTtsError = { utteranceId ->
                val turnId = utteranceId?.toIntOrNull() ?: -1

                if (
                    CompanionEngine.isCurrentTurn(turnId) &&
                    CompanionEngine.liveActive.value
                ) {
                    CompanionEngine.setAppState(AppState.LISTENING)
                    voiceManager.startListening()
                }
            }
        )

        CompanionEngine.onStartListeningRequested = {
            if (CompanionEngine.liveActive.value) {
                CompanionEngine.setAppState(AppState.LISTENING)
                voiceManager.startListening()
            }
        }

        CompanionEngine.onStopLiveRequested = {
            stopLive()
        }

        CompanionEngine.onTextTurnRequested = { text ->
            voiceManager.stopListening()
            handleTurn(text)
        }

        CompanionEngine.onSurfaceProviderChanged = { provider ->
            cameraManager.setSurfaceProvider(provider)
        }

        createNotificationChannel()
        updateForegroundService()

        lifecycleScope.launch {
            CompanionEngine.isAppInForeground.collect { inForeground ->
                if (
                    !inForeground &&
                    CompanionEngine.liveActive.value &&
                    CompanionEngine.overlayEnabled.value
                ) {
                    showOverlay()
                } else {
                    hideOverlay()
                }
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        super.onStartCommand(intent, flags, startId)

        val action = intent?.action
            ?: return START_NOT_STICKY

        when (action) {

            "START_LIVE" -> {
                CompanionEngine.setLiveActive(true)
                updateForegroundService()

                CompanionEngine.setAppState(AppState.LISTENING)
                voiceManager.startListening()
            }

            "START_SCREEN_CAPTURE" -> {
                val resultCode =
                    intent.getIntExtra("resultCode", 0)

                val data: Intent? =
                    intent.getParcelableExtra("data")

                if (resultCode != 0 && data != null) {
                    CompanionEngine.setScreenEnabled(true)
                    updateForegroundService()

                    val mediaProjectionManager =
                        getSystemService(
                            Context.MEDIA_PROJECTION_SERVICE
                        ) as MediaProjectionManager

                    val projection =
                        mediaProjectionManager.getMediaProjection(
                            resultCode,
                            data
                        )

                    screenManager.start(projection)
                }
            }

            "STOP_SCREEN_CAPTURE" -> {
                CompanionEngine.setScreenEnabled(false)

                screenManager.stop()
                updateForegroundService()
            }

            "START_CAMERA" -> {
                CompanionEngine.setCameraEnabled(true)
                updateForegroundService()

                cameraManager.startCamera(
                    this,
                    CompanionEngine.cameraSurfaceProvider
                )
            }

            "STOP_CAMERA" -> {
                CompanionEngine.setCameraEnabled(false)

                cameraManager.stopCamera()
                updateForegroundService()
            }
        }

        return START_NOT_STICKY
    }

    private fun updateForegroundService() {
        var types = 0

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            types =
                types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }

        if (
            CompanionEngine.cameraEnabled.value &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) {
            types =
                types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }

        if (
            CompanionEngine.screenEnabled.value &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        ) {
            types =
                types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        }

        val notification = NotificationCompat.Builder(
            this,
            "LIVE_AI"
        )
            .setContentTitle("AI Companion Active")
            .setContentText("Runtime engine is running")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                1,
                notification,
                types
            )
        } else {
            startForeground(1, notification)
        }
    }

    private fun handleTurn(text: String?) {
        if (text.isNullOrBlank()) {
            if (CompanionEngine.liveActive.value) {
                CompanionEngine.setAppState(AppState.LISTENING)
                voiceManager.startListening()
            } else {
                CompanionEngine.setAppState(AppState.IDLE)
            }

            return
        }

        if (currentTurnJob?.isActive == true) {
            Log.w(
                "CompanionService",
                "Job already active. Dropping overlapping turn."
            )
            return
        }

        currentTurnJob = lifecycleScope.launch {

            if (!CompanionEngine.requestMutex.tryLock()) {
                Log.w(
                    "CompanionService",
                    "Request in progress (Mutex locked). Dropping overlapping turn."
                )
                return@launch
            }

            var camBmp: Bitmap? = null
            var scrBmp: Bitmap? = null

            try {
                CompanionEngine.setAppState(AppState.PROCESSING)

                CompanionEngine.addMessage(
                    "user",
                    text
                )

                val turnId =
                    CompanionEngine.startNewTurn()

                voiceManager.applyTtsSettings(
                    prefs.ttsRate,
                    prefs.ttsPitch
                )

                camBmp =
                    if (CompanionEngine.cameraEnabled.value) {
                        cameraManager.takeSnapshot()
                    } else {
                        null
                    }

                scrBmp =
                    if (CompanionEngine.screenEnabled.value) {
                        screenManager.getSnapshot()
                    } else {
                        null
                    }

                val history = JSONArray()

                CompanionEngine.chatHistory.value
                    .filter { message ->
                        message.role == "user" || message.role == "assistant"
                    }
                    .dropLast(1)
                    .forEach { message ->

                        history.put(
                            JSONObject().apply {
                                put("role", message.role)
                                put("content", message.content)
                            }
                        )
                    }

                val result = lmClient.sendChat(
                    text,
                    history,
                    camBmp,
                    scrBmp
                )

                if (result.isSuccess) {

                    if (
                        camBmp != null ||
                        scrBmp != null
                    ) {
                        CompanionEngine.setVisionCapability(
                            VisionCapability.SUPPORTED
                        )
                    }

                    val reply =
                        result.getOrNull() ?: ""

                    CompanionEngine.addMessage(
                        "assistant",
                        reply
                    )

                    if (
                        prefs.useTts &&
                        CompanionEngine.liveActive.value
                    ) {
                        CompanionEngine.setAppState(
                            AppState.SPEAKING
                        )

                        voiceManager.speak(
                            reply,
                            turnId.toString()
                        )
                    } else {
                        CompanionEngine.setAppState(
                            AppState.IDLE
                        )

                        if (
                            CompanionEngine.liveActive.value
                        ) {
                            CompanionEngine.setAppState(
                                AppState.LISTENING
                            )

                            voiceManager.startListening()
                        }
                    }

                } else {

                    if (isActive) {
                        val errorMsg =
                            result.exceptionOrNull()?.message
                                ?: "Unknown API Error"

                        val lowerError =
                            errorMsg.lowercase()

                        if (
                            camBmp != null ||
                            scrBmp != null
                        ) {
                            if (
                                lowerError.contains("vision") ||
                                lowerError.contains("image") ||
                                lowerError.contains("multimodal")
                            ) {
                                CompanionEngine.setVisionCapability(
                                    VisionCapability.UNSUPPORTED
                                )
                            } else {
                                CompanionEngine.setVisionCapability(
                                    VisionCapability.UNKNOWN
                                )
                            }
                        }

                        CompanionEngine.addMessage(
                            "system",
                            "Error: $errorMsg"
                        )

                        CompanionEngine.setAppState(
                            AppState.ERROR
                        )
                    }
                }

            } catch (e: CancellationException) {
                Log.i(
                    "CompanionService",
                    "AI Request Cancelled"
                )

                throw e

            } catch (e: Exception) {

                if (isActive) {
                    CompanionEngine.addMessage(
                        "system",
                        "Error: ${e.message}"
                    )

                    CompanionEngine.setAppState(
                        AppState.ERROR
                    )
                }

            } finally {
                camBmp?.recycle()
                scrBmp?.recycle()

                CompanionEngine.requestMutex.unlock()
            }
        }
    }

    private fun stopLive() {
        CompanionEngine.startNewTurn()

        currentTurnJob?.cancel()
        currentTurnJob = null

        CompanionEngine.setLiveActive(false)
        CompanionEngine.setAppState(AppState.IDLE)

        voiceManager.stopListening()
        voiceManager.stopTts()

        CompanionEngine.setCameraEnabled(false)
        cameraManager.stopCamera()

        CompanionEngine.setScreenEnabled(false)
        screenManager.stop()

        hideOverlay()

        ServiceCompat.stopForeground(
            this,
            ServiceCompat.STOP_FOREGROUND_REMOVE
        )

        stopSelf()
    }

    @SuppressLint("InflateParams")
    private fun showOverlay() {
        if (isOverlayAttached) {
            return
        }

        windowManager =
            getSystemService(WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },

            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,

            PixelFormat.TRANSLUCENT
        ).apply {
            gravity =
                Gravity.TOP or Gravity.START

            x = 50
            y = 200
        }

        overlayView = ComposeView(this).apply {
            setContent {
                OverlayView()
            }
        }

        try {
            windowManager?.addView(
                overlayView,
                params
            )

            isOverlayAttached = true

        } catch (e: Exception) {
            Log.e(
                "CompanionService",
                "Failed to add overlay",
                e
            )
        }
    }

    private fun hideOverlay() {
        try {
            if (
                isOverlayAttached &&
                overlayView != null
            ) {
                windowManager?.removeView(
                    overlayView
                )

                isOverlayAttached = false
                overlayView = null
            }
        } catch (e: Exception) {
            Log.e(
                "CompanionService",
                "Failed to remove overlay",
                e
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "LIVE_AI",
                "Live AI Companion",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(
                NotificationManager::class.java
            )?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        CompanionEngine.onStartListeningRequested = null
        CompanionEngine.onStopLiveRequested = null
        CompanionEngine.onTextTurnRequested = null
        CompanionEngine.onSurfaceProviderChanged = null

        currentTurnJob?.cancel()

        voiceManager.destroy()
        cameraManager.stopCamera()
        screenManager.stop()
        hideOverlay()
    }
}
