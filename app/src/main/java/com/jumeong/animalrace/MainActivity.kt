package com.jumeong.animalrace

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.*
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.os.Build
import android.widget.Button

import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import android.widget.ImageView
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import kotlin.math.max
import kotlin.math.min
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// --- 상태 및 데이터 정의 ---
enum class GameState { INTRO, COUNTDOWN, RACING, FINISHED }
enum class RaceState { RUNNING, STUNNED, BOOST }

data class TrackObject(val progress: Float, val isItem: Boolean, var isActive: Boolean = true)
data class SandParticle(val x: Float, val y: Float, val size: Float, val alpha: Int)
data class CelebrationParticle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    val color: Int, var size: Float,
    var alpha: Float, var rotation: Float,
    var rotationSpeed: Float
)
data class ParallaxElement(val x: Float, val y: Float, val size: Float, val type: Int, val color: Int)

data class Animal(
    val emoji: String,
    var progress: Float = 0f,
    var speed: Float = 0.0015f,
    var state: RaceState = RaceState.RUNNING,
    var stateDuration: Int = 0,
    var rank: Int = 0,
    var bobOffset: Float = 0f,
    var rotation: Float = 0f,
    var isSelected: Boolean = false,
    var currentBoostPower: Float = 1.0f
)

enum class MapType {
    GRASS,      // 잔디 운동장
    DIRT        // 흙 운동장
}

// UI Constants
private const val BUTTON_SIZE = 80f
private const val BUTTON_PADDING = 20f
private const val BUTTON_GAP = 15f
private const val FINISH_LINE_OFFSET = 250f

// Animation Constants
private const val FRAME_DELAY = 16L
private const val BOB_AMPLITUDE = 15f
private const val BOOST_BOB_SPEED = 0.6f
private const val NORMAL_BOB_SPEED = 0.4f
private const val ROTATION_SPEED = 24f
private const val MAX_ROTATION = 720f

// Game Constants
private const val COLLISION_THRESHOLD = 0.006f
private const val BOOST_DURATION = 45
private const val STUN_DURATION = 60
private const val CATCHUP_DISTANCE = 0.1f
private const val CATCHUP_SPEED = 0.0002f

// Graphics Constants
private const val ANIMAL_SIZE = 80
private const val ROCKET_SIZE = 60
private const val STRIPE_DIVISOR = 8f
private const val LANE_SEPARATOR_WIDTH = 10f  // 5f에서 10f로 변경

class MainActivity : AppCompatActivity() {
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var raceView: RaceView
    private var nativeAd: NativeAd? = null
    private var isBgmEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // 펀치홀 영역까지 화면 확장 (갤럭시 S20 등 대응)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // 전체 화면 및 펀치홀 확장 적용 (안정적인 방식으로 수정)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val backgroundColor = if (isDarkMode) Color.BLACK else Color.WHITE

