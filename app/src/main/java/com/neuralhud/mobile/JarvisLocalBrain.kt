package com.neuralhud.mobile

import android.content.Context
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.text.Normalizer
import java.util.Locale
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

enum class JarvisIntent {
    OPEN_APP,
    SEARCH_WEB,
    OPEN_URL,
    OPEN_SETTINGS,
    GO_BACK,
    GO_HOME,
    NOTIFICATIONS,
    QUICK_SETTINGS,
    CALCULATE,
    TIME,
    DATE,
    TYPE_TEXT,
    CLICK_TEXT,
    SCROLL_DOWN,
    SCROLL_UP,
    WAIT,
    SAVE_NOTE,
    QUESTION,
    HELP
}

data class IntentGuess(val intent: JarvisIntent, val confidence: Float)

object JarvisLocalBrain {
    const val HIDDEN_NEURONS = 2678
    const val INPUT_FEATURES = 256
    const val CONNECTIONS_PER_NEURON = 16
    private const val MODEL_VERSION = 1
    private const val LEARNING_RATE = 0.045f
    private const val TRAINING_EPOCHS = 3
    private const val FILE_NAME = "jarvis-brain-2678.bin"

    private var model: Model? = null

    fun prewarm(context: Context) {
        Thread({ synchronized(this) { getModel(context.applicationContext) } }, "jarvis-local-brain-init").apply { isDaemon = true }.start()
    }

    @Synchronized
    fun classify(context: Context, text: String): IntentGuess {
        val loaded = getModel(context.applicationContext)
        val scores = loaded.forward(features(text)).second
        var best = 0
        for (i in 1 until scores.size) if (scores[i] > scores[best]) best = i
        return IntentGuess(JarvisIntent.entries[best], scores[best])
    }

    @Synchronized
    fun learn(context: Context, example: String, intent: JarvisIntent) {
        val loaded = getModel(context.applicationContext)
        loaded.train(features(example), intent.ordinal)
        save(context.applicationContext, loaded)
    }

    private fun getModel(context: Context): Model {
        model?.let { return it }
        val file = File(context.filesDir, FILE_NAME)
        val loaded = try {
            if (file.isFile && file.length() in 1000L..2_000_000L) DataInputStream(file.inputStream().buffered()).use { Model.read(it) } else null
        } catch (_: Exception) {
            null
        }
        val instance = loaded ?: Model().apply {
            val samples = trainingSamples()
            repeat(TRAINING_EPOCHS) {
                samples.forEach { train(features(it.first), it.second.ordinal) }
            }
            save(context, this)
        }
        model = instance
        return instance
    }

    private fun save(context: Context, model: Model) {
        val destination = File(context.filesDir, FILE_NAME)
        val temporary = File(context.filesDir, "$FILE_NAME.tmp")
        try {
            DataOutputStream(temporary.outputStream().buffered()).use { model.write(it) }
            if (!temporary.renameTo(destination)) {
                destination.outputStream().use { output -> temporary.inputStream().use { it.copyTo(output) } }
                temporary.delete()
            }
        } catch (_: Exception) {
            temporary.delete()
        }
    }

