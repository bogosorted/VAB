package com.aragao.vab

import android.annotation.SuppressLint
import android.app.Application
import android.provider.Settings
import com.aragao.vab.core.ai.ModelManager
import com.aragao.vab.core.buddy.BuddyDna
import com.aragao.vab.core.buddy.BuddyGenerator
import com.aragao.vab.core.markdown.BoardRepository
import com.aragao.vab.data.queue.QueueDatabase
import com.aragao.vab.data.queue.QueueOrchestrator
import com.aragao.vab.data.settings.SettingsRepository

class VabApplication : Application() {

    lateinit var buddyDna: BuddyDna
        private set

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

        // Seed 100% travada e imutável derivada do ANDROID_ID exclusivo deste aparelho
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            ?: "vab-immutable-fallback"
        buddyDna = BuddyGenerator.generateDna(androidId)

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
}
