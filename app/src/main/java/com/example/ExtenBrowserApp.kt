package com.example

import android.app.Application
import com.example.data.db.AppDatabase
import com.example.engine.SampleExtensions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExtenBrowserApp : Application() {

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)

        // Seed sample extensions if first launch
        val prefs = getSharedPreferences("exten_app_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("sample_extensions_seeded_v1", false)) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val samples = SampleExtensions.seedSampleExtensions(this@ExtenBrowserApp)
                    for (sample in samples) {
                        database.extensionDao().insertOrUpdate(sample)
                    }
                    prefs.edit().putBoolean("sample_extensions_seeded_v1", true).apply()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
