package com.example.lab6.ai

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LightAiState(
    val sampleCount: Int = 0,
    val prediction: LightPrediction? = null,
    val lastShadowAt: Long? = null,
    val modelError: Boolean = false,
)

class LightAiViewModel(application: Application) : AndroidViewModel(application) {
    private val latestLux = MutableStateFlow<Float?>(null)
    private val mutableState = MutableStateFlow(LightAiState())
    val state = mutableState.asStateFlow()
    private var inference: Job? = null

    fun onLux(lux: Float?) {
        latestLux.value = lux?.takeIf { it.isFinite() && it >= 0f }
    }

    fun start(onActivity: (LightActivity?) -> Unit) {
        if (inference?.isActive == true) return
        inference = viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    ActivityClassifier(getApplication<Application>()).use { classifier ->
                        val window = LightWindow()
                        var sincePrediction = 0
                        var previousActivity: LightActivity? = null
                        var lastShadowAt: Long? = null
                        while (isActive) {
                            delay(50)
                            val lux = latestLux.value
                            if (lux == null) {
                                if (window.count > 0) {
                                    window.clear()
                                    sincePrediction = 0
                                    previousActivity = null
                                    withContext(Dispatchers.Main) {
                                        onActivity(null)
                                        mutableState.value = LightAiState(lastShadowAt = lastShadowAt)
                                    }
                                }
                                continue
                            }
                            window.add(lux)
                            sincePrediction++
                            if (window.count < LIGHT_WINDOW_SIZE) {
                                withContext(Dispatchers.Main) {
                                    mutableState.value = LightAiState(sampleCount = window.count, lastShadowAt = lastShadowAt)
                                }
                                continue
                            }
                            if (sincePrediction < 4) continue
                            sincePrediction = 0
                            val prediction = classifier.classify(window.snapshot())
                            val accepted = prediction.accepted
                            if (accepted == LightActivity.SHADOW && previousActivity != accepted) {
                                lastShadowAt = System.currentTimeMillis()
                            }
                            withContext(Dispatchers.Main) {
                                if (accepted != previousActivity) {
                                    Log.i("LightAI", "Class=${prediction.activity} confidence=${prediction.confidence} accepted=$accepted")
                                    onActivity(accepted)
                                }
                                mutableState.value = LightAiState(window.count, prediction, lastShadowAt)
                            }
                            previousActivity = accepted
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("LightAI", "Local inference failed", error)
                onActivity(null)
                mutableState.value = LightAiState(modelError = true)
            }
        }
    }

    fun stop() {
        inference?.cancel()
        inference = null
        latestLux.value = null
        mutableState.value = LightAiState()
    }
}
