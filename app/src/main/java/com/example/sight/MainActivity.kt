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
    private val periodMs = 10_000L

    // hold references for each row
    private val rowData = mutableListOf<Row>()

    private var baseSizePx: Int = 200 // default, loaded from prefs if saved

    // countdown UI
    private var tvCountdown: TextView? = null
    private var nextToggleTimeMs: Long = 0L
    private val countdownUpdateInterval = 100L // every 0.1s

    data class Row(val container: FrameLayout, val eView: EView, val arrow: ArrowView)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("sight_prefs", MODE_PRIVATE)
        baseSizePx = prefs.getInt("e_size_px", 200)

        btnStartStop = findViewById(R.id.btnStartStop)
        btnSettings = findViewById(R.id.btnSettings)
        llContainer = findViewById(R.id.llContainer)

        // 关键：允许子 View 在父容器之外绘制，避免箭头被裁剪
        disableClippingForViewAndParents(llContainer)

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

                // arrow default size equals E size so overlay doesn't change row height
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
     * Build each row as a FrameLayout (overlay). EView is centered; ArrowView is an overlay
     * positioned at runtime to left or right of E without affecting E's position.
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
                // 允许 overlay 超出本行范围
                clipChildren = false
                clipToPadding = false
            }

            val eView = EView(this).apply {
                sizePx = baseSizePx
                // center within FrameLayout
                layoutParams = FrameLayout.LayoutParams(baseSizePx, baseSizePx, Gravity.CENTER)
            }

            val arrow = ArrowView(this).apply {
                visibility = View.GONE
                // default overlay size = E size (won't push anything)
                layoutParams = FrameLayout.LayoutParams(baseSizePx, baseSizePx).apply {
                    // initial gravity center vertical; we will reposition via arrow.x / arrow.y
                    gravity = Gravity.CENTER_VERTICAL
                }
            }

            rowFrame.addView(eView)
            rowFrame.addView(arrow)

            llContainer.addView(rowFrame)
            rowData.add(Row(rowFrame, eView, arrow))
        }
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            if (!showingAnswer) {
                // hide arrows (GONE) and reset arrow overlay size to E size
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

                // countdown for this phase
                nextToggleTimeMs = SystemClock.uptimeMillis() + periodMs
                showingAnswer = true
                handler.postDelayed(this, periodMs)
            } else {
                // show arrows overlayed; arrows layout size = 3 * E size (overlay, won't push E)
                rowData.forEachIndexed { idx, r ->
                    r.arrow.visibility = View.VISIBLE
                    r.arrow.directionDeg = r.eView.rotationDeg

                    // set layout size to 3x so the view's drawable area is large enough
                    val size = r.eView.sizePx * 3
                    val lp = r.arrow.layoutParams
                    lp.width = size
                    lp.height = size
                    r.arrow.layoutParams = lp
                    r.arrow.requestLayout()

                    // Position arrow relative to the E center:
                    // if container not measured yet, post to run after layout
                    val container = r.container
                    if (container.width == 0 || r.eView.width == 0) {
                        container.post {
                            positionArrow(r, idx, size)
                        }
                    } else {
                        positionArrow(r, idx, size)
                    }
                }

                nextToggleTimeMs = SystemClock.uptimeMillis() + periodMs
                showingAnswer = false
                handler.postDelayed(this, periodMs)
            }
        }
    }

    // position arrow overlay left or right of the centered E within the row frame
    private fun positionArrow(row: Row, idx: Int, arrowSize: Int) {
        val container = row.container
        val e = row.eView
        // center coordinates and E left within container
        val containerW = container.width
        val containerH = container.height
        val eW = e.width
        val eH = e.height
        if (containerW == 0 || eW == 0) return
        val eLeft = (containerW - eW) / 2 // left position of E within container
        // left arrows for idx 0,2,4,6
        val isLeft = (idx % 2 == 0)
        val arrow = row.arrow
        if (isLeft) {
            // put arrow to the left of E: x = eLeft - arrowSize
            val x = (eLeft - arrowSize).toFloat()
            arrow.x = x
        } else {
            // put arrow to right: x = eLeft + eW
            val x = (eLeft + eW).toFloat()
            arrow.x = x
        }
        // vertically center the arrow drawing (arrowSize is the layout/drawing size)
        val y = ((containerH - arrowSize) / 2).toFloat()
        arrow.y = y
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
        running = true
        btnStartStop.text = "停止"
        showingAnswer = false
        nextToggleTimeMs = SystemClock.uptimeMillis() + periodMs
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
     * If parent is ConstraintLayout we align bottom of countdown to bottom of the start button
     * and place it between start and settings (startToEnd / endToStart). We also color it red.
     * Otherwise we fall back to inserting a centered TextView above the main container and color it red.
     */
    private fun insertCountdownTextView() {
        try {
            val parent = btnStartStop.parent
            val tv = TextView(this).apply {
                id = View.generateViewId()
                textSize = 16f
                setTextColor(Color.RED) // red font
                text = ""
            }

            if (parent is ConstraintLayout) {
                val lp = ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.WRAP_CONTENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    startToEnd = btnStartStop.id
                    endToStart = btnSettings.id
                    // align bottom with the start button bottom
                    bottomToBottom = btnStartStop.id
                    // tiny margin downward so it visually sits a bit lower
                    topMargin = dpToPx(2)
                }
                parent.addView(tv, lp)
                tvCountdown = tv
            } else {
                // fallback: add as a centered text above llContainer (and move it down a bit)
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
                    // some fallback, add to container
                    llContainer.addView(tv, 0)
                    tvCountdown = tv
                }
            }
        } catch (ex: Exception) {
            // ignore but ensure tvCountdown nullable
            tvCountdown = null
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).roundToInt()
    }

    /**
     * Disable clipping (clipChildren/clipToPadding) for a view and all its ancestor ViewGroups.
     * This ensures large overlay children (the arrows) won't be cropped by parent views.
     */
    private fun disableClippingForViewAndParents(v: View) {
        var p: ViewParent? = v.parent
        // also set for the view itself (if it's a ViewGroup)
        if (v is ViewGroup) {
            v.clipChildren = false
            v.clipToPadding = false
        }
        while (p is ViewGroup) {
            try {
                p.clipChildren = false
                p.clipToPadding = false
            } catch (_: Exception) { /* ignore */ }
            p = p.parent
        }
    }
}
