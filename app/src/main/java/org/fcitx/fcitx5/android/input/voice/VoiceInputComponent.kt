/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import org.fcitx.fcitx5.android.core.CapabilityFlag
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.core.FormattedText
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.core.TextFormatFlag
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.dependency.context
import org.fcitx.fcitx5.android.input.dependency.fcitx
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.ui.main.MainActivity
import org.fcitx.fcitx5.android.utils.InputMethodUtil
import org.fcitx.fcitx5.android.utils.WeakHashSet
import org.fcitx.fcitx5.android.utils.toast
import org.mechdancer.dependency.Dependent
import org.mechdancer.dependency.UniqueComponent
import org.mechdancer.dependency.manager.ManagedHandler
import org.mechdancer.dependency.manager.managedHandler
import timber.log.Timber

class VoiceInputComponent : UniqueComponent<VoiceInputComponent>(), Dependent,
    ManagedHandler by managedHandler(), InputBroadcastReceiver {

    val context by manager.context()
    val service by manager.inputMethodService()
    val fcitx by manager.fcitx()

    private val prefs = AppPrefs.getInstance()

    private val showVoiceInputButton by prefs.keyboard.showVoiceInputButton
    private val preferredVoiceInput by prefs.keyboard.preferredVoiceInput

    fun shouldShowVoiceInput(capFlags: CapabilityFlags): Boolean {
        val canUseSpeechRecognizer = SpeechRecognizer.isRecognitionAvailable(context)
        val hasVoiceSubtype = InputMethodUtil.findVoiceSubtype(preferredVoiceInput) != null
        return showVoiceInputButton && (hasVoiceSubtype || canUseSpeechRecognizer) &&
            !capFlags.has(CapabilityFlag.Password)
    }

    // TODO: switch between "other voice input method" and "SpeechRecognizer"
    val voiceInputCallback = View.OnClickListener {
        val preferredIdx = InputMethodUtil.listVoiceInputMethods().indexOfFirst { (imi, _) ->
            imi.id == preferredVoiceInput
        }
        if (preferredIdx < 0) {
            startListening()
            return@OnClickListener
        }
        val (imi, subtype) = InputMethodUtil.listVoiceInputMethods()[preferredIdx]
        InputMethodUtil.switchInputMethod(service, imi.id, subtype)
    }

    private var languageCode = ""

    override fun onImeUpdate(ime: InputMethodEntry) {
        languageCode = ime.languageCode.replace("_", "-")
    }

    fun Bundle.results() = getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

    fun Bundle.dumpResults() = buildString {
        val results = getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val scores = getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        results?.forEachIndexed { index, string ->
            append(string)
            append("[score=${scores?.get(index)}]")
            if (index > 0) append(", ")
        }
    }

    private var speechRecognizer: SpeechRecognizer? = null

    private fun getSpeechRecognizer(): SpeechRecognizer {
        return speechRecognizer ?: SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(recognitionListener)
        }.also { speechRecognizer = it }
    }

    private var startedListening = false

    private fun buildUnderlineText(str: String): FormattedText {
        return FormattedText(arrayOf(str), intArrayOf(TextFormatFlag.Underline.flag), -1)
    }

    fun interface AudioVolumeListener {
        fun onAudioVolumeChange(listening: Boolean, dB: Float)
    }

    private val audioVolumeListeners = WeakHashSet<AudioVolumeListener>()

    fun addAudioVolumeListener(listener: AudioVolumeListener) {
        audioVolumeListeners.add(listener)
    }

    fun removeAudioVolumeListener(listener: AudioVolumeListener) {
        audioVolumeListeners.remove(listener)
    }

    // TODO: interrupt voice input on keyboard input
    private val recognitionListener by lazy {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle) {
                startedListening = true
                audioVolumeListeners.forEach { it.onAudioVolumeChange(true, 0f) }
                Timber.d("onReadyForSpeech, $params")
            }

            override fun onBeginningOfSpeech() {
                Timber.d("onBeginningOfSpeech")
            }

            override fun onRmsChanged(rmsdB: Float) {
                Timber.d("onRmsChanged: rmsdB=$rmsdB")
                audioVolumeListeners.forEach { it.onAudioVolumeChange(true, rmsdB) }
            }

            override fun onBufferReceived(buffer: ByteArray) {
                /* This would never be called */
            }

            override fun onEndOfSpeech() {
                Timber.d("onEndOfSpeech")
            }

            override fun onError(error: Int) {
                startedListening = false
                audioVolumeListeners.forEach { it.onAudioVolumeChange(false, 0f) }
                Timber.d("onError: $error")
                val message = when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        requestRecordAudioPermission()
                        return
                    }
                    SpeechRecognizer.ERROR_NO_MATCH -> null
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> null
                    SpeechRecognizer.ERROR_CLIENT -> null
                    else -> "onError: $error"
                }
                message?.let { context.toast(it) }
            }

            override fun onPartialResults(partialResults: Bundle) {
                Timber.d("onPartialResults: ${partialResults.dumpResults()}")
                val strings = partialResults.results() ?: return
                // TODO: don't call IMS directly
                service.updateComposingText(buildUnderlineText(strings[0]))
            }

            override fun onResults(results: Bundle) {
                startedListening = false
                audioVolumeListeners.forEach { it.onAudioVolumeChange(false, 0f) }
                Timber.d("onResults: ${results.dumpResults()}")
                val strings = results.results() ?: return
                service.commitText(strings[0])
            }

            override fun onEvent(eventType: Int, params: Bundle) {
                /* unused */
            }
        }
    }

    fun startListening() {
        if (startedListening) {
            startedListening = false
            speechRecognizer?.stopListening()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            requestRecordAudioPermission()
            return
        }

        // reset fcitx preedit to avoid committing stale preedit along with speech text
        service.postFcitxJob { reset() }

        getSpeechRecognizer().startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            // required
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            // optional
            if (languageCode.isNotBlank()) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            }
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        })
    }

    fun onFinishInputView() {
        if (startedListening) {
            speechRecognizer?.cancel()
            startedListening = false
        }
        speechRecognizer?.destroy()
        speechRecognizer = null
        audioVolumeListeners.forEach { it.onAudioVolumeChange(false, 0f) }
    }

    private fun requestRecordAudioPermission() {
        if (onVoicePermissionGranted == null) {
            onVoicePermissionGranted = { startListening() }
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_REQUEST_VOICE_PERMISSION
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    companion object {
        const val ACTION_REQUEST_VOICE_PERMISSION =
            "org.fcitx.fcitx5.android.action.REQUEST_VOICE_PERMISSION"

        // set before launching MainActivity to request RECORD_AUDIO permission,
        // called on the main thread when the permission is granted
        var onVoicePermissionGranted: (() -> Unit)? = null
    }
}
