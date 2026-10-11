package com.neuralhud.mobile

import android.content.Context
import kotlin.math.exp
import kotlin.random.Random

class LocalNeuralModel(context: Context) {
    private val prefs = context.getSharedPreferences("neural_model", Context.MODE_PRIVATE)
    private val inputSize = 20
    private val hidden1 = 56
    private val hidden2 = 56
    private val outputSize = 6
    private val w1 = FloatArray(inputSize * hidden1)
    private val b1 = FloatArray(hidden1)
    private val w2 = FloatArray(hidden1 * hidden2)
    private val b2 = FloatArray(hidden2)
    private val w3 = FloatArray(hidden2 * outputSize)
    private val b3 = FloatArray(outputSize)
    private val labels = arrayOf("Aplicativo", "Pesquisa", "Arquivo", "HUD", "Aprendizado", "Possível problema")

    init {
        load()
        if (!prefs.contains("w1")) {
            val r = Random(19842)
            for (i in w1.indices) w1[i] = r.nextFloat() * 0.12f - 0.06f
            for (i in w2.indices) w2[i] = r.nextFloat() * 0.12f - 0.06f
            for (i in w3.indices) w3[i] = r.nextFloat() * 0.12f - 0.06f
            val samples = listOf(
                "abrir aplicativo tela interface" to 0,
                "google pesquisa web internet consulta" to 1,
                "arquivo documento apk zip texto" to 2,
                "hud esfera flutuante painel opacidade" to 3,
                "aprender feedback treino modelo neural" to 4,
                "erro falha problema travou não funciona" to 5
            )
            repeat(22) {
                for ((text, target) in samples) train(features(text), target)
            }
            save()
        }
    }

    @Synchronized
    fun classify(text: String): String {
        val scores = forward(features(text)).last()
        val best = scores.indices.maxByOrNull { scores[it] } ?: 0
        return labels[best]
    }

    @Synchronized
    fun feedback(text: String, label: Int) {
        train(features(text), label.coerceIn(0, outputSize - 1))
        save()
    }

    private fun features(text: String): FloatArray {
        val s = text.lowercase()
        val x = FloatArray(inputSize)
        if (s.isEmpty()) return x
        x[0] = (s.length.coerceAtMost(500) / 500f)
        x[1] = s.count { it.isLetter() }.toFloat() / s.length
        x[2] = s.count { it.isDigit() }.toFloat() / s.length
        x[3] = s.count { it.isWhitespace() }.toFloat() / s.length
        x[4] = if ("google" in s || "pesquisa" in s || "buscar" in s || "search" in s) 1f else 0f
        x[5] = if ("arquivo" in s || "documento" in s || ".apk" in s || ".zip" in s) 1f else 0f
        x[6] = if ("hud" in s || "esfera" in s || "painel" in s) 1f else 0f
        x[7] = if ("aprender" in s || "treinar" in s || "feedback" in s) 1f else 0f
        x[8] = if ("erro" in s || "falha" in s || "problema" in s) 1f else 0f
        x[9] = if (s.contains("http") || s.contains("www.")) 1f else 0f
        x[10] = if (s.endsWith(".pdf") || ".pdf " in s) 1f else 0f
        x[11] = if (s.contains(".png") || s.contains(".jpg") || s.contains(".webp")) 1f else 0f
        x[12] = if ("configuração" in s || "configuracao" in s || "ajuste" in s) 1f else 0f
        x[13] = if ("mensagem" in s || "conversa" in s || "chat" in s) 1f else 0f
        x[14] = if (s.contains("senha") || s.contains("password")) 1f else 0f
        x[15] = if (s.contains("android") || s.contains("app")) 1f else 0f
        x[16] = s.count { it in "aeiouáéíóú" }.toFloat() / s.length
        x[17] = s.count { it in ".,;:!?/" }.toFloat() / s.length
        x[18] = s.count { it.isUpperCase() }.toFloat() / s.length
        x[19] = s.count { !it.isLetterOrDigit() && !it.isWhitespace() }.toFloat() / s.length
        return x
    }

    private fun forward(x: FloatArray): Array<FloatArray> {
        val h1 = FloatArray(hidden1)
        for (j in 0 until hidden1) {
            var sum = b1[j]
            for (i in 0 until inputSize) sum += x[i] * w1[j * inputSize + i]
            h1[j] = sigmoid(sum)
        }
        val h2 = FloatArray(hidden2)
        for (j in 0 until hidden2) {
            var sum = b2[j]
            for (i in 0 until hidden1) sum += h1[i] * w2[j * hidden1 + i]
            h2[j] = sigmoid(sum)
        }
        val out = FloatArray(outputSize)
        for (j in 0 until outputSize) {
            var sum = b3[j]
            for (i in 0 until hidden2) sum += h2[i] * w3[j * hidden2 + i]
            out[j] = sigmoid(sum)
        }
        return arrayOf(h1, h2, out)
    }

    private fun train(x: FloatArray, label: Int) {
        val pass = forward(x)
        val h1 = pass[0]
        val h2 = pass[1]
        val out = pass[2]
        val d3 = FloatArray(outputSize)
        for (j in 0 until outputSize) {
            val target = if (j == label) 1f else 0f
            d3[j] = (target - out[j]) * out[j] * (1f - out[j])
        }
        val d2 = FloatArray(hidden2)
        for (i in 0 until hidden2) {
            var sum = 0f
            for (j in 0 until outputSize) sum += d3[j] * w3[j * hidden2 + i]
            d2[i] = sum * h2[i] * (1f - h2[i])
        }
        val d1 = FloatArray(hidden1)
        for (i in 0 until hidden1) {
            var sum = 0f
            for (j in 0 until hidden2) sum += d2[j] * w2[j * hidden1 + i]
            d1[i] = sum * h1[i] * (1f - h1[i])
        }
        val lr = 0.08f
        for (j in 0 until outputSize) {
            b3[j] += lr * d3[j]
            for (i in 0 until hidden2) w3[j * hidden2 + i] += lr * d3[j] * h2[i]
        }
        for (j in 0 until hidden2) {
            b2[j] += lr * d2[j]
            for (i in 0 until hidden1) w2[j * hidden1 + i] += lr * d2[j] * h1[i]
        }
        for (j in 0 until hidden1) {
            b1[j] += lr * d1[j]
            for (i in 0 until inputSize) w1[j * inputSize + i] += lr * d1[j] * x[i]
        }
    }

    private fun sigmoid(value: Float): Float = (1.0 / (1.0 + exp(-value.toDouble()))).toFloat()

    private fun save() {
        prefs.edit()
            .putString("w1", w1.joinToString(","))
            .putString("b1", b1.joinToString(","))
            .putString("w2", w2.joinToString(","))
            .putString("b2", b2.joinToString(","))
            .putString("w3", w3.joinToString(","))
            .putString("b3", b3.joinToString(","))
            .apply()
    }

    private fun load() {
        readArray("w1", w1)
        readArray("b1", b1)
        readArray("w2", w2)
        readArray("b2", b2)
        readArray("w3", w3)
        readArray("b3", b3)
    }

    private fun readArray(key: String, target: FloatArray) {
        val raw = prefs.getString(key, null) ?: return
        val values = raw.split(',')
        if (values.size == target.size) values.forEachIndexed { index, value ->
            target[index] = value.toFloatOrNull() ?: target[index]
        }
    }
}
