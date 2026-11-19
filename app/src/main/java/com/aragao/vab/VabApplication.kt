package com.aragao.vab

import android.annotation.SuppressLint
import android.app.Application
import android.provider.Settings
import com.aragao.vab.core.ai.ModelManager
import com.aragao.vab.core.buddy.AppIconManager
import com.aragao.vab.core.buddy.BuddyDna
import com.aragao.vab.core.buddy.BuddyGenerator
import com.aragao.vab.core.markdown.BoardRepository
import com.aragao.vab.data.queue.QueueDatabase
import com.aragao.vab.data.queue.QueueOrchestrator
import com.aragao.vab.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VabApplication : Application() {

    private var defaultDeviceDna: BuddyDna = BuddyGenerator.generateDna("vab-default")
    private val _buddyDnaFlow = MutableStateFlow(defaultDeviceDna)
    val buddyDnaFlow: StateFlow<BuddyDna> = _buddyDnaFlow.asStateFlow()

    val buddyDna: BuddyDna
        get() = _buddyDnaFlow.value

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var boardRepository: BoardRepository
        private set

    lateinit var modelManager: ModelManager
        private set

    lateinit var queueDatabase: QueueDatabase
        private set

    lateinit var queueOrchestrator: QueueOrchestrator
        private set

    @SuppressLint("HardwareIds")
    override fun onCreate() {
        super.onCreate()

        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            ?: "vab-immutable-fallback"
        defaultDeviceDna = BuddyGenerator.generateDna(androidId)
        _buddyDnaFlow.value = defaultDeviceDna

        settingsRepository = SettingsRepository(this)
        boardRepository = BoardRepository(this, settingsRepository)
        modelManager = ModelManager(this)
        queueDatabase = QueueDatabase.getInstance(this)
        queueOrchestrator = QueueOrchestrator(
            queueDao = queueDatabase.queueDao(),
            boardRepository = boardRepository,
            settingsRepository = settingsRepository,
            modelManager = modelManager
        )
    }

    fun updateBuddySeed(customSeed: Long?) {
        val nextDna = if (customSeed != null) {
            BuddyGenerator.generateDnaFromSeed(customSeed)
        } else {
            defaultDeviceDna
        }
        _buddyDnaFlow.value = nextDna
        AppIconManager.syncLauncherAlias(this, nextDna)
    }
}
