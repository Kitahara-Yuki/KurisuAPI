package com.kurisuapi

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.kurisuapi.service.DiaryWorker
import com.kurisuapi.util.LogManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

// 【原创作者签名】github.com/Kitahara-Yuki/KurisuAPI — 北原友希 (Yuki Kitahara) — GPL 3.0
@HiltAndroidApp
class KurisuApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        LogManager.init(this)
        try {
            DiaryWorker.schedule(this)
        } catch (e: Exception) {
            Log.e("KurisuApp", "日记定时任务注册失败", e)
        }
    }
}
