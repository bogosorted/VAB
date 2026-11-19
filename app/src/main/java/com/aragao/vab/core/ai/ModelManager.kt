package com.aragao.vab.core.ai

import android.content.Context
import android.os.Environment
import com.aragao.vab.llama.LlamaEngine
import com.aragao.vab.whisper.WhisperEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ModelRuntimeStatus(
    val whisperModelFile: File? = null,
    val slmModelFile: File? = null,
    val whisperLoadedInRam: Boolean = false,
    val slmLoadedInRam: Boolean = false,
    val statusMessage: String = "Descansando • memória livre"
)

/**
 * Gerencia os arquivos de modelos (.bin do Whisper e .gguf do Qwen) e implementa
 * o carregamento Lazy na fila com liberação automática de RAM após 2 minutos ocioso.
 */
class ModelManager(private val context: Context) {

    companion object {
        const val DEFAULT_QWEN_FILENAME = "qwen-default.gguf"
        const val DEFAULT_WHISPER_ASSET = "whisper-default.bin"
        const val DEFAULT_WHISPER_FILENAME = "whisper-small-q5_1.bin"
        private const val KEEP_ALIVE_MS = 120_000L // 2 minutos
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val whisperEngine = WhisperEngine()
    val llamaEngine = LlamaEngine()

    private var keepAliveJob: Job? = null

    private val _runtimeStatus = MutableStateFlow(ModelRuntimeStatus())
    val runtimeStatus: StateFlow<ModelRuntimeStatus> = _runtimeStatus.asStateFlow()

    private val modelsDir: File by lazy {
        File(context.filesDir, "models").apply { mkdirs() }
    }

    suspend fun resolveWhisperModel(customPath: String?): File? = withContext(Dispatchers.IO) {
        if (!customPath.isNullOrBlank()) {
            val f = File(customPath)
            if (f.exists() && f.length() > 0L) return@withContext f
        }

        // Remove modelo base antigo (60MB) caso tenha sobrado de versão anterior
        val legacyBase = File(modelsDir, "whisper-default.bin")
        if (legacyBase.exists() && legacyBase.length() < 150_000_000L) {
            runCatching { legacyBase.delete() }
        }

        // 1) Verifica se o modelo small atualizado já foi extraído em filesDir/models/
        val internalFile = File(modelsDir, DEFAULT_WHISPER_FILENAME)
        if (internalFile.exists() && internalFile.length() > 150_000_000L) {
            return@withContext internalFile
        }

        // 2) Tenta extrair dos assets do APK (flavor demoBundled)
        _runtimeStatus.value = _runtimeStatus.value.copy(
            statusMessage = "Preparando voz offline (1ª vez)..."
        )
        if (extractAssetIfPresent("models/$DEFAULT_WHISPER_ASSET", internalFile)) {
            return@withContext internalFile
        }

        // 3) Fallback devSlim: procura em pastas acessíveis do aparelho
        findExternalModelFile(".bin")
    }

    suspend fun resolveSlmModel(customPath: String?): File? = withContext(Dispatchers.IO) {
        if (!customPath.isNullOrBlank()) {
            val f = File(customPath)
            if (f.exists() && f.length() > 0L) return@withContext f
        }

        val internalFile = File(modelsDir, DEFAULT_QWEN_FILENAME)
        if (internalFile.exists() && internalFile.length() > 0L) {
            return@withContext internalFile
        }

        _runtimeStatus.value = _runtimeStatus.value.copy(
            statusMessage = "Preparando cérebro offline (1ª vez)..."
        )
        if (extractAssetIfPresent("models/$DEFAULT_QWEN_FILENAME", internalFile)) {
            return@withContext internalFile
        }

        findExternalModelFile(".gguf")
    }

    private fun findExternalModelFile(extension: String): File? {
        val searchDirs = listOfNotNull(
            context.getExternalFilesDir("models"),
            context.getExternalFilesDir(null),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "vab-models"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        )
        for (dir in searchDirs) {
            val found = runCatching {
                dir.listFiles { file ->
                    file.isFile && file.length() > 1024L && file.name.endsWith(extension, ignoreCase = true)
                }?.firstOrNull()
            }.getOrNull()
            if (found != null) return found
        }
        return null
    }

    suspend fun refreshDetectedModels(customWhisperPath: String?, customSlmPath: String?) {
        val w = resolveWhisperModel(customWhisperPath)
        val s = resolveSlmModel(customSlmPath)
        val msg = if (w != null && s != null) {
            "Pronto • memória livre"
        } else {
            "Faltando modelo offline — importe na aba Buddy"
        }
        _runtimeStatus.value = _runtimeStatus.value.copy(
            whisperModelFile = w,
            slmModelFile = s,
            statusMessage = msg
        )
    }

    suspend fun transcribeBatchAndReleaseWhisper(
        wavFile: File,
        customWhisperPath: String?
    ): Result<String> {
        keepAliveJob?.cancel()
        // Garante que SLM e Whisper não disputem pico de RAM simultaneamente em aparelhos menores
        if (llamaEngine.isLoaded) {
            llamaEngine.unload()
        }

        val modelFile = resolveWhisperModel(customWhisperPath)
            ?: return Result.failure(IllegalStateException("Modelo de voz (.bin) não encontrado. Importe na aba Buddy ou instale o APK completo."))

        _runtimeStatus.value = _runtimeStatus.value.copy(
            whisperModelFile = modelFile,
            whisperLoadedInRam = true,
            slmLoadedInRam = false,
            statusMessage = "Ouvindo seu áudio..."
        )

        if (!whisperEngine.ensureLoaded(modelFile)) {
            _runtimeStatus.value = _runtimeStatus.value.copy(
                whisperLoadedInRam = false,
                statusMessage = "Falha ao carregar modelo Whisper"
            )
            return Result.failure(IllegalStateException("Falha ao inicializar Whisper em C++"))
        }

        val text = whisperEngine.transcribeWavFile(wavFile, "pt")
        return Result.success(text)
    }

    suspend fun unloadWhisperNow() {
        whisperEngine.unload()
        _runtimeStatus.value = _runtimeStatus.value.copy(
            whisperLoadedInRam = false
        )
    }

    suspend fun decideWithSlmAndScheduleUnload(
        systemPrompt: String,
        userPrompt: String,
        gbnfGrammar: String,
        customSlmPath: String?
    ): Result<String> {
        keepAliveJob?.cancel()
        if (whisperEngine.isLoaded) {
            whisperEngine.unload()
        }

        val modelFile = resolveSlmModel(customSlmPath)
            ?: return Result.failure(IllegalStateException("Modelo de IA (.gguf) não encontrado. Importe na aba Buddy ou instale o APK completo."))

        _runtimeStatus.value = _runtimeStatus.value.copy(
            slmModelFile = modelFile,
            whisperLoadedInRam = false,
            slmLoadedInRam = true,
            statusMessage = "Pensando e organizando no fichário..."
        )

        if (!llamaEngine.ensureLoaded(modelFile)) {
            _runtimeStatus.value = _runtimeStatus.value.copy(
                slmLoadedInRam = false,
                statusMessage = "Falha ao carregar modelo Qwen (.gguf)"
            )
            return Result.failure(IllegalStateException("Falha ao inicializar Qwen no llama.cpp"))
        }

        val jsonOut = llamaEngine.generateConstrainedJson(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            gbnfGrammar = gbnfGrammar,
            maxTokens = 768,
            temperature = 0.12f
        )

        scheduleKeepAliveUnload()
        return if (jsonOut.isNotBlank()) {
            Result.success(jsonOut)
        } else {
            Result.failure(IllegalStateException("Saída JSON vazia retornada pelo SLM"))
        }
    }

    private fun scheduleKeepAliveUnload() {
        _runtimeStatus.value = _runtimeStatus.value.copy(
            slmLoadedInRam = llamaEngine.isLoaded,
            whisperLoadedInRam = false,
            statusMessage = "Acordado • libera memória em 2 min"
        )
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            delay(KEEP_ALIVE_MS)
            whisperEngine.unload()
            llamaEngine.unload()
            _runtimeStatus.value = _runtimeStatus.value.copy(
                whisperLoadedInRam = false,
                slmLoadedInRam = false,
                statusMessage = "Descansando • memória livre"
            )
        }
    }

    suspend fun forceReleaseAllRam() {
        keepAliveJob?.cancel()
        whisperEngine.unload()
        llamaEngine.unload()
        _runtimeStatus.value = _runtimeStatus.value.copy(
            whisperLoadedInRam = false,
            slmLoadedInRam = false,
            statusMessage = "Descansando • memória livre"
        )
    }

    private fun extractAssetIfPresent(assetPath: String, targetFile: File): Boolean {
        return try {
            context.assets.open(assetPath).use { input ->
                val temp = File(targetFile.parentFile, "${targetFile.name}.tmp")
                temp.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 256 * 1024)
                }
                temp.renameTo(targetFile)
            }
            targetFile.exists() && targetFile.length() > 0L
        } catch (_: Exception) {
            false
        }
    }
}
