package com.example

import android.app.Application
import android.content.Context
import com.example.data.datastore.SettingsDataStore
import com.example.data.local.RecordingDatabase
import com.example.data.repository.RecordingRepository
import com.example.recording.RecordingFileManager
import com.example.recording.ScreenRecordManager

class AppContainer(context: Context) {
    val database: RecordingDatabase = RecordingDatabase.getInstance(context)
    val settingsDataStore: SettingsDataStore = SettingsDataStore(context)
    val fileManager: RecordingFileManager = RecordingFileManager(context)
    val repository: RecordingRepository = RecordingRepository(
        context = context,
        dao = database.recordingDao(),
        fileManager = fileManager
    )
    val screenRecordManager: ScreenRecordManager = ScreenRecordManager(
        context = context,
        fileManager = fileManager,
        repository = repository
    )
}

class BARecorderApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
