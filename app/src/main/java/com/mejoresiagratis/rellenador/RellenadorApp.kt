package com.mejoresiagratis.rellenador

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RellenadorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Required once before any PdfBox usage on Android
        PDFBoxResourceLoader.init(applicationContext)
    }
}
