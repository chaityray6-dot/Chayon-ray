package com.example.data.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

data class TtsUiState(
    val isReady: Boolean = false,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val paragraphs: List<String> = emptyList(),
    val currentParagraphIndex: Int = 0,
    val speechRate: Float = 1.0f,
    val selectedLanguageTag: String = "en-US",
    val selectedVoiceName: String = "",
    val availableVoices: List<Voice> = emptyList(),
    val error: String? = null
)

class TtsController(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = TextToSpeech(appContext, this)
    private val _state = MutableStateFlow(TtsUiState())
    val state: StateFlow<TtsUiState> = _state.asStateFlow()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts ?: return
            engine.language = Locale.US
            engine.setSpeechRate(_state.value.speechRate)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    val idx = utteranceId?.removePrefix("para_")?.toIntOrNull() ?: return
                    val current = _state.value
                    if (current.isPlaying && idx + 1 < current.paragraphs.size) {
                        speakParagraph(idx + 1)
                    } else if (idx + 1 >= current.paragraphs.size) {
                        _state.update { it.copy(isPlaying = false, isPaused = false, currentParagraphIndex = 0) }
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _state.update { it.copy(isPlaying = false, error = "TTS playback error") }
                }
            })
            val voices = runCatching { engine.voices?.toList().orEmpty() }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    isReady = true,
                    availableVoices = voices.sortedBy { v -> v.name },
                    selectedVoiceName = engine.voice?.name.orEmpty()
                )
            }
        } else {
            _state.update { it.copy(isReady = false, error = "Android TTS engine unavailable on this device.") }
        }
    }

    fun loadText(text: String) {
        val paras = text.split(Regex("""\n\s*\n|(?<=[.!?।])\s+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        _state.update {
            it.copy(
                paragraphs = if (paras.isEmpty()) listOf("No readable text on this page.") else paras,
                currentParagraphIndex = 0,
                isPlaying = false,
                isPaused = false
            )
        }
    }

    fun playOrResume() {
        val current = _state.value
        if (!current.isReady || current.paragraphs.isEmpty()) return
        speakParagraph(current.currentParagraphIndex.coerceIn(0, current.paragraphs.lastIndex))
    }

    fun pause() {
        tts?.stop()
        _state.update { it.copy(isPlaying = false, isPaused = true) }
    }

    fun stop() {
        tts?.stop()
        _state.update { it.copy(isPlaying = false, isPaused = false, currentParagraphIndex = 0) }
    }

    fun nextParagraph() {
        val current = _state.value
        if (current.paragraphs.isEmpty()) return
        val next = (current.currentParagraphIndex + 1).coerceAtMost(current.paragraphs.lastIndex)
        if (current.isPlaying) {
            speakParagraph(next)
        } else {
            _state.update { it.copy(currentParagraphIndex = next) }
        }
    }

    fun previousParagraph() {
        val current = _state.value
        if (current.paragraphs.isEmpty()) return
        val prev = (current.currentParagraphIndex - 1).coerceAtLeast(0)
        if (current.isPlaying) {
            speakParagraph(prev)
        } else {
            _state.update { it.copy(currentParagraphIndex = prev) }
        }
    }

    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(clamped)
        _state.update { it.copy(speechRate = clamped) }
        if (_state.value.isPlaying) {
            speakParagraph(_state.value.currentParagraphIndex)
        }
    }

    fun setLanguage(locale: Locale) {
        tts?.language = locale
        _state.update { it.copy(selectedLanguageTag = locale.toLanguageTag()) }
    }

    fun setVoice(voice: Voice) {
        tts?.voice = voice
        _state.update { it.copy(selectedVoiceName = voice.name) }
    }

    private fun speakParagraph(index: Int) {
        val paras = _state.value.paragraphs
        if (index !in paras.indices) return
        _state.update { it.copy(currentParagraphIndex = index, isPlaying = true, isPaused = false, error = null) }
        tts?.speak(paras[index], TextToSpeech.QUEUE_FLUSH, null, "para_$index")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
