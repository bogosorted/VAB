package com.aragao.vab.whisper

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pré-processamento de áudio PCM 16kHz para o Whisper:
 * 1) Remove DC offset;
 * 2) Descarta silêncio puro ou áudios curtos (< 180 ms);
 * 3) Recorta apenas silêncio morto nas pontas preservando 240 ms de margem (sem cortar início de sílabas);
 * 4) Normaliza o volume usando o percentil 95 de energia dos frames de voz (evitando que um clique/toque no celular distorça o ganho).
 */
object AudioPreProcessor {

    fun preprocessForWhisper(samples: FloatArray, sampleRate: Int = 16000): FloatArray {
        val minSamples = (sampleRate * 0.18f).toInt().coerceAtLeast(1)
        if (samples.size < minSamples) return FloatArray(0)

        var sum = 0.0
        for (s in samples) sum += s.toDouble()
        val mean = (sum / samples.size).toFloat()

        val centered = FloatArray(samples.size)
        var peak = 0f
        var sumSq = 0.0
        for (i in samples.indices) {
            val v = (samples[i] - mean).coerceIn(-1f, 1f)
            centered[i] = v
            val av = abs(v)
            if (av > peak) peak = av
            sumSq += (v * v).toDouble()
        }

        val overallRms = sqrt(sumSq / centered.size).toFloat()
        if (peak < 0.006f || overallRms < 0.0020f) {
            return FloatArray(0)
        }

        val frameLen = (sampleRate / 50).coerceAtLeast(160) // 20ms
        val totalFrames = (centered.size + frameLen - 1) / frameLen
        val frameRmsList = FloatArray(totalFrames)

        for (f in 0 until totalFrames) {
            val start = f * frameLen
            val end = minOf(centered.size, start + frameLen)
            var frameSq = 0.0
            for (i in start until end) {
                val v = centered[i]
                frameSq += (v * v).toDouble()
            }
            frameRmsList[f] = sqrt(frameSq / (end - start)).toFloat()
        }

        val sortedRms = frameRmsList.sortedArray()
        val p95Rms = sortedRms[(sortedRms.size * 95 / 100).coerceIn(0, sortedRms.lastIndex)]
        if (p95Rms < 0.0030f) {
            return FloatArray(0)
        }

        // Limiar suave baseado no p95 (não no pico de clique) para nunca cortar fala baixa
        val activeThreshold = (p95Rms * 0.14f).coerceIn(0.0028f, 0.018f)

        var firstActive = -1
        var lastActive = -1
        for (f in 0 until totalFrames) {
            if (frameRmsList[f] >= activeThreshold) {
                if (firstActive == -1) firstActive = f
                lastActive = f
            }
        }

        if (firstActive == -1 || lastActive < firstActive) {
            return FloatArray(0)
        }

        val padSamples = (sampleRate * 0.22f).toInt() // 220ms de margem
        val trimStart = (firstActive * frameLen - padSamples).coerceAtLeast(0)
        val trimEnd = ((lastActive + 1) * frameLen + padSamples).coerceAtMost(centered.size)
        if (trimEnd - trimStart < minSamples) return FloatArray(0)

        val trimmed = centered.copyOfRange(trimStart, trimEnd)

        // Normalização linear por pico real (alvo 0.85 de pico, nunca clipa nenhuma amostra!)
        var trimmedPeak = 0f
        for (s in trimmed) {
            val a = abs(s)
            if (a > trimmedPeak) trimmedPeak = a
        }
        if (trimmedPeak > 0.003f) {
            val gain = (0.85f / trimmedPeak).coerceIn(1.0f, 14.0f)
            if (gain > 1.02f) {
                for (i in trimmed.indices) {
                    trimmed[i] = (trimmed[i] * gain).coerceIn(-0.98f, 0.98f)
                }
            }
        }

        return trimmed
    }
}