        initializeAds()

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(backgroundColor)
        }

        raceView = RaceView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            )
        }

        val adWidthPx = (160 * resources.displayMetrics.density + 0.5f).toInt()
        val nativeAdView = layoutInflater.inflate(R.layout.layout_native_ad, null) as NativeAdView
        nativeAdView.layoutParams = LinearLayout.LayoutParams(
            adWidthPx,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        rootLayout.addView(raceView)
        rootLayout.addView(nativeAdView)
        setContentView(rootLayout)

        loadNativeAd(nativeAdView)
    }

    private fun initializeAds() {
        val testDeviceIds = listOf("2128B2410594C4F06D59AAECFA40A410")
        val configuration = RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build()
        MobileAds.setRequestConfiguration(configuration)
        MobileAds.initialize(this) {}
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    fun startBgm() {
        if (!isBgmEnabled) return
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer.create(this, R.raw.race_bgm)
                mediaPlayer?.isLooping = true
            }
            if (mediaPlayer?.isPlaying == false) mediaPlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopBgm() {
        mediaPlayer?.pause()
        mediaPlayer?.seekTo(0)
    }

    fun toggleBgm() {
        isBgmEnabled = !isBgmEnabled
        if (!isBgmEnabled) {
            stopBgm()
        } else if (raceView.isRacing()) {
            startBgm()
        }
    }

    fun isBgmEnabled(): Boolean = isBgmEnabled

    override fun onPause() {
        super.onPause()
        mediaPlayer?.pause()
    }

    override fun onResume() {
        super.onResume()
        if (isBgmEnabled && raceView.isRacing() && !raceView.isPaused()) {
            mediaPlayer?.start()
        }
    }

    fun showPauseDialog() {
        val dialogView = createPauseDialogView()
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        setupPauseDialogButtons(dialogView, dialog)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun createPauseDialogView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 60, 60, 60)
            setBackgroundColor(Color.parseColor("#2C2C2C"))

            addView(createPauseTitle())
            addView(createResumeButton())
            addView(createMenuButton())
        }
    }

    private fun createPauseTitle() = TextView(this).apply {
        text = "PAUSED"
        textSize = 35f
        setTextColor(Color.WHITE)
        gravity = android.view.Gravity.CENTER
        setPadding(0, 0, 0, 40)
        typeface = ResourcesCompat.getFont(context, R.font.pfstardust)
    }

    private fun createResumeButton() = Button(this).apply {
        text = "RESUME"
        textSize = 20f
        setBackgroundColor(Color.parseColor("#4CAF50"))
        setTextColor(Color.WHITE)
        typeface = ResourcesCompat.getFont(context, R.font.pfstardust)
    }

    private fun createMenuButton() = Button(this).apply {
        text = "EXIT TO MENU"
        textSize = 20f
        setBackgroundColor(Color.parseColor("#F44336"))
        setTextColor(Color.WHITE)
        typeface = ResourcesCompat.getFont(context, R.font.pfstardust)
        setPadding(0, 30, 0, 0)
    }

    private fun setupPauseDialogButtons(dialogView: LinearLayout, dialog: AlertDialog) {
        val btnResume = dialogView.getChildAt(1) as Button
        val btnMenu = dialogView.getChildAt(2) as Button

        btnResume.setOnClickListener {
            raceView.togglePause()
            dialog.dismiss()
        }

        btnMenu.setOnClickListener {
            raceView.togglePause()
            raceView.resetGame()
            stopBgm()
            dialog.dismiss()
        }
    }

    fun showResultDialog() {
        val results = raceView.getResults()
        val dialogView = layoutInflater.inflate(R.layout.dialog_result, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        setupResultDialog(dialogView, results, dialog)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun setupResultDialog(dialogView: View, results: List<Animal>, dialog: AlertDialog) {
        val txtMsg = dialogView.findViewById<TextView>(R.id.txtResultMessage)
        val layoutLeftColumn = dialogView.findViewById<android.widget.LinearLayout>(R.id.layoutLeftColumn)
        val layoutRightColumn = dialogView.findViewById<android.widget.LinearLayout>(R.id.layoutRightColumn)
        val btnRematch = dialogView.findViewById<Button>(R.id.btnRematch)

        txtMsg.text = "RACE OVER"
        txtMsg.visibility = View.VISIBLE

        layoutLeftColumn.removeAllViews()
        layoutRightColumn.removeAllViews()

        val getOrdinal = { n: Int ->
            when {
                n % 100 in 11..13 -> "${n}th"
                n % 10 == 1 -> "${n}st"
                n % 10 == 2 -> "${n}nd"
                n % 10 == 3 -> "${n}rd"
                else -> "${n}th"
            }
        }

        results.forEachIndexed { index, animal ->
            val itemLayout = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(2f), 0, dpToPx(2f))
            }

            val rankText = TextView(this).apply {
                text = getOrdinal(animal.rank)
                textSize = 18f
                setTextColor(Color.WHITE)
                typeface = androidx.core.content.res.ResourcesCompat.getFont(this@MainActivity, R.font.pfstardust)
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    dpToPx(55f), android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dpToPx(6f)
                }
                textAlignment = View.TEXT_ALIGNMENT_TEXT_END
            }

            val animalBitmap = raceView.getAnimalBitmap(animal.emoji)
            val animalView = if (animalBitmap != null) {
                android.widget.ImageView(this).apply {
                    setImageBitmap(animalBitmap)
                    val iconSize = dpToPx(28f)
                    layoutParams = android.widget.LinearLayout.LayoutParams(iconSize, iconSize)
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                }
            } else {
                TextView(this).apply {
                    text = animal.emoji
                    textSize = 22f
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
            }

            itemLayout.addView(rankText)
            itemLayout.addView(animalView)

            if (index < 5) {
                layoutLeftColumn.gravity = android.view.Gravity.END
                layoutLeftColumn.addView(itemLayout)
            } else {
                layoutRightColumn.gravity = android.view.Gravity.START
                layoutRightColumn.addView(itemLayout)
            }
        }

        btnRematch.setOnClickListener {
            raceView.resetGame()
            dialog.dismiss()
        }
    }

    override fun onDestroy() {
        nativeAd?.destroy()
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun loadNativeAd(adView: NativeAdView) {
        val builder = AdLoader.Builder(this, "ca-app-pub-1141162708477405/7386462230")
        builder.forNativeAd { ad ->
            nativeAd?.destroy()
            nativeAd = ad
            populateNativeAdView(ad, adView)
        }
        val adOptions = NativeAdOptions.Builder().build()
        builder.withNativeAdOptions(adOptions)
        val adLoader = builder.build()
        adLoader.loadAd(AdRequest.Builder().build())
    }

    private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
        adView.headlineView = adView.findViewById(R.id.ad_headline)
        adView.bodyView = adView.findViewById(R.id.ad_body)
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        adView.iconView = adView.findViewById(R.id.ad_app_icon)
        adView.advertiserView = adView.findViewById(R.id.ad_advertiser)

        (adView.headlineView as TextView).text = nativeAd.headline

        if (nativeAd.body == null) {
            adView.bodyView?.visibility = View.INVISIBLE
        } else {
            adView.bodyView?.visibility = View.VISIBLE
            (adView.bodyView as TextView).text = nativeAd.body
        }

        if (nativeAd.callToAction == null) {
            adView.callToActionView?.visibility = View.INVISIBLE
        } else {
            adView.callToActionView?.visibility = View.VISIBLE
            (adView.callToActionView as Button).text = nativeAd.callToAction
        }

        if (nativeAd.icon == null) {
            adView.iconView?.visibility = View.GONE
        } else {
            (adView.iconView as ImageView).setImageDrawable(nativeAd.icon?.drawable)
            adView.iconView?.visibility = View.VISIBLE
        }

        if (nativeAd.advertiser == null) {
            adView.advertiserView?.visibility = View.INVISIBLE
        } else {
            (adView.advertiserView as TextView).text = nativeAd.advertiser
            adView.advertiserView?.visibility = View.VISIBLE
        }

        adView.setNativeAd(nativeAd)
    }

    // --- 커스텀 뷰 클래스 ---
    inner class RaceView(context: Context) : View(context) {
        private val customTypeface: Typeface? = ResourcesCompat.getFont(context, R.font.pfstardust)
        private val paint = Paint().apply {
            textAlign = Paint.Align.CENTER
            typeface = customTypeface
            isAntiAlias = true
        }

        private val grassDark = Color.parseColor("#2E7D32")
        private val grassLight = Color.parseColor("#388E3C")

        // 사막 모래 색상으로 변경
        private val sandBase = Color.parseColor("#E8D4A0")        // 밝은 모래색
        private val sandLight = Color.parseColor("#F5E6C8")       // 더 밝은 모래색
        private val sandDark = Color.parseColor("#D4B896")        // 어두운 모래색
        private val sandDot = Color.parseColor("#C9A876")         // 모래 점 색상

        private var sandBackgroundBitmap: Bitmap? = null

        private val colorButton = Color.parseColor("#FFC107")

        private var currentMapType = MapType.GRASS

        // 모래 텍스처용 랜덤 점들 (한 번만 생성)
        private var sandParticles = mutableListOf<SandParticle>()

        private val puddlePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        private val bitmapPaint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }

        private val animalImageMap = mapOf(
            "🦖" to "dinosaur", "🐑" to "sheep", "🫏" to "zebra", "🐢" to "turtle", "🐧" to "penguin",
            "🐯" to "tiger", "🐺" to "fox", "🐻" to "dog", "🦁" to "giraffe", "🦍" to "duck"
        )

        private val animalBitmaps = mutableMapOf<String, Bitmap>()
        private var rocketBitmap: Bitmap? = null

        private val allAnimals = mutableListOf(
            Animal("🦖"), Animal("🐑"), Animal("🫏"), Animal("🐢"), Animal("🐧"),
            Animal("🐯"), Animal("🐺"), Animal("🐻"), Animal("🦁"), Animal("🦍")
        )

        private var racingAnimals = mutableListOf<Animal>()
        private var displayLaneCount = 4  // 최소 4레인 표시
        private val trackObjects = mutableListOf<MutableList<TrackObject>>()
        private var currentGameState = GameState.INTRO
        private var isRacing = false
        private var rankCounter = 1
        private var frameCounter = 0
        private var trackLength = 0f
        private var cameraX = 0f
        private var isPaused = false

        // Countdown
        private var countdownValue = 3
        private var countdownFrameCounter = 0
        private var countdownAlpha = 255f
        private var countdownScale = 3f

        // Celebration
        private val celebrationParticles = mutableListOf<CelebrationParticle>()
        private var showCelebration = false

        // Parallax
        private var parallaxFar = mutableListOf<ParallaxElement>()
        private var parallaxMid = mutableListOf<ParallaxElement>()
        private var parallaxNear = mutableListOf<ParallaxElement>()

        // Shimmer & intro animation
        private var shimmerOffset = 0f
        private var introAnimFrame = 0

        private val handler = Handler(Looper.getMainLooper())
        private val updateRunnable = object : Runnable {
            override fun run() {
                updateLogic()
                invalidate()
                handler.postDelayed(this, FRAME_DELAY)
            }
        }

        init {
            handler.post(updateRunnable)
            loadAnimalBitmaps()
            loadRocketBitmap()
        }

        fun togglePause() {
            isPaused = !isPaused
            invalidate()
        }

        fun isPaused(): Boolean = isPaused
        fun isRacing(): Boolean = isRacing
        fun getAnimalBitmap(emoji: String): Bitmap? = animalBitmaps[emoji]
        fun getWinner(): Animal? = racingAnimals.find { it.rank == 1 }
        fun getResults(): List<Animal> = racingAnimals.sortedBy { it.rank }

        fun resetGame() {
            allAnimals.forEach {
                it.isSelected = false
                it.progress = 0f
                it.rank = 0
                it.state = RaceState.RUNNING
            }
            currentGameState = GameState.INTRO
            isRacing = false
            showCelebration = false
            celebrationParticles.clear()
            invalidate()
        }

        private fun loadAnimalBitmaps() {
            animalImageMap.forEach { (emoji, imageName) ->
                try {
                    val resourceId = resources.getIdentifier(imageName, "drawable", context.packageName)
                    if (resourceId != 0) {
                        val original = BitmapFactory.decodeResource(resources, resourceId)
                        val scaled = Bitmap.createScaledBitmap(original, ANIMAL_SIZE, ANIMAL_SIZE, true)
                        animalBitmaps[emoji] = scaled
                        if (original != scaled) original.recycle()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private fun loadRocketBitmap() {
            try {
                val resourceId = resources.getIdentifier("rocket", "drawable", context.packageName)
                if (resourceId != 0) {
                    val original = BitmapFactory.decodeResource(resources, resourceId)
                    rocketBitmap = Bitmap.createScaledBitmap(original, ROCKET_SIZE, ROCKET_SIZE, true)
                    if (original != rocketBitmap) original.recycle()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun getAnimalSize(): Int {
            val baseSize = 80
            val maxSize = 150
            val laneHeight = height.toFloat() / displayLaneCount.coerceAtLeast(1)

            // 레인 높이의 60%를 동물 크기로 사용 (최소 80, 최대 150)
            val dynamicSize = (laneHeight * 0.6f).toInt()
            return dynamicSize.coerceIn(baseSize, maxSize)
        }

        private fun drawAnimal(canvas: Canvas, emoji: String, x: Float, y: Float, rotation: Float, size: Int? = null) {
            val bitmap = animalBitmaps[emoji] ?: return

            // 동적 크기 계산
            val targetSize = size ?: ANIMAL_SIZE

            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(rotation)

            // 비트맵 스케일링
            if (bitmap.width != targetSize || bitmap.height != targetSize) {
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)
                canvas.drawBitmap(scaledBitmap, -scaledBitmap.width / 2f, -scaledBitmap.height / 2f, bitmapPaint)
                if (scaledBitmap != bitmap) scaledBitmap.recycle()
            } else {
                canvas.drawBitmap(bitmap, -bitmap.width / 2f, -bitmap.height / 2f, bitmapPaint)
            }

            canvas.restore()
        }

        private fun drawPuddle(canvas: Canvas, x: Float, y: Float, scale: Float = 1f) {
            val path = Path().apply {
                moveTo(x - 30f * scale, y)
                cubicTo(x - 35f * scale, y - 15f * scale, x - 20f * scale, y - 20f * scale, x, y - 18f * scale)
                cubicTo(x + 20f * scale, y - 16f * scale, x + 35f * scale, y - 10f * scale, x + 32f * scale, y + 2f * scale)
                cubicTo(x + 28f * scale, y + 15f * scale, x + 15f * scale, y + 20f * scale, x, y + 18f * scale)
                cubicTo(x - 15f * scale, y + 16f * scale, x - 28f * scale, y + 10f * scale, x - 30f * scale, y)
                close()
            }

            puddlePaint.color = Color.parseColor("#4A90E2")
            puddlePaint.alpha = 200
            canvas.drawPath(path, puddlePaint)

            val highlightPath = Path().apply {
                moveTo(x - 10f * scale, y - 8f * scale)
                cubicTo(x - 5f * scale, y - 12f * scale, x + 5f * scale, y - 10f * scale, x + 10f * scale, y - 5f * scale)
                cubicTo(x + 8f * scale, y, x - 8f * scale, y - 2f * scale, x - 10f * scale, y - 8f * scale)
                close()
            }

            puddlePaint.color = Color.parseColor("#87CEEB")
            puddlePaint.alpha = 180
            canvas.drawPath(highlightPath, puddlePaint)
        }

        private fun drawBoostItem(canvas: Canvas, x: Float, y: Float, size: Int = ROCKET_SIZE) {
            rocketBitmap?.let { bitmap ->
                val scaledBitmap = if (bitmap.width != size || bitmap.height != size) {
                    Bitmap.createScaledBitmap(bitmap, size, size, true)
                } else {
                    bitmap
                }
                canvas.drawBitmap(scaledBitmap, x - scaledBitmap.width / 2f, y - scaledBitmap.height / 2f, bitmapPaint)
                if (scaledBitmap != bitmap) scaledBitmap.recycle()
            }
        }

        private fun getOrdinal(n: Int): String = when {
            n % 100 in 11..13 -> "${n}th"
            n % 10 == 1 -> "${n}st"
            n % 10 == 2 -> "${n}nd"
            n % 10 == 3 -> "${n}rd"
            else -> "${n}th"
        }

        // 모래 입자 생성 함수 추가
        private fun generateSandParticles() {
            sandParticles.clear()
            val particleCount = (trackLength * height / 100).toInt()  // 밀도 조절

            repeat(particleCount) {
                sandParticles.add(
                    SandParticle(
                        x = Random.nextFloat() * trackLength,
                        y = Random.nextFloat() * height,
                        size = Random.nextFloat() * 3f + 1f,  // 1~4 크기
                        alpha = Random.nextInt(30, 100)        // 30~100 투명도
                    )
                )
            }
        }

        // drawTrackBackground 수정
        private fun drawTrackBackground(canvas: Canvas) {
            when (currentMapType) {
                MapType.GRASS -> drawGrassBackground(canvas)
                MapType.DIRT -> drawSandBackground(canvas)
            }
        }

        // 잔디 배경 (기존 방식)
        private fun drawGrassBackground(canvas: Canvas) {
            val stripeWidth = width / STRIPE_DIVISOR
            val offset = cameraX % (stripeWidth * 2)

            for (i in 0..25) {
                paint.color = if (i % 2 == 0) grassDark else grassLight
                canvas.drawRect(
                    i * stripeWidth - offset,
                    0f,
                    (i + 1) * stripeWidth - offset,
                    height.toFloat(),
                    paint
                )
            }
        }

        // 모래 배경 - 비트맵 타일링으로 매우 가볍게
        private fun drawSandBackground(canvas: Canvas) {
            val bitmap = sandBackgroundBitmap ?: return

            // 베이스 색상
            paint.style = Paint.Style.FILL
            paint.color = sandBase
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

            // 비트맵 타일링 (반복)
            canvas.save()
            canvas.translate(-cameraX, 0f)

            val tileCount = (trackLength / width).toInt() + 2
            paint.alpha = 255
            for (i in 0 until tileCount) {
                canvas.drawBitmap(bitmap, i * width.toFloat(), 0f, bitmapPaint)
            }

            canvas.restore()
        }


        // 메모리 해제 (onDetachedFromWindow에 추가)
        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            sandBackgroundBitmap?.recycle()
            sandBackgroundBitmap = null
        }


        // onSizeChanged 수정
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            trackLength = w * 4.0f

            // 모래 배경 비트맵 생성
            generateSandBackgroundBitmap()
        }

        private fun generateSandBackgroundBitmap() {
            // 기존 비트맵 해제
            sandBackgroundBitmap?.recycle()

            // 화면 크기의 타일 비트맵 생성 (반복 패턴용)
            val tileWidth = width
            val tileHeight = height

            sandBackgroundBitmap = Bitmap.createBitmap(tileWidth, tileHeight, Bitmap.Config.RGB_565)
            val canvas = Canvas(sandBackgroundBitmap!!)

            // 베이스 색상
            paint.style = Paint.Style.FILL
            paint.color = sandBase
            canvas.drawRect(0f, 0f, tileWidth.toFloat(), tileHeight.toFloat(), paint)

            // 작은 점들만 추가 (개수 줄임)
            paint.color = sandDot
            repeat(200) {  // 200개로 줄임
                val x = Random.nextFloat() * tileWidth
                val y = Random.nextFloat() * tileHeight
                val size = Random.nextFloat() * 2f + 1f
                val alpha = Random.nextInt(30, 80)
                paint.alpha = alpha
                canvas.drawCircle(x, y, size, paint)
            }

            // 밝은 점들
            paint.color = sandLight
            repeat(100) {  // 100개로 줄임
                val x = Random.nextFloat() * tileWidth
                val y = Random.nextFloat() * tileHeight
                val size = Random.nextFloat() * 2f + 0.5f
                val alpha = Random.nextInt(20, 60)
                paint.alpha = alpha
                canvas.drawCircle(x, y, size, paint)
            }

            paint.alpha = 255
        }

        override fun onTouchEvent(event: MotionEvent?): Boolean {
            if (event?.action == MotionEvent.ACTION_DOWN) {
                when (currentGameState) {
                    GameState.INTRO -> handleIntroTouch(event.x, event.y)
                    GameState.RACING -> handleRacingTouch(event.x, event.y)
                    else -> {}
                }
            }
            return true
        }

        private fun handleRacingTouch(tx: Float, ty: Float) {
            val btnY = BUTTON_PADDING

            // BGM 버튼 (좌측 상단 첫 번째)
            val bgmBtnX = BUTTON_PADDING
            if (tx in bgmBtnX..(bgmBtnX + BUTTON_SIZE) && ty in btnY..(btnY + BUTTON_SIZE)) {
                this@MainActivity.toggleBgm()
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                invalidate()
                return
            }

            // 일시정지 버튼 (BGM 버튼 오른쪽)
            val pauseBtnX = bgmBtnX + BUTTON_SIZE + BUTTON_GAP
            if (tx in pauseBtnX..(pauseBtnX + BUTTON_SIZE) && ty in btnY..(btnY + BUTTON_SIZE)) {
                togglePause()
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (isPaused) this@MainActivity.showPauseDialog()
                return
            }
        }

        private fun handleIntroTouch(tx: Float, ty: Float) {
            val h = height.toFloat()
            val cellGap = 15f
            val availableHeight = h * 0.50f
            val cellSizeFromHeight = (availableHeight - cellGap) / 2f
            val cellSizeFromWidth = (width * 0.65f - cellGap * 4) / 5f
            val cellSize = min(cellSizeFromWidth, cellSizeFromHeight)
            val totalGridWidth = cellSize * 5 + cellGap * 4
            val startX = (width - totalGridWidth) / 2f
            val startY = h * 0.18f

            for (i in allAnimals.indices) {
                val r = i / 5
                val c = i % 5
                val left = startX + c * (cellSize + cellGap)
                val top = startY + r * (cellSize + cellGap)

                if (tx in left..(left + cellSize) && ty in top..(top + cellSize)) {
                    allAnimals[i].isSelected = !allAnimals[i].isSelected
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    invalidate()
                    return
                }
            }

            // 맵 선택 버튼 터치 처리
            val mapBtnW = width * 0.18f
            val mapBtnH = h * 0.09f
            val mapBtnY = h * 0.73f
            val mapBtnGap = 20f
            val totalMapBtnWidth = mapBtnW * 2 + mapBtnGap
            val mapBtnStartX = (width - totalMapBtnWidth) / 2f

            // GRASS 버튼
            val grassLeft = mapBtnStartX
            if (tx in grassLeft..(grassLeft + mapBtnW) && ty in mapBtnY..(mapBtnY + mapBtnH)) {
                currentMapType = MapType.GRASS
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                invalidate()
                return
            }

            // DIRT 버튼
            val dirtLeft = mapBtnStartX + mapBtnW + mapBtnGap
            if (tx in dirtLeft..(dirtLeft + mapBtnW) && ty in mapBtnY..(mapBtnY + mapBtnH)) {
                currentMapType = MapType.DIRT
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                invalidate()
                return
            }

            // Start button
            val btnW = width * 0.3f
            val bT = h * 0.86f
            val btnH = h * 0.10f
            val btnL = (width - btnW) / 2f
            if (allAnimals.count { it.isSelected } >= 2 &&
                tx in btnL..(btnL + btnW) && ty in bT..(bT + btnH)) {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                startRace()
            }
        }

        private fun startRace() {
            // 맵은 인트로에서 이미 선택됨 (currentMapType)

            racingAnimals.clear()
            trackObjects.clear()

            allAnimals.filter { it.isSelected }.forEach {
                racingAnimals.add(it.copy(speed = 0.0013f + Random.nextFloat() * 0.0002f))
                val objects = MutableList(Random.nextInt(3, 7)) {
                    TrackObject(Random.nextFloat() * 0.8f + 0.1f, Random.nextBoolean())
                }.apply { sortBy { it.progress } }
                trackObjects.add(objects)
            }

            // 4명 이하일 때도 최소 4레인 사용
            displayLaneCount = maxOf(racingAnimals.size, 4)

            currentGameState = GameState.COUNTDOWN
            isRacing = false
            rankCounter = 1
            frameCounter = 0
            cameraX = 0f
            countdownValue = 3
            countdownFrameCounter = 0
            countdownAlpha = 255f
            countdownScale = 3f
            showCelebration = false
            celebrationParticles.clear()
            generateParallaxElements()
        }


        private fun updateLogic() {
            if (isPaused) return
            frameCounter++

            // Shimmer & intro animation (always runs)
            shimmerOffset += 0.008f
            if (shimmerOffset > 1f) shimmerOffset = 0f
            introAnimFrame++

            when (currentGameState) {
                GameState.COUNTDOWN -> updateCountdown()
                GameState.RACING -> if (isRacing) updateRace()
                else -> {}
            }
            if (showCelebration) updateCelebrationParticles()
        }

        private fun updateRace() {
            var finishedCount = 0
            val leaderProgress = racingAnimals.maxOfOrNull { it.progress } ?: 0f

            racingAnimals.forEachIndexed { i, animal ->
                if (animal.progress >= 1f) {
                    finishedCount++
                    return@forEachIndexed
                }

                checkCollisions(animal, i)
                updateAnimalState(animal, i, leaderProgress)

                if (animal.progress >= 1f && animal.rank == 0) {
                    animal.progress = 1f
                    animal.rank = rankCounter++
                    if (animal.rank == 1) {
                        spawnCelebration(i)
                    }
                }
            }

            updateCamera()

            if (finishedCount == racingAnimals.size && racingAnimals.isNotEmpty()) {
                finishRace()
            }
        }

        private fun checkCollisions(animal: Animal, trackIndex: Int) {
            trackObjects[trackIndex].forEach { obj ->
                if (obj.isActive && Math.abs(animal.progress - obj.progress) < COLLISION_THRESHOLD) {
                    if (obj.isItem) {
                        animal.state = RaceState.BOOST
                        animal.stateDuration = BOOST_DURATION
                        animal.currentBoostPower = 1.15f + Random.nextFloat() * 0.15f
                    } else {
                        animal.state = RaceState.STUNNED
                        animal.stateDuration = STUN_DURATION
                        animal.rotation = 0f
                    }
                    obj.isActive = false
                }
            }
        }

        private fun updateAnimalState(animal: Animal, index: Int, leaderProgress: Float) {
            // Update state duration
            if (animal.stateDuration > 0) {
                animal.stateDuration--
                if (animal.stateDuration <= 0) {
                    animal.state = RaceState.RUNNING
                    animal.rotation = 0f
                }

                if (animal.state == RaceState.STUNNED && animal.rotation < MAX_ROTATION) {
                    animal.rotation = min(animal.rotation + ROTATION_SPEED, MAX_ROTATION)
                }
            } else {
                animal.rotation = 0f
            }

            // Update bob animation
            val bobSpeed = if (animal.state == RaceState.BOOST) BOOST_BOB_SPEED else NORMAL_BOB_SPEED
            val bobAmplitude = if (animal.state == RaceState.STUNNED) 0f else BOB_AMPLITUDE
            animal.bobOffset = sin(frameCounter * bobSpeed + index) * bobAmplitude

            // Update progress with enhanced catch-up mechanics
            val progressGap = leaderProgress - animal.progress

            // 기존 캐치업 속도
            val catchUpSpeed = if (progressGap > CATCHUP_DISTANCE && animal.state == RaceState.RUNNING) {
                CATCHUP_SPEED
            } else {
                0f
            }

            // 순위 기반 추가 속도 (뒤쪽 순위일수록 더 빠름)
            val rankBonus = if (animal.state == RaceState.RUNNING) {
                // 현재 순위 계산 (임시 순위)
                val currentRank = racingAnimals.count { it.progress > animal.progress } + 1
                val totalAnimals = racingAnimals.size

                // 뒤에서 30% 안에 있으면 보너스 부여
                if (currentRank > totalAnimals * 0.7f) {
                    val rankRatio = (currentRank - totalAnimals * 0.7f) / (totalAnimals * 0.3f)
                    0.0001f * rankRatio  // 최대 0.0001f 추가 속도
                } else {
                    0f
                }
            } else {
                0f
            }

            val finalSpeed = when (animal.state) {
                RaceState.BOOST -> animal.speed * animal.currentBoostPower
                RaceState.STUNNED -> 0f
                else -> animal.speed
            }

            animal.progress += (finalSpeed + catchUpSpeed + rankBonus)
        }

        private fun updateCamera() {
            val maxProgress = racingAnimals.maxOfOrNull { it.progress } ?: 0f
            val targetX = maxProgress * (trackLength - FINISH_LINE_OFFSET) - (width * 0.6f)
            cameraX = max(0f, min(targetX, trackLength - width))
        }

        private fun finishRace() {
            isRacing = false
            currentGameState = GameState.FINISHED
            this@MainActivity.showResultDialog()
            this@MainActivity.stopBgm()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            drawTrackBackground(canvas)  // 함수명 변경

            when (currentGameState) {
                GameState.INTRO -> drawIntro(canvas)
                GameState.COUNTDOWN -> {
                    drawRace(canvas)
                    drawCountdown(canvas)
                }
                else -> drawRace(canvas)
            }
        }


        private fun drawIntro(canvas: Canvas) {
            val h = height.toFloat()
            drawTitle(canvas, h)
            drawAnimalSelection(canvas, h)
            drawMapSelection(canvas, h)
            drawStartButton(canvas, h)
        }

        private fun drawTitle(canvas: Canvas, h: Float) {
            paint.textSize = h * 0.14f
            paint.style = Paint.Style.FILL_AND_STROKE
            paint.strokeWidth = 8f

            // Shadow
            paint.color = Color.parseColor("#FF6B35")
            canvas.drawText("ANIMAL RACE", width / 2f + 4f, h * 0.13f + 4f, paint)

            // Gradient text
            val baseShader = LinearGradient(
                0f, h * 0.10f, 0f, h * 0.15f,
                Color.parseColor("#FFD700"),
                Color.parseColor("#FF9800"),
                Shader.TileMode.CLAMP
            )
            paint.shader = baseShader
            canvas.drawText("ANIMAL RACE", width / 2f, h * 0.13f, paint)
            paint.shader = null

            // Shimmer highlight overlay
            val shimmerW = width * 0.15f
            val sx = shimmerOffset * (width + shimmerW) - shimmerW
            val shimmerShader = LinearGradient(
                sx, 0f, sx + shimmerW, 0f,
                intArrayOf(Color.TRANSPARENT, Color.argb(160, 255, 255, 255), Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            paint.shader = shimmerShader
            paint.alpha = 200
            canvas.drawText("ANIMAL RACE", width / 2f, h * 0.13f, paint)
            paint.shader = null
            paint.alpha = 255
            paint.style = Paint.Style.FILL
            paint.strokeWidth = 0f
        }

        private fun drawAnimalSelection(canvas: Canvas, h: Float) {
            val cellGap = 15f
            // 캐릭터 선택 영역: 0.18f ~ 0.68f (50% 영역)
            val availableHeight = h * 0.50f
            val cellSizeFromHeight = (availableHeight - cellGap) / 2f  // 2행
            val cellSizeFromWidth = (width * 0.65f - cellGap * 4) / 5f  // 5열
            val cellSize = min(cellSizeFromWidth, cellSizeFromHeight)
            val totalGridWidth = cellSize * 5 + cellGap * 4
            val totalGridHeight = cellSize * 2 + cellGap
            val startX = (width - totalGridWidth) / 2f
            val startY = h * 0.18f

            allAnimals.forEachIndexed { i, animal ->
                val r = i / 5
                val c = i % 5
                val left = startX + c * (cellSize + cellGap)
                val top = startY + r * (cellSize + cellGap)
                val rect = RectF(left, top, left + cellSize, top + cellSize)

                drawAnimalCell(canvas, animal, rect)
            }
        }

        private fun drawAnimalCell(canvas: Canvas, animal: Animal, rect: RectF) {
            val cx = rect.centerX()
            val cy = rect.centerY()

            // Bounce scale when selected
            val scale = if (animal.isSelected) {
                1.0f + sin(introAnimFrame * 0.08f) * 0.04f
            } else 1.0f

            canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(scale, scale)
            canvas.translate(-cx, -cy)

            // Glow behind selected
            if (animal.isSelected) {
                paint.color = Color.argb((80 + sin(introAnimFrame * 0.1f) * 30).toInt().coerceIn(0,255), 204, 255, 0)
                paint.style = Paint.Style.FILL
                paint.maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
                canvas.drawRoundRect(rect, 20f, 20f, paint)
                paint.maskFilter = null
            }

            // Background
            paint.color = if (animal.isSelected) Color.parseColor("#CCFFFFFF") else Color.parseColor("#B3000000")
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(rect, 20f, 20f, paint)

            // Animal image or emoji
            val bitmap = animalBitmaps[animal.emoji]
            if (bitmap != null) {
                val imageSize = min(rect.width(), rect.height()) * 0.7f
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, imageSize.toInt(), imageSize.toInt(), true)
                canvas.drawBitmap(
                    scaledBitmap,
                    rect.centerX() - scaledBitmap.width / 2f,
                    rect.centerY() - scaledBitmap.height / 2f,
                    bitmapPaint
                )
                if (scaledBitmap != bitmap) scaledBitmap.recycle()
            } else {
                paint.textSize = 70f
                canvas.drawText(animal.emoji, rect.centerX(), rect.centerY() + 25f, paint)
            }

            // Selection border
            if (animal.isSelected) {
                paint.style = Paint.Style.STROKE
                paint.color = Color.parseColor("#CCFF00")
                paint.strokeWidth = 8f
                canvas.drawRoundRect(rect, 20f, 20f, paint)
            }

            canvas.restore()
        }

        private fun drawMapSelection(canvas: Canvas, h: Float) {
            val mapBtnW = width * 0.18f
            val mapBtnH = h * 0.09f
            val mapBtnY = h * 0.73f
            val mapBtnGap = 20f
            val totalMapBtnWidth = mapBtnW * 2 + mapBtnGap
            val mapBtnStartX = (width - totalMapBtnWidth) / 2f

            // 라벨
            paint.textSize = h * 0.045f
            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER

            // GRASS 버튼
            val grassRect = RectF(mapBtnStartX, mapBtnY, mapBtnStartX + mapBtnW, mapBtnY + mapBtnH)
            paint.style = Paint.Style.FILL
            paint.color = if (currentMapType == MapType.GRASS) Color.parseColor("#4CAF50") else Color.parseColor("#66000000")
            canvas.drawRoundRect(grassRect, 15f, 15f, paint)
            if (currentMapType == MapType.GRASS) {
                paint.style = Paint.Style.STROKE
                paint.color = Color.parseColor("#CCFF00")
                paint.strokeWidth = 5f
                canvas.drawRoundRect(grassRect, 15f, 15f, paint)
            }
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.textSize = h * 0.04f
            canvas.drawText("🌿 GRASS", grassRect.centerX(), grassRect.centerY() + 10f, paint)

            // DIRT 버튼
            val dirtRect = RectF(mapBtnStartX + mapBtnW + mapBtnGap, mapBtnY, mapBtnStartX + mapBtnW * 2 + mapBtnGap, mapBtnY + mapBtnH)
            paint.style = Paint.Style.FILL
            paint.color = if (currentMapType == MapType.DIRT) Color.parseColor("#D4A056") else Color.parseColor("#66000000")
            canvas.drawRoundRect(dirtRect, 15f, 15f, paint)
            if (currentMapType == MapType.DIRT) {
                paint.style = Paint.Style.STROKE
                paint.color = Color.parseColor("#CCFF00")
                paint.strokeWidth = 5f
                canvas.drawRoundRect(dirtRect, 15f, 15f, paint)
            }
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.textSize = h * 0.04f
            canvas.drawText("🏜️ DIRT", dirtRect.centerX(), dirtRect.centerY() + 10f, paint)
        }

        private fun drawStartButton(canvas: Canvas, h: Float) {
            val btnW = width * 0.3f
            val bL = (width - btnW) / 2f
            val bT = h * 0.86f
            val btnH = h * 0.10f
            val rect = RectF(bL, bT, bL + btnW, bT + btnH)

            paint.style = Paint.Style.FILL
            paint.color = if (allAnimals.count { it.isSelected } >= 2) colorButton else Color.GRAY
            canvas.drawRoundRect(rect, 50f, 50f, paint)

            paint.style = Paint.Style.FILL_AND_STROKE
            paint.strokeWidth = 3f
            paint.color = Color.BLACK
            paint.textSize = h * 0.065f
            canvas.drawText("START RACE", width / 2f, bT + btnH * 0.7f, paint)
            paint.style = Paint.Style.FILL
            paint.strokeWidth = 0f
        }

        private fun drawRace(canvas: Canvas) {
            drawParallaxLayers(canvas)

            canvas.save()
            canvas.translate(-cameraX, 0f)

            val rH = height.toFloat() / displayLaneCount
            val fX = trackLength - FINISH_LINE_OFFSET

            drawFinishLine(canvas, fX)
            drawLanes(canvas, rH, fX)

            canvas.restore()

            drawRanks(canvas, height.toFloat() / displayLaneCount, fX)
            drawControlButtons(canvas)
            if (showCelebration) drawCelebration(canvas)
        }

        private fun drawRanks(canvas: Canvas, rowHeight: Float, finishX: Float) {
            val animalSize = getAnimalSize()

            // Paint 설정 백업
            val originalAlign = paint.textAlign
            paint.textAlign = Paint.Align.LEFT  // 계산을 위해 LEFT로 변경
            paint.style = Paint.Style.FILL

            racingAnimals.forEachIndexed { i, animal ->
                if (animal.rank > 0) {
                    val centerY = (i * rowHeight) + (rowHeight / 2f)

                    // 동물의 화면 좌표
                    val animalWorldX = animal.progress * finishX
                    val animalScreenX = animalWorldX - cameraX

                    // 순위 텍스트 위치
                    val rankX = animalScreenX + 0.2f;// - 0.8f; //1.3f
                    val rankY = centerY;

                    val rankText = getOrdinal(animal.rank)

                    // 텍스트 크기 측정
                    paint.textSize = animalSize * 0.625f
                    val textBounds = Rect()
                    paint.getTextBounds(rankText, 0, rankText.length, textBounds)

                    val padding = 8f

                    // 배경 그리기
                    paint.color = Color.parseColor("#CC000000")
                    canvas.drawRoundRect(
                        RectF(
                            rankX - padding,
                            rankY - textBounds.height() - padding,
                            rankX + textBounds.width() + padding,
                            rankY + padding
                        ),
                        8f, 8f, paint
                    )

                    // 텍스트 그리기
                    paint.color = Color.WHITE
                    canvas.drawText(rankText, rankX, rankY, paint)
                }
            }

            // Paint 설정 복원
            paint.textAlign = originalAlign
        }


        private fun drawFinishLine(canvas: Canvas, finishX: Float) {
            val checkerSize = 20f
            val numCols = 3
            val numRows = (height / checkerSize).toInt() + 1
            val startX = finishX - checkerSize * numCols / 2f

            paint.style = Paint.Style.FILL
            for (row in 0 until numRows) {
                for (col in 0 until numCols) {
                    paint.color = if ((row + col) % 2 == 0) Color.WHITE else Color.BLACK
                    canvas.drawRect(
                        startX + col * checkerSize,
                        row * checkerSize,
                        startX + (col + 1) * checkerSize,
                        (row + 1) * checkerSize,
                        paint
                    )
                }
            }
        }

        private fun drawLanes(canvas: Canvas, rowHeight: Float, finishX: Float) {
            val animalSize = getAnimalSize()
            val itemScale = animalSize / 80f

            // 1-6: Odd/even lane tinting
            for (lane in 0 until displayLaneCount) {
                if (lane % 2 == 1) {
                    paint.color = Color.argb(18, 0, 0, 0)
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(0f, lane * rowHeight, trackLength, (lane + 1) * rowHeight, paint)
                }
            }

            // 1-6: Dashed lane separators
            for (lane in 1 until displayLaneCount) {
                val y = lane * rowHeight
                paint.color = Color.WHITE
                paint.alpha = 100
                paint.strokeWidth = 4f
                paint.style = Paint.Style.STROKE
                val dashLen = 30f
                val gapLen = 20f
                var dx = 0f
                while (dx < trackLength) {
                    canvas.drawLine(dx, y, min(dx + dashLen, trackLength), y, paint)
                    dx += dashLen + gapLen
                }
                paint.style = Paint.Style.FILL
                paint.alpha = 255
            }

            // 1-6: Lane numbers on left side
            paint.textSize = rowHeight * 0.25f
            paint.textAlign = Paint.Align.CENTER
            paint.style = Paint.Style.FILL
            for (lane in 0 until displayLaneCount) {
                val cy = lane * rowHeight + rowHeight / 2f
                paint.color = Color.argb(100, 255, 255, 255)
                canvas.drawCircle(25f, cy, rowHeight * 0.18f, paint)
                paint.color = Color.argb(180, 0, 0, 0)
                canvas.drawText("${lane + 1}", 25f, cy + rowHeight * 0.09f, paint)
            }

            racingAnimals.forEachIndexed { i, animal ->
                val centerY = (i * rowHeight) + (rowHeight / 2f)

                // Track objects
                drawTrackObjects(canvas, i, finishX, centerY, itemScale)

                // 1-4: Boost afterimage (ghost trails)
                if (animal.state == RaceState.BOOST) {
                    for (g in 1..3) {
                        val ghostAlpha = (60 - g * 18).coerceAtLeast(10)
                        val ghostOffset = g * animalSize * 0.35f
                        bitmapPaint.alpha = ghostAlpha
                        canvas.save()
                        canvas.translate(animal.progress * finishX - ghostOffset, centerY + animal.bobOffset)
                        val bitmap = animalBitmaps[animal.emoji]
                        if (bitmap != null) {
                            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, animalSize, animalSize, true)
                            canvas.drawBitmap(scaledBitmap, -animalSize / 2f, -animalSize / 2f, bitmapPaint)
                            if (scaledBitmap != bitmap) scaledBitmap.recycle()
                        }
                        canvas.restore()
                        bitmapPaint.alpha = 255
                    }
                }

                // Animal
                canvas.save()
                canvas.translate(animal.progress * finishX, centerY + animal.bobOffset)
                canvas.rotate(animal.rotation)

                paint.alpha = 255
                drawAnimal(canvas, animal.emoji, 0f, 0f, 0f, animalSize)

                if (animal.state == RaceState.BOOST) {
                    paint.textSize = animalSize * 0.5f
                    canvas.drawText("🔥", -animalSize * 0.75f, -animalSize * 0.25f, paint)
                }

                // 1-4: Stun stars orbiting
                if (animal.state == RaceState.STUNNED) {
                    paint.textSize = animalSize * 0.3f
                    val starRadius = animalSize * 0.55f
                    for (s in 0..2) {
                        val starAngle = frameCounter * 0.12f + s * (Math.PI.toFloat() * 2f / 3f)
                        val sx = cos(starAngle) * starRadius
                        val sy = sin(starAngle) * starRadius - animalSize * 0.3f
                        canvas.drawText("⭐", sx, sy, paint)
                    }
                }

                canvas.restore()
            }
        }


        private fun drawTrackObjects(canvas: Canvas, trackIndex: Int, finishX: Float, centerY: Float, itemScale: Float) {
            trackObjects[trackIndex].forEach { obj ->
                if (obj.isActive) {
                    if (obj.isItem) {
                        val rocketSize = (ROCKET_SIZE * itemScale).toInt()
                        drawBoostItem(canvas, obj.progress * finishX, centerY, rocketSize)
                    } else {
                        drawPuddle(canvas, obj.progress * finishX, centerY, itemScale)
                    }
                }
            }
        }

        private fun drawControlButtons(canvas: Canvas) {
            val btnY = BUTTON_PADDING
            val bgmBtnX = BUTTON_PADDING
            val pauseBtnX = bgmBtnX + BUTTON_SIZE + BUTTON_GAP

            drawBgmButton(canvas, bgmBtnX, btnY)
            drawPauseButton(canvas, pauseBtnX, btnY)
        }

        private fun drawBgmButton(canvas: Canvas, x: Float, y: Float) {
            val rect = RectF(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE)

            paint.style = Paint.Style.FILL
            paint.color = if (this@MainActivity.isBgmEnabled()) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
            canvas.drawRoundRect(rect, 15f, 15f, paint)

            paint.color = Color.WHITE
            paint.textSize = 45f
            val icon = if (this@MainActivity.isBgmEnabled()) "🔊" else "🔇"
            canvas.drawText(icon, rect.centerX(), rect.centerY() + 15f, paint)
        }

        private fun drawPauseButton(canvas: Canvas, x: Float, y: Float) {
            val rect = RectF(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE)

            paint.style = Paint.Style.FILL
            paint.color = colorButton
            canvas.drawRoundRect(rect, 15f, 15f, paint)

            paint.color = Color.BLACK
            if (isPaused) {
                val trianglePath = Path().apply {
                    moveTo(x + 25f, y + 20f)
                    lineTo(x + 25f, y + 60f)
                    lineTo(x + 60f, y + 40f)
                    close()
                }
                canvas.drawPath(trianglePath, paint)
            } else {
                paint.strokeWidth = 8f
                canvas.drawLine(x + 25f, y + 20f, x + 25f, y + 60f, paint)
                canvas.drawLine(x + 55f, y + 20f, x + 55f, y + 60f, paint)
            }
        }

        // === COUNTDOWN ===
        private fun updateCountdown() {
            countdownFrameCounter++
            val framesPerStep = 55
            val progress = countdownFrameCounter.toFloat() / framesPerStep

            countdownScale = 3f - (2f * progress).coerceAtMost(2f)
            countdownAlpha = if (progress > 0.7f) ((1f - (progress - 0.7f) / 0.3f) * 255f) else 255f

            if (countdownFrameCounter >= framesPerStep) {
                countdownFrameCounter = 0
                countdownValue--
                countdownScale = 3f
                countdownAlpha = 255f

                if (countdownValue < 0) {
                    currentGameState = GameState.RACING
                    isRacing = true
                    this@MainActivity.startBgm()
                }
            }
        }

        private fun drawCountdown(canvas: Canvas) {
            paint.color = Color.BLACK
            paint.alpha = 100
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.alpha = 255

            val centerX = width / 2f
            val centerY = height / 2f
            val text = if (countdownValue > 0) countdownValue.toString() else "GO!"
            val baseSize = height * 0.4f

            canvas.save()
            canvas.translate(centerX, centerY)
            canvas.scale(countdownScale, countdownScale)

            paint.textSize = baseSize
            paint.textAlign = Paint.Align.CENTER
            paint.style = Paint.Style.FILL_AND_STROKE
            paint.strokeWidth = 8f
            paint.color = Color.BLACK
            paint.alpha = countdownAlpha.toInt().coerceIn(0, 255)
            canvas.drawText(text, 4f, baseSize * 0.35f + 4f, paint)

            val shader = LinearGradient(
                0f, -baseSize * 0.2f, 0f, baseSize * 0.4f,
                if (countdownValue > 0) Color.WHITE else Color.parseColor("#FFD700"),
                if (countdownValue > 0) Color.parseColor("#90CAF9") else Color.parseColor("#FF9800"),
                Shader.TileMode.CLAMP
            )
            paint.shader = shader
            paint.alpha = countdownAlpha.toInt().coerceIn(0, 255)
            canvas.drawText(text, 0f, baseSize * 0.35f, paint)
            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.strokeWidth = 0f
            paint.alpha = 255
            canvas.restore()

            if (countdownValue == 0 && countdownFrameCounter < 10) {
                paint.color = Color.WHITE
                paint.alpha = ((1f - countdownFrameCounter / 10f) * 120).toInt()
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.alpha = 255
            }
        }

        // === PARALLAX ===
        private fun generateParallaxElements() {
            parallaxFar.clear()
            parallaxMid.clear()
            parallaxNear.clear()
            val h = height.toFloat()

            when (currentMapType) {
                MapType.GRASS -> {
                    repeat(15) {
                        parallaxFar.add(ParallaxElement(
                            Random.nextFloat() * trackLength * 1.2f,
                            Random.nextFloat() * h,
                            Random.nextFloat() * 80f + 40f, 0,
                            Color.argb(25, 255, 255, 255)
                        ))
                    }
                    repeat(30) {
                        parallaxMid.add(ParallaxElement(
                            Random.nextFloat() * trackLength * 1.2f,
                            Random.nextFloat() * h,
                            Random.nextFloat() * 6f + 3f, 1,
                            if (Random.nextBoolean()) Color.argb(60, 255, 255, 100)
                            else Color.argb(60, 255, 200, 200)
                        ))
                    }
                    repeat(40) {
                        parallaxNear.add(ParallaxElement(
                            Random.nextFloat() * trackLength * 1.2f,
                            Random.nextFloat() * h,
                            Random.nextFloat() * 8f + 4f, 2,
                            Color.argb(35, 0, 80, 0)
                        ))
                    }
                }
                MapType.DIRT -> {
                    repeat(12) {
                        parallaxFar.add(ParallaxElement(
                            Random.nextFloat() * trackLength * 1.2f,
                            Random.nextFloat() * h,
                            Random.nextFloat() * 100f + 50f, 0,
                            Color.argb(20, 255, 220, 150)
                        ))
                    }
                    repeat(25) {
                        parallaxMid.add(ParallaxElement(
                            Random.nextFloat() * trackLength * 1.2f,
                            Random.nextFloat() * h,
                            Random.nextFloat() * 5f + 2f, 1,
                            Color.argb(50, 160, 120, 80)
                        ))
                    }
                    repeat(20) {
                        parallaxNear.add(ParallaxElement(
                            Random.nextFloat() * trackLength * 1.2f,
                            Random.nextFloat() * h,
                            Random.nextFloat() * 30f + 15f, 2,
                            Color.argb(25, 200, 180, 140)
                        ))
                    }
                }
            }
        }

        private fun drawParallaxLayers(canvas: Canvas) {
            paint.style = Paint.Style.FILL

            canvas.save()
            canvas.translate(-cameraX * 0.15f, 0f)
            parallaxFar.forEach { elem ->
                paint.color = elem.color
                canvas.drawCircle(elem.x, elem.y, elem.size, paint)
            }
            canvas.restore()

            canvas.save()
            canvas.translate(-cameraX * 0.4f, 0f)
            parallaxMid.forEach { elem ->
                paint.color = elem.color
                canvas.drawCircle(elem.x, elem.y, elem.size, paint)
                canvas.drawCircle(elem.x + elem.size, elem.y - elem.size * 0.5f, elem.size * 0.7f, paint)
                canvas.drawCircle(elem.x - elem.size * 0.5f, elem.y + elem.size * 0.3f, elem.size * 0.8f, paint)
            }
            canvas.restore()

            canvas.save()
            canvas.translate(-cameraX * 0.7f, 0f)
            parallaxNear.forEach { elem ->
                paint.color = elem.color
                when (currentMapType) {
                    MapType.GRASS -> {
                        paint.strokeWidth = 2f
                        paint.style = Paint.Style.STROKE
                        canvas.drawLine(elem.x, elem.y, elem.x - 2f, elem.y - elem.size, paint)
                        canvas.drawLine(elem.x, elem.y, elem.x + 2f, elem.y - elem.size * 0.8f, paint)
                        canvas.drawLine(elem.x, elem.y, elem.x, elem.y - elem.size * 1.1f, paint)
                        paint.style = Paint.Style.FILL
                    }
                    MapType.DIRT -> {
                        paint.strokeWidth = 1.5f
                        paint.style = Paint.Style.STROKE
                        canvas.drawLine(elem.x, elem.y, elem.x + elem.size, elem.y, paint)
                        paint.style = Paint.Style.FILL
                    }
                }
            }
            canvas.restore()
        }

        // === CELEBRATION ===
        private fun spawnCelebration(winnerLaneIndex: Int) {
            showCelebration = true
            celebrationParticles.clear()
            val fX = trackLength - FINISH_LINE_OFFSET
            val rH = height.toFloat() / displayLaneCount
            val winnerScreenX = fX - cameraX
            val winnerScreenY = winnerLaneIndex * rH + rH / 2f

            val colors = intArrayOf(
                Color.parseColor("#FFD700"), Color.parseColor("#FF6B35"),
                Color.parseColor("#FF1744"), Color.parseColor("#FFFFFF"),
                Color.parseColor("#FFC107"), Color.parseColor("#E040FB"),
                Color.parseColor("#00E5FF")
            )

            repeat(80) {
                val angle = Random.nextFloat() * Math.PI.toFloat() * 2f
                val speed = Random.nextFloat() * 8f + 3f
                celebrationParticles.add(CelebrationParticle(
                    x = winnerScreenX + Random.nextFloat() * 60f - 30f,
                    y = winnerScreenY + Random.nextFloat() * 60f - 30f,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed - Random.nextFloat() * 3f,
                    color = colors[Random.nextInt(colors.size)],
                    size = Random.nextFloat() * 8f + 3f,
                    alpha = 255f,
                    rotation = Random.nextFloat() * 360f,
                    rotationSpeed = Random.nextFloat() * 15f - 7.5f
                ))
            }
        }

        private fun updateCelebrationParticles() {
            val iterator = celebrationParticles.iterator()
            while (iterator.hasNext()) {
                val p = iterator.next()
                p.x += p.vx
                p.y += p.vy
                p.vy += 0.15f
                p.alpha -= 2f
                p.rotation += p.rotationSpeed
                p.size *= 0.995f
                if (p.alpha <= 0f) iterator.remove()
            }
        }

        private fun drawCelebration(canvas: Canvas) {
            celebrationParticles.forEach { p ->
                paint.color = p.color
                paint.alpha = p.alpha.toInt().coerceIn(0, 255)
                paint.style = Paint.Style.FILL
                canvas.save()
                canvas.translate(p.x, p.y)
                canvas.rotate(p.rotation)
                when ((p.color xor p.size.toInt()) % 3) {
                    0 -> canvas.drawRect(-p.size, -p.size * 0.5f, p.size, p.size * 0.5f, paint)
                    1 -> canvas.drawCircle(0f, 0f, p.size * 0.6f, paint)
                    else -> {
                        val path = Path().apply {
                            moveTo(0f, -p.size)
                            lineTo(p.size * 0.7f, p.size * 0.5f)
                            lineTo(-p.size * 0.7f, p.size * 0.5f)
                            close()
                        }
                        canvas.drawPath(path, paint)
                    }
                }
                canvas.restore()
            }
            paint.alpha = 255

            val winner = racingAnimals.find { it.rank == 1 }
            if (winner != null) {
                val idx = racingAnimals.indexOf(winner)
                val rH = height.toFloat() / displayLaneCount
                val fX = trackLength - FINISH_LINE_OFFSET
                val animalScreenX = winner.progress * fX - cameraX
                val animalScreenY = idx * rH + rH / 2f
                val animalSize = getAnimalSize()
                paint.textSize = animalSize * 0.5f
                paint.textAlign = Paint.Align.CENTER
                val crownBob = sin(frameCounter * 0.1f) * 5f
                canvas.drawText("\uD83D\uDC51", animalScreenX, animalScreenY - animalSize * 0.6f + crownBob, paint)
            }
        }
    }
}
