package com.example

import android.app.Application
import com.example.data.db.AppDatabase

class ExtenBrowserApp : Application() {

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
    }
}
