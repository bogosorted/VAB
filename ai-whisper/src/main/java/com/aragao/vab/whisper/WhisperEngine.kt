package com.aragao.vab.whisper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

internal object WhisperJni {
    init {
        System.loadLibrary("vab_whisper")
    }

    external fun nativeLoadModel(modelPath: String): Long
    external fun nativeTranscribe(
        ctxPtr: Long,
        pcmSamples: FloatArray,
        language: String,
        nThreads: Int
    ): String

    external fun nativeFreeModel(ctxPtr: Long)
}

class WhisperEngine {
    private val mutex = Mutex()
    private var ctxPtr: Long = 0L
    private var loadedPath: String? = null

    val isLoaded: Boolean
        get() = ctxPtr != 0L

    suspend fun ensureLoaded(modelFile: File): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (ctxPtr != 0L && loadedPath == modelFile.absolutePath) {
                return@withContext true
            }
            if (ctxPtr != 0L) {
                WhisperJni.nativeFreeModel(ctxPtr)
                ctxPtr = 0L
                loadedPath = null
            }
            if (!modelFile.exists() || modelFile.length() == 0L) {
                return@withContext false
            }
            ctxPtr = WhisperJni.nativeLoadModel(modelFile.absolutePath)
            if (ctxPtr != 0L) {
                loadedPath = modelFile.absolutePath
                true
            } else {
                false
            }
        }
    }

    suspend fun transcribeWavFile(
        wavFile: File,
        language: String = "pt"
    ): String = mutex.withLock {
        withContext(Dispatchers.Default) {
            if (ctxPtr == 0L || !wavFile.exists()) return@withContext ""
            val samples = WavPcmReader.readMono16kFloat(wavFile)
            if (samples.size < 2400 || WavPcmReader.computeRms(samples) < 0.004f) {
                // Menos de 150ms ou silêncio absoluto: evita alucinação clássica do Whisper
                return@withContext ""
            }
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
            WhisperJni.nativeTranscribe(ctxPtr, samples, language, threads).trim()
        }
    }

    suspend fun unload() = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (ctxPtr != 0L) {
                WhisperJni.nativeFreeModel(ctxPtr)
                ctxPtr = 0L
                loadedPath = null
            }
        }
    }
}

object WavPcmReader {
    fun readMono16kFloat(wavFile: File): FloatArray {
        val bytes = wavFile.readBytes()
        if (bytes.size <= 44) return FloatArray(0)

        // Procura o chunk "data" do RIFF/WAV
        var dataOffset = 44
        var dataSize = bytes.size - 44
        var channels = 1
        var sampleRate = 16000

        if (bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte()) {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            var pos = 12
            while (pos + 8 <= bytes.size) {
                val chunkId = String(bytes, pos, 4, Charsets.US_ASCII)
                val chunkLen = buf.getInt(pos + 4)
                if (chunkId == "fmt " && pos + 16 <= bytes.size) {
                    channels = buf.getShort(pos + 10).toInt().coerceAtLeast(1)
                    sampleRate = buf.getInt(pos + 12).coerceAtLeast(8000)
                } else if (chunkId == "data") {
                    dataOffset = pos + 8
                    dataSize = chunkLen.coerceAtMost(bytes.size - dataOffset)
                    break
                }
                pos += 8 + chunkLen + (chunkLen and 1)
            }
        }

        val totalShorts = dataSize / 2
        val frames = totalShorts / channels
        if (frames <= 0) return FloatArray(0)

        val pcmBuffer = ByteBuffer.wrap(bytes, dataOffset, frames * channels * 2)
            .order(ByteOrder.LITTLE_ENDIAN)

        val mono = FloatArray(frames)
        for (i in 0 until frames) {
            var acc = 0f
            for (c in 0 until channels) {
                acc += pcmBuffer.short / 32768.0f
            }
            mono[i] = (acc / channels).coerceIn(-1.0f, 1.0f)
        }

        if (sampleRate == 16000) return mono

        // Reamostragem linear simples para 16kHz caso venha de outra taxa
        val outLen = ((frames.toLong() * 16000L) / sampleRate).toInt().coerceAtLeast(1)
        val resampled = FloatArray(outLen)
        val ratio = sampleRate.toDouble() / 16000.0
        for (i in 0 until outLen) {
            val srcPos = i * ratio
            val i0 = srcPos.toInt().coerceIn(0, frames - 1)
            val i1 = (i0 + 1).coerceIn(0, frames - 1)
            val frac = (srcPos - i0).toFloat()
            resampled[i] = mono[i0] * (1f - frac) + mono[i1] * frac
        }
        return resampled
    }

    fun computeRms(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sumSq = 0.0
        for (s in samples) {
            sumSq += (s * s).toDouble()
        }
        return sqrt(sumSq / samples.size).toFloat()
    }
}
