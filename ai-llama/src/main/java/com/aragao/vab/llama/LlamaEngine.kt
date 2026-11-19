package com.aragao.vab.llama

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

internal object LlamaJni {
    init {
        System.loadLibrary("vab_llama")
    }

    external fun nativeLoadModel(modelPath: String): Long
    external fun nativeGenerateConstrainedJson(
        modelPtr: Long,
        systemPrompt: String,
        userPrompt: String,
        grammar: String,
        maxTokens: Int,
        temperature: Float,
        nThreads: Int
    ): String

    external fun nativeFreeModel(modelPtr: Long)
}

class LlamaEngine {
    private val mutex = Mutex()
    private var modelPtr: Long = 0L
    private var loadedPath: String? = null

    val isLoaded: Boolean
        get() = modelPtr != 0L

    suspend fun ensureLoaded(modelFile: File): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (modelPtr != 0L && loadedPath == modelFile.absolutePath) {
                return@withContext true
            }
            if (modelPtr != 0L) {
                LlamaJni.nativeFreeModel(modelPtr)
                modelPtr = 0L
                loadedPath = null
            }
            if (!modelFile.exists() || modelFile.length() == 0L) {
                return@withContext false
            }
            modelPtr = LlamaJni.nativeLoadModel(modelFile.absolutePath)
            if (modelPtr != 0L) {
                loadedPath = modelFile.absolutePath
                true
            } else {
                false
            }
        }
    }

    suspend fun generateConstrainedJson(
        systemPrompt: String,
        userPrompt: String,
        gbnfGrammar: String,
        maxTokens: Int = 512,
        temperature: Float = 0.15f
    ): String = mutex.withLock {
        withContext(Dispatchers.Default) {
            if (modelPtr == 0L) return@withContext ""
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
            LlamaJni.nativeGenerateConstrainedJson(
                modelPtr = modelPtr,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                grammar = gbnfGrammar,
                maxTokens = maxTokens,
                temperature = temperature,
                nThreads = threads
            ).trim()
        }
    }

    suspend fun unload() = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (modelPtr != 0L) {
                LlamaJni.nativeFreeModel(modelPtr)
                modelPtr = 0L
                loadedPath = null
            }
        }
    }
}
