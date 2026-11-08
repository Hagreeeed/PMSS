package com.example.lab6.ai

import android.content.Context
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil

enum class LightActivity(val label: String) {
    STABLE("Стабільне світло"),
    CHANGING("Зміна освітленості"),
    SHADOW("Різке затемнення"),
}

data class LightPrediction(val activity: LightActivity, val probabilities: List<Float>) {
    val confidence: Float get() = probabilities[activity.ordinal]
    val accepted: LightActivity? get() = activity.takeIf { confidence >= 0.8f }
}

class ActivityClassifier(context: Context) : AutoCloseable {
    private val interpreter = Interpreter(
        FileUtil.loadMappedFile(context, "light_activity.tflite"),
        Interpreter.Options().setNumThreads(1),
    )

    init {
        val input = interpreter.getInputTensor(0)
        val output = interpreter.getOutputTensor(0)
        val valid = input.dataType() == DataType.FLOAT32 &&
            input.shape().contentEquals(intArrayOf(1, LIGHT_WINDOW_SIZE)) &&
            output.dataType() == DataType.FLOAT32 &&
            output.shape().contentEquals(intArrayOf(1, LightActivity.entries.size))
        if (!valid) {
            interpreter.close()
            error("Unexpected light model tensor signature")
        }
    }

    fun classify(window: FloatArray): LightPrediction {
        require(window.size == LIGHT_WINDOW_SIZE)
        val output = arrayOf(FloatArray(LightActivity.entries.size))
        interpreter.run(arrayOf(window), output)
        val probabilities = output.single().toList()
        val best = LightActivity.entries.maxBy { probabilities[it.ordinal] }
        return LightPrediction(best, probabilities)
    }

    override fun close() = interpreter.close()
}
