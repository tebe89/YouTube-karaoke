package com.example

import android.app.Application
import android.util.Log
import com.example.audio.AudioEngineManager
import com.example.shizuku.ShizukuOptimizer

class TBSoundLabApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("TBSoundLab", "Initializing TB Sound Lab Engine...")
        AudioEngineManager.init(this)
        ShizukuOptimizer.init(this)
    }
}