    private fun features(raw: String): FloatArray {
        val normalized = normalize(raw)
        val tokens = normalized.split(Regex("[^a-z0-9.+*/^=-]+")).filter { it.isNotBlank() }.take(80)
        val vector = FloatArray(INPUT_FEATURES)
        fun add(token: String, weight: Float) {
            val h = token.hashCode()
            val index = (h and Int.MAX_VALUE) % INPUT_FEATURES
            vector[index] += if ((h and 1) == 0) weight else -weight
        }
        tokens.forEachIndexed { index, token ->
            add(token, 1f)
            if (index > 0) add(tokens[index - 1] + "_" + token, 0.65f)
        }
        val norm = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 0f) for (i in vector.indices) vector[i] /= norm
        return vector
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun trainingSamples(): List<Pair<String, JarvisIntent>> {
        val rows = listOf(
            JarvisIntent.OPEN_APP to listOf("abra o YouTube", "inicie o Chrome", "quero abrir WhatsApp", "launch an app", "abre o aplicativo", "iniciar Spotify"),
            JarvisIntent.SEARCH_WEB to listOf("pesquise gatos na internet", "procure tutoriais de Kotlin", "busque notícias sobre ciência", "pesquisar na web", "encontre informações sobre Marte", "search the web"),
            JarvisIntent.OPEN_URL to listOf("abra o site example.com", "visite https://developer.android.com", "abrir este endereço web", "open a website", "entre no site da Wikipedia"),
            JarvisIntent.OPEN_SETTINGS to listOf("abra as configurações", "quero configurar o Android", "abrir ajustes do sistema", "open settings", "ir para configurações de Wi-Fi"),
            JarvisIntent.GO_BACK to listOf("volte uma tela", "voltar", "retorne para a página anterior", "go back", "aperte voltar"),
            JarvisIntent.GO_HOME to listOf("ir para tela inicial", "voltar para home", "vá para o início", "go home", "abrir a tela principal"),
            JarvisIntent.NOTIFICATIONS to listOf("abrir notificações", "mostrar notificações do telefone", "puxe a central de notificações", "open notifications", "ver notificações"),
            JarvisIntent.QUICK_SETTINGS to listOf("abrir configurações rápidas", "mostrar painel rápido", "puxe os atalhos do sistema", "quick settings", "mostrar controles rápidos"),
            JarvisIntent.CALCULATE to listOf("calcule 32 vezes 45", "quanto é 150 dividido por 3", "resolver a expressão 4+8*3", "calculate 20 squared", "faça esta conta", "quanto dá 19 mais 24"),
            JarvisIntent.TIME to listOf("que horas são", "diga a hora atual", "qual é o horário agora", "current time", "me fale as horas"),
            JarvisIntent.DATE to listOf("que dia é hoje", "qual a data de hoje", "diga o dia atual", "today's date", "data de agora"),
            JarvisIntent.TYPE_TEXT to listOf("digite olá no campo", "escreva este texto", "insira uma mensagem", "type this text", "preencha o campo"),
            JarvisIntent.CLICK_TEXT to listOf("toque no botão continuar", "clique em pesquisar", "pressione o botão enviar", "tap the settings button", "selecione o item menu"),
            JarvisIntent.SCROLL_DOWN to listOf("role para baixo", "desça a página", "continue rolando para baixo", "scroll down", "mostrar mais conteúdo"),
            JarvisIntent.SCROLL_UP to listOf("role para cima", "suba a página", "voltar ao início da lista", "scroll up", "subir conteúdo"),
            JarvisIntent.WAIT to listOf("aguarde 2 segundos", "espere um pouco", "pausar por 500 ms", "wait a moment", "esperar"),
            JarvisIntent.SAVE_NOTE to listOf("anote comprar pão", "salve uma nota", "lembre que tenho uma tarefa", "save a note", "guardar este lembrete"),
            JarvisIntent.QUESTION to listOf("o que é inteligência artificial", "por que o céu é azul", "me explique a gravidade", "quem inventou o telefone", "como funciona uma bateria", "what is a neural network"),
            JarvisIntent.HELP to listOf("ajuda", "o que você sabe fazer", "mostrar comandos suportados", "help me", "quais são suas funções")
        )
        return rows.flatMap { (intent, phrases) -> phrases.take(3).map { it to intent } }
    }

    private class Model {
        private val connectionIndices = IntArray(HIDDEN_NEURONS * CONNECTIONS_PER_NEURON)
        private val inputWeights = FloatArray(connectionIndices.size)
        private val hiddenBias = FloatArray(HIDDEN_NEURONS)
        private val outputWeights = FloatArray(JarvisIntent.entries.size * HIDDEN_NEURONS)
        private val outputBias = FloatArray(JarvisIntent.entries.size)

        init {
            val topologyRandom = Random(2678)
            val weightRandom = Random(9132678)
            for (i in connectionIndices.indices) {
                connectionIndices[i] = topologyRandom.nextInt(INPUT_FEATURES)
                inputWeights[i] = (weightRandom.nextFloat() - 0.5f) * 0.32f
            }
            for (i in hiddenBias.indices) hiddenBias[i] = 0.015f
            for (i in outputWeights.indices) outputWeights[i] = (weightRandom.nextFloat() - 0.5f) * 0.02f
        }

