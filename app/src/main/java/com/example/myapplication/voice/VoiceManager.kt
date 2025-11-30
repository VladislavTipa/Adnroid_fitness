package com.example.myapplication.voice

import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.*

class VoiceManager(private val context: Context, private val callback: VoiceCallback) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Слушаю команды...")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    interface VoiceCallback {
        fun onVoiceCommand(command: String)
        fun onVoiceError(error: String)
        fun onVoiceReady()
        fun onVoiceListening()
    }

    init {
        initializeSpeechRecognizer()
    }

    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) {
                        Log.d("VoiceManager", "Готов к распознаванию речи")
                        callback.onVoiceReady()
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d("VoiceManager", "Начало речи")
                        callback.onVoiceListening()
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // Уровень громкости (можно использовать для визуализации)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {
                        // Не используется
                    }

                    override fun onEndOfSpeech() {
                        Log.d("VoiceManager", "Конец речи")
                    }

                    override fun onError(error: Int) {
                        val errorMessage = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Ошибка аудио"
                            SpeechRecognizer.ERROR_CLIENT -> "Ошибка клиента"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет разрешения"
                            SpeechRecognizer.ERROR_NETWORK -> "Сетевая ошибка"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Таймаут сети"
                            SpeechRecognizer.ERROR_NO_MATCH -> "Команда не распознана"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознаватель занят"
                            SpeechRecognizer.ERROR_SERVER -> "Ошибка сервера"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Таймаут речи"
                            else -> "Неизвестная ошибка: $error"
                        }
                        Log.e("VoiceManager", "Ошибка распознавания: $errorMessage")
                        callback.onVoiceError(errorMessage)
                    }

                    override fun onResults(results: android.os.Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val command = matches[0].lowercase(Locale.getDefault())
                            Log.d("VoiceManager", "Распознанная команда: $command")
                            callback.onVoiceCommand(command)
                        } else {
                            callback.onVoiceError("Команда не распознана")
                        }
                    }

                    override fun onPartialResults(partialResults: android.os.Bundle?) {
                        // Частичные результаты (не используется)
                    }

                    override fun onEvent(eventType: Int, params: android.os.Bundle?) {
                        // События (не используется)
                    }
                })
            }
        } else {
            callback.onVoiceError("Распознавание речи не доступно на этом устройстве")
        }
    }

    fun startListening() {
        try {
            speechRecognizer?.startListening(speechIntent)
            Log.d("VoiceManager", "Начало прослушивания")
        } catch (e: SecurityException) {
            callback.onVoiceError("Нет разрешения на запись аудио")
        } catch (e: Exception) {
            callback.onVoiceError("Ошибка запуска прослушивания: ${e.message}")
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        Log.d("VoiceManager", "Остановка прослушивания")
    }

    fun destroy() {
        speechRecognizer?.destroy()
        Log.d("VoiceManager", "Очистка ресурсов")
    }

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }
}