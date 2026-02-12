package com.example.sight

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.math.roundToInt
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var btnStartStop: Button
    private lateinit var btnSettings: Button
    private lateinit var llContainer: LinearLayout
    private lateinit var prefs: SharedPreferences

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var showingAnswer = false

    // 两段时长（ms），会在 startSequence 里从 prefs 读取最新值
    private var questionDurationMs: Long = 10_000L
    private var answerDurationMs: Long = 10_000L

    // hold references for each row
    private val rowData = mutableListOf<Row>()

    private var baseSizePx: Int = 200 // default, loaded from prefs if saved

    // countdown UI
    private var tvCountdown: TextView? = null
    private var nextToggleTimeMs: Long = 0L
    private val countdownUpdateInterval = 100L // every 0.1s

    // overlay container to hold arrows so they won't be clipped by row parents
    private lateinit var overlayContainer: FrameLayout

    data class Row(val container: FrameLayout, val eView: EView, val arrow: ArrowView)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("sight_prefs", MODE_PRIVATE)
        baseSizePx = prefs.getInt("e_size_px", 200)

        btnStartStop = findViewById(R.id.btnStartStop)
        btnSettings = findViewById(R.id.btnSettings)
        llContainer = findViewById(R.id.llContainer)

        // try to disable clipping on content view ancestors (best-effort)
        disableClippingForViewAndParents(llContainer)

        // create an overlay container and add it above activity content so arrows can be drawn there
        setupOverlayContainer()

        insertCountdownTextView() // create and insert countdown view

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnStartStop.setOnClickListener {
            if (!running) startSequence() else stopSequence()
        }

        buildRows()
    }

    override fun onResume() {
        super.onResume()
        // reload saved size
        val newSize = prefs.getInt("e_size_px", baseSizePx)
        if (newSize != baseSizePx) {
            baseSizePx = newSize
            // update rows
            rowData.forEachIndexed { idx, r ->
                // update eView
                r.eView.sizePx = baseSizePx
                r.eView.layoutParams.width = baseSizePx
                r.eView.layoutParams.height = baseSizePx
                r.eView.requestLayout()

                // arrow default layout size equals E size so overlay won't change row height
                val lpArrow = r.arrow.layoutParams
                lpArrow.width = baseSizePx
                lpArrow.height = baseSizePx
                r.arrow.layoutParams = lpArrow
                r.arrow.requestLayout()

                // update container top margin: first row = 5px, others = baseSizePx
                val lpRow = r.container.layoutParams as LinearLayout.LayoutParams
                lpRow.topMargin = if (idx == 0) 5 else baseSizePx
                r.container.layoutParams = lpRow
            }
        }
    }

    /**
     * Build each row as a FrameLayout (overlay pattern for E); ArrowView instances are added to
     * overlayContainer instead of being children of the row, so arrows never push/move E and
     * are not clipped by row parents.
     */
    private fun buildRows() {
        llContainer.removeAllViews()
        rowData.clear()

        for (i in 0 until 8) {
            val rowFrame = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    baseSizePx
                ).apply {
                    topMargin = if (i == 0) 5 else baseSizePx // first row distance = 5px
                }
                // allow overlay to extend visually (defensive)
                clipChildren = false
                clipToPadding = false
            }

            val eView = EView(this).apply {
                sizePx = baseSizePx
                layoutParams = FrameLayout.LayoutParams(baseSizePx, baseSizePx, Gravity.CENTER)
            }

            // create arrow, but DO NOT add to rowFrame — add to overlayContainer
            val arrow = ArrowView(this).apply {
                visibility = View.GONE
                // layout size initially equal to E; will be resized when showing answers
                layoutParams = FrameLayout.LayoutParams(baseSizePx, baseSizePx)
                elevation = 100f
            }

            rowFrame.addView(eView)
            llContainer.addView(rowFrame)
            overlayContainer.addView(arrow)

            rowData.add(Row(rowFrame, eView, arrow))
        }
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            if (!showingAnswer) {
                // hide arrows (GONE) and reset arrow layout to E size
                rowData.forEach { row ->
                    row.arrow.visibility = View.GONE
                    row.arrow.scaleX = 1f
                    row.arrow.scaleY = 1f
                    val lp = row.arrow.layoutParams
                    lp.width = row.eView.sizePx
                    lp.height = row.eView.sizePx
                    row.arrow.layoutParams = lp
                    row.arrow.requestLayout()
                }

                // randomize directions
                rowData.forEach { r ->
                    val deg = listOf(0, 90, 180, 270).random()
                    r.eView.rotationDeg = deg
                }

                // countdown for this phase = questionDurationMs
                nextToggleTimeMs = SystemClock.uptimeMillis() + questionDurationMs
                showingAnswer = true
                handler.postDelayed(this, questionDurationMs)
            } else {
                // show answers overlayed; arrow drawing area = 3 * E size
                rowData.forEachIndexed { idx, r ->
                    r.arrow.visibility = View.VISIBLE
                    r.arrow.directionDeg = r.eView.rotationDeg

                    // set layout size to 3x so the arrow has enough drawing area (still in overlay)
                    val size = r.eView.sizePx * 3
                    val lp = r.arrow.layoutParams
                    lp.width = size
                    lp.height = size
                    r.arrow.layoutParams = lp
                    r.arrow.requestLayout()

                    // position arrow in overlay; when view not measured, post
                    if (r.eView.width == 0 || overlayContainer.width == 0) {
                        overlayContainer.post {
                            positionArrowInOverlay(r, idx, size)
                        }
                    } else {
                        positionArrowInOverlay(r, idx, size)
                    }

                    r.arrow.bringToFront()
                    r.arrow.invalidate()
                }

                // countdown for answer phase
                nextToggleTimeMs = SystemClock.uptimeMillis() + answerDurationMs
                showingAnswer = false
                handler.postDelayed(this, answerDurationMs)
            }
        }
    }

    /**
     * Position arrow inside overlayContainer based on eView screen coordinates.
     * idx decides left/right placement. arrowSize is the layout/draw size (px).
     */
    private fun positionArrowInOverlay(row: Row, idx: Int, arrowSize: Int) {
        val eView = row.eView
        val arrow = row.arrow

        val eLoc = IntArray(2)
        val ovLoc = IntArray(2)
        eView.getLocationOnScreen(eLoc)
        overlayContainer.getLocationOnScreen(ovLoc)

        val eLeftInOverlay = (eLoc[0] - ovLoc[0]).toFloat()
        val eTopInOverlay = (eLoc[1] - ovLoc[1]).toFloat()

        val eW = eView.width
        val eH = eView.height

        val isLeft = (idx % 2 == 0)

        val x: Float = if (isLeft) {
            eLeftInOverlay - arrowSize.toFloat()
        } else {
            eLeftInOverlay + eW.toFloat()
        }

        val y = eTopInOverlay + (eH - arrowSize) / 2.0f

        arrow.x = x
        arrow.y = y
        arrow.visibility = View.VISIBLE
        arrow.bringToFront()
        arrow.invalidate()
    }

    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            val remainMs = (nextToggleTimeMs - now).coerceAtLeast(0L)
            val remainSeconds = remainMs.toDouble() / 1000.0
            val text = String.format(Locale.getDefault(), "%.1fs", remainSeconds)
            tvCountdown?.text = text
            handler.postDelayed(this, countdownUpdateInterval)
        }
    }

    private fun startSequence() {
        if (running) return
        // read durations from prefs (seconds stored as float), convert to ms
        val qSec = prefs.getFloat("duration_question_s", 10f)
        val aSec = prefs.getFloat("duration_answer_s", 10f)
        questionDurationMs = (qSec * 1000L).toLong()
        answerDurationMs = (aSec * 1000L).toLong()

        running = true
        btnStartStop.text = "停止"
        showingAnswer = false
        nextToggleTimeMs = SystemClock.uptimeMillis() + questionDurationMs
        handler.post(tickRunnable)
        handler.post(countdownRunnable)
    }

    private fun stopSequence() {
        running = false
        btnStartStop.text = "开始"
        handler.removeCallbacks(tickRunnable)
        handler.removeCallbacks(countdownRunnable)
        rowData.forEach { it.arrow.visibility = View.GONE }
        tvCountdown?.text = ""
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * Insert countdown TextView between start and settings buttons.
     */
    private fun insertCountdownTextView() {
        try {
            val parent = btnStartStop.parent
            val tv = TextView(this).apply {
                id = View.generateViewId()
                textSize = 16f
                setTextColor(Color.RED)
                text = ""
            }

            if (parent is ConstraintLayout) {
                val lp = ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.WRAP_CONTENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    startToEnd = btnStartStop.id
                    endToStart = btnSettings.id
                    bottomToBottom = btnStartStop.id
                }
                parent.addView(tv, lp)
                tvCountdown = tv
            } else {
                val tvLp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dpToPx(4)
                }
                val root = llContainer.parent
                if (root is LinearLayout) {
                    val index = root.indexOfChild(llContainer)
                    root.addView(tv, index)
                    tv.layoutParams = tvLp
                    tv.gravity = Gravity.CENTER
                    tvCountdown = tv
                } else {
                    llContainer.addView(tv, 0)
                    tvCountdown = tv
                }
            }
        } catch (ex: Exception) {
            tvCountdown = null
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).roundToInt()
    }

    /**
     * Create overlayContainer as a top-level FrameLayout (match_parent) and add it
     * to the activity's content view so overlay children (arrows) draw above everything.
     */
    private fun setupOverlayContainer() {
        val content = findViewById<ViewGroup>(android.R.id.content)
        overlayContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            clipChildren = false
            clipToPadding = false
            isClickable = false
        }
        content.addView(overlayContainer)
    }

    /**
     * Best-effort: disable clipping for v (if ViewGroup) and for its ancestor viewgroups.
     */
    private fun disableClippingForViewAndParents(v: View) {
        if (v is ViewGroup) {
            try {
                v.clipChildren = false
                v.clipToPadding = false
            } catch (_: Exception) { }
        }
        var parent = v.parent
        while (parent is ViewGroup) {
            try {
                parent.clipChildren = false
                parent.clipToPadding = false
            } catch (_: Exception) { }
            parent = (parent as? View)?.parent
        }
    }
}
