package com.example.miniblog

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.example.miniblog.data.AppPreferences

class MiniBlogApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Apply the saved theme (System / Light / Dark) before any UI shows.
        AppCompatDelegate.setDefaultNightMode(
            AppPreferences(this).getThemeMode()
        )
    }
}