        fun forward(input: FloatArray): Pair<FloatArray, FloatArray> {
            val hidden = FloatArray(HIDDEN_NEURONS)
            for (n in 0 until HIDDEN_NEURONS) {
                var sum = hiddenBias[n]
                val offset = n * CONNECTIONS_PER_NEURON
                for (j in 0 until CONNECTIONS_PER_NEURON) {
                    sum += input[inputOffset(offset + j)] * inputWeights[offset + j]
                }
                hidden[n] = kotlin.math.tanh(sum * 2.0).toFloat()
            }
            val logits = FloatArray(JarvisIntent.entries.size)
            for (o in logits.indices) {
                var sum = outputBias[o]
                val offset = o * HIDDEN_NEURONS
                for (n in 0 until HIDDEN_NEURONS) sum += hidden[n] * outputWeights[offset + n]
                logits[o] = sum
            }
            val max = logits.maxOrNull() ?: 0f
            var total = 0.0
            val probs = FloatArray(logits.size)
            for (i in logits.indices) {
                probs[i] = exp((logits[i] - max).toDouble()).toFloat()
                total += probs[i]
            }
            if (total > 0.0) for (i in probs.indices) probs[i] = (probs[i] / total).toFloat()
            return hidden to probs
        }

        private fun inputOffset(index: Int): Int = connectionIndices[index]

        fun train(input: FloatArray, label: Int) {
            val (hidden, probabilities) = forward(input)
            val outputDelta = FloatArray(probabilities.size)
            for (o in probabilities.indices) {
                val target = if (o == label) 1f else 0f
                outputDelta[o] = (target - probabilities[o]) * LEARNING_RATE
            }
            val hiddenDelta = FloatArray(HIDDEN_NEURONS)
            for (n in 0 until HIDDEN_NEURONS) {
                var error = 0f
                for (o in outputDelta.indices) error += outputDelta[o] * outputWeights[o * HIDDEN_NEURONS + n]
                hiddenDelta[n] = error * (1f - hidden[n] * hidden[n]) * 2f
            }
            for (o in outputDelta.indices) {
                val delta = outputDelta[o]
                outputBias[o] += delta
                val offset = o * HIDDEN_NEURONS
                for (n in hidden.indices) outputWeights[offset + n] += delta * hidden[n]
            }
            for (n in hidden.indices) {
                val delta = hiddenDelta[n]
                hiddenBias[n] += delta
                val offset = n * CONNECTIONS_PER_NEURON
                for (j in 0 until CONNECTIONS_PER_NEURON) {
                    inputWeights[offset + j] += delta * input[connectionIndices[offset + j]]
                }
            }
        }

        fun write(out: DataOutputStream) {
            out.writeInt(MODEL_VERSION)
            out.writeInt(HIDDEN_NEURONS)
            out.writeInt(INPUT_FEATURES)
            out.writeInt(JarvisIntent.entries.size)
            inputWeights.forEach(out::writeFloat)
            hiddenBias.forEach(out::writeFloat)
            outputWeights.forEach(out::writeFloat)
            outputBias.forEach(out::writeFloat)
        }

        companion object {
            fun read(input: DataInputStream): Model? {
                if (input.readInt() != MODEL_VERSION ||
                    input.readInt() != HIDDEN_NEURONS ||
                    input.readInt() != INPUT_FEATURES ||
                    input.readInt() != JarvisIntent.entries.size) return null
                val model = Model()
                for (i in model.inputWeights.indices) model.inputWeights[i] = input.readFloat()
                for (i in model.hiddenBias.indices) model.hiddenBias[i] = input.readFloat()
                for (i in model.outputWeights.indices) model.outputWeights[i] = input.readFloat()
                for (i in model.outputBias.indices) model.outputBias[i] = input.readFloat()
                return model
            }
        }
    }
}
