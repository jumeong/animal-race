package com.jumeong.animalrace

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.ScaleAnimation
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val splashScreen = installSplashScreen()
        setContentView(R.layout.activity_splash)

        // Title fade-in + scale animation
        val txtTitle = findViewById<TextView>(R.id.txtSplashTitle)

        val titleAnim = AnimationSet(true).apply {
            interpolator = DecelerateInterpolator()
            addAnimation(AlphaAnimation(0f, 1f).apply { duration = 800 })
            addAnimation(ScaleAnimation(
                0.6f, 1f, 0.6f, 1f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f
            ).apply { duration = 800 })
        }
        txtTitle.startAnimation(titleAnim)


        // 3초 후 MainActivity로 이동
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 3000)
    }
}