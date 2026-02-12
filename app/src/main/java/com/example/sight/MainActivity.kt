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
                    // initial position; we'll reposition it when showing answers
                    gravity = Gravity.CENTER_VERTICAL
                }
            }

            rowFrame.addView(eView)
            r
