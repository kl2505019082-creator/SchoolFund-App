package com.uptm.schoolfund.ui

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.LinearInterpolator
import android.widget.ProgressBar
import com.uptm.schoolfund.R

/** Official SMK Perlis splash screen shown briefly on app launch before MainActivity. */
class SplashActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val progress = findViewById<ProgressBar>(R.id.progressSplash)
        ObjectAnimator.ofInt(progress, "progress", 0, 100).apply {
            duration = SPLASH_DURATION_MS
            interpolator = LinearInterpolator()
            start()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, SPLASH_DURATION_MS)
    }

    companion object {
        private const val SPLASH_DURATION_MS = 2200L
    }
}
