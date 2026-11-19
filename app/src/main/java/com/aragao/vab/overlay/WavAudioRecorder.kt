package com.aragao.vab.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class WavAudioRecorder(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 16000
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    private var currentOutputFile: File? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startRecording(): File? {
        if (_isRecording.value) return currentOutputFile

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return null

        val bufferSize = maxOf(minBuf * 2, 4096)
        val recorder = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        }.getOrNull() ?: return null

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return null
        }

        val recordingsDir = File(context.filesDir, "recordings").apply { mkdirs() }
        val outFile = File(recordingsDir, "vab_${System.currentTimeMillis()}.wav")

        currentOutputFile = outFile
        audioRecord = recorder
        _isRecording.value = true
        _amplitude.value = 0f

        recorder.startRecording()

        recordJob = scope.launch {
            var totalAudioBytes = 0L
            val shortBuf = ShortArray(1024)
            val byteBuf = ByteBuffer.allocate(shortBuf.size * 2).order(ByteOrder.LITTLE_ENDIAN)

            FileOutputStream(outFile).use { fos ->
                // Reserva cabeçalho RIFF/WAV de 44 bytes
                fos.write(ByteArray(44))

                while (isActive && _isRecording.value) {
                    val readCount = recorder.read(shortBuf, 0, shortBuf.size)
                    if (readCount > 0) {
                        byteBuf.clear()
                        var sumSq = 0.0
                        for (i in 0 until readCount) {
                            val s = shortBuf[i]
                            byteBuf.putShort(s)
                            val norm = s / 32768.0
                            sumSq += norm * norm
                        }
                        val rms = sqrt(sumSq / readCount).toFloat()
                        _amplitude.value = (rms * 4.5f).coerceIn(0f, 1f)

                        fos.write(byteBuf.array(), 0, readCount * 2)
                        totalAudioBytes += (readCount * 2).toLong()
                    }
                }
            }
            writeWavHeader(outFile, totalAudioBytes)
        }

        return outFile
    }

    suspend fun stopRecording(): File? {
        if (!_isRecording.value) return null
        _isRecording.value = false
        _amplitude.value = 0f

        runCatching {
            audioRecord?.stop()
        }
        recordJob?.join()
        runCatching {
            audioRecord?.release()
        }
        audioRecord = null
        recordJob = null

        val file = currentOutputFile
        currentOutputFile = null
        return file?.takeIf { it.exists() && it.length() > 44L }
    }

    private fun writeWavHeader(file: File, pcmDataLen: Long) {
        if (!file.exists()) return
        val totalDataLen = pcmDataLen + 36L
        val byteRate = SAMPLE_RATE * 1 * 16 / 8

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen.toInt())
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // Subchunk1Size PCM
            putShort(1) // AudioFormat = 1 (PCM)
            putShort(1) // NumChannels = 1 (Mono)
            putInt(SAMPLE_RATE)
            putInt(byteRate)
            putShort(2) // BlockAlign = 1 * 16 / 8
            putShort(16) // BitsPerSample
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmDataLen.toInt())
        }

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(header.array())
        }
    }
}
