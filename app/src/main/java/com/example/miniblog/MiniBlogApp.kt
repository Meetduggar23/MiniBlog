package com.example.miniblog

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.example.miniblog.data.AppPreferences

class MiniBlogApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(
            AppPreferences(this).getThemeMode()
        )
    }
}

