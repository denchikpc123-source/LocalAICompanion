package com.localai.companion.engine

import androidx.camera.core.Preview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicInteger

enum class AppState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR,
    OFFLINE
}

enum class VisionCapability {
    SUPPORTED,
    UNSUPPORTED,
    UNKNOWN
}

data class ChatMessage(
    val role: String,
    val content: String
)

object CompanionEngine {
    private val _appState = MutableStateFlow(AppState.IDLE)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    private val _liveActive = MutableStateFlow(false)
    val liveActive: StateFlow<Boolean> = _liveActive.asStateFlow()

    private val _cameraEnabled = MutableStateFlow(false)
    val cameraEnabled: StateFlow<Boolean> = _cameraEnabled.asStateFlow()

    private val _screenEnabled = MutableStateFlow(false)
    val screenEnabled: StateFlow<Boolean> = _screenEnabled.asStateFlow()

    private val _overlayEnabled = MutableStateFlow(false)
    val overlayEnabled: StateFlow<Boolean> = _overlayEnabled.asStateFlow()

    private val _ttsEnabled = MutableStateFlow(true)
    val ttsEnabled: StateFlow<Boolean> = _ttsEnabled.asStateFlow()

    private val _selectedModel = MutableStateFlow("")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _visionCapability = MutableStateFlow(VisionCapability.UNKNOWN)
    val visionCapability: StateFlow<VisionCapability> = _visionCapability.asStateFlow()

    private val _isAppInForeground = MutableStateFlow(true)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    val requestMutex = Mutex()

    private val currentTurnId = AtomicInteger(0)

    var onStartListeningRequested: (() -> Unit)? = null
    var onStopLiveRequested: (() -> Unit)? = null
    var onTextTurnRequested: ((String) -> Unit)? = null

    var cameraSurfaceProvider: Preview.SurfaceProvider? = null

    var onSurfaceProviderChanged: ((Preview.SurfaceProvider?) -> Unit)? = null

    fun setAppState(state: AppState) {
        _appState.value = state
    }

    fun setLiveActive(isActive: Boolean) {
        _liveActive.value = isActive
    }

    fun setCameraEnabled(isEnabled: Boolean) {
        _cameraEnabled.value = isEnabled
    }

    fun setScreenEnabled(isEnabled: Boolean) {
        _screenEnabled.value = isEnabled
    }

    fun setOverlayEnabled(isEnabled: Boolean) {
        _overlayEnabled.value = isEnabled
    }

    fun setTtsEnabled(isEnabled: Boolean) {
        _ttsEnabled.value = isEnabled
    }

    fun setSelectedModel(model: String) {
        _selectedModel.value = model
    }

    fun setVisionCapability(capability: VisionCapability) {
        _visionCapability.value = capability
    }

    fun setAppInForeground(inForeground: Boolean) {
        _isAppInForeground.value = inForeground
    }

    fun addMessage(role: String, content: String) {
        val list = _chatHistory.value.toMutableList()
        list.add(ChatMessage(role, content))
        _chatHistory.value = list
    }

    fun clearHistory() {
        _chatHistory.value = emptyList()
    }

    fun startNewTurn(): Int {
        return currentTurnId.incrementAndGet()
    }

    fun isCurrentTurn(turnId: Int): Boolean {
        return currentTurnId.get() == turnId
    }
}
