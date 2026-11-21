package com.example.lab7.data.device

import android.content.Context
import com.example.lab7.domain.LightClassifier
import com.example.lab7.domain.model.LightClass
import com.example.lab7.domain.model.Prediction
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil

/** Модель ЛР6: вхід float32 [1, 20], вихід float32 [1, 3]. */
class TfliteLightClassifier(context: Context) : LightClassifier {
    override val windowSize = 20

    private val interpreter = Interpreter(
        FileUtil.loadMappedFile(context, "light_activity.tflite"),
        Interpreter.Options().setNumThreads(1),
    )

    init {
        val input = interpreter.getInputTensor(0)
        val output = interpreter.getOutputTensor(0)
        val valid = input.dataType() == DataType.FLOAT32 &&
            input.shape().contentEquals(intArrayOf(1, windowSize)) &&
            output.dataType() == DataType.FLOAT32 &&
            output.shape().contentEquals(intArrayOf(1, LightClass.entries.size))
        if (!valid) {
            interpreter.close()
            error("Модель має інші розміри тензорів")
        }
    }

    override fun classify(window: FloatArray): Prediction {
        require(window.size == windowSize)
        val output = arrayOf(FloatArray(LightClass.entries.size))
        interpreter.run(arrayOf(window), output)
        val probabilities = output.single().toList()
        return Prediction(LightClass.entries.maxBy { probabilities[it.ordinal] }, probabilities)
    }

    override fun close() = interpreter.close()
}
