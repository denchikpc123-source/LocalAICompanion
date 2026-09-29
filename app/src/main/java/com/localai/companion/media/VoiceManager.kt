package com.localai.companion.media

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class VoiceManager(
    private val context: Context,
    private val onSpeechResult: (String?) -> Unit,
    private val onTtsStart: (String?) -> Unit,
    private val onTtsDone: (String?) -> Unit,
    private val onTtsError: (String?) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var errorRetryRunnable: Runnable? = null

    @Volatile
    var isListening = false
        private set

    init {
        mainHandler.post {
            initSpeechRecognizer()
            initTts()
        }
    }

    private fun initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false

                val delayMs = when (error) {
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1000L
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> 2000L
                    SpeechRecognizer.ERROR_NO_MATCH -> 150L
                    else -> 500L
                }

                errorRetryRunnable = Runnable {
                    onSpeechResult(null)
                }

                mainHandler.postDelayed(
                    errorRetryRunnable!!,
                    delayMs
                )
            }

            override fun onResults(results: Bundle?) {
                isListening = false

                val matches = results
                    ?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                if (!matches.isNullOrEmpty()) {
                    onSpeechResult(matches[0])
                } else {
                    onSpeechResult(null)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(
                eventType: Int,
                params: Bundle?
            ) {}
        })
    }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale.getDefault())

                tts?.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            mainHandler.post {
                                onTtsStart(utteranceId)
                            }
                        }

                        override fun onDone(utteranceId: String?) {
                            mainHandler.post {
                                onTtsDone(utteranceId)
                            }
                        }

                        override fun onError(utteranceId: String?) {
                            mainHandler.post {
                                onTtsError(utteranceId)
                            }
                        }
                    }
                )
            }
        }
    }

    fun applyTtsSettings(
        rate: Float,
        pitch: Float
    ) {
        mainHandler.post {
            tts?.setSpeechRate(rate)
            tts?.setPitch(pitch)
        }
    }

    fun startListening() {
        mainHandler.post {
            errorRetryRunnable?.let {
                mainHandler.removeCallbacks(it)
            }

            if (isListening) {
                return@post
            }

            if (tts?.isSpeaking == true) {
                tts?.stop()
            }

            val intent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    Locale.getDefault()
                )
                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    false
                )
            }

            try {
                speechRecognizer?.startListening(intent)
                isListening = true
            } catch (e: Exception) {
                isListening = false
                onSpeechResult(null)
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            errorRetryRunnable?.let {
                mainHandler.removeCallbacks(it)
            }

            if (isListening) {
                speechRecognizer?.stopListening()
                isListening = false
            }
        }
    }

    fun speak(
        text: String,
        utteranceId: String
    ) {
        mainHandler.post {
            tts?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                utteranceId
            )
        }
    }

    fun stopTts() {
        mainHandler.post {
            if (tts?.isSpeaking == true) {
                tts?.stop()
            }
        }
    }

    fun destroy() {
        mainHandler.post {
            errorRetryRunnable?.let {
                mainHandler.removeCallbacks(it)
            }

            speechRecognizer?.destroy()
            speechRecognizer = null

            tts?.stop()
            tts?.shutdown()
            tts = null
        }
    }
}
