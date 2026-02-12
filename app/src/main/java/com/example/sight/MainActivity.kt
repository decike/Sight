package com.example.sight

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import kotlin.random.Random

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

    data class Row(val container: LinearLayout, val eView: EView, val arrow: ArrowView)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("sight_prefs", MODE_PRIVATE)
        baseSizePx = prefs.getInt("e_size_px", 200)

        btnStartStop = findViewById(R.id.btnStartStop)
        btnSettings = findViewById(R.id.btnSettings)
        llContainer = findViewById(R.id.llContainer)

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
            rowData.forEach { r ->
                r.eView.sizePx = baseSizePx
                // arrow size will be adjusted when showing answers
            }
        }
    }

    private fun buildRows() {
        llContainer.removeAllViews()
        rowData.clear()
        for (i in 0 until 8) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = baseSizePx
                }
                gravity = Gravity.CENTER
            }

            val eView = EView(this).apply {
                sizePx = baseSizePx
                rotationDeg = 0
            }

            val arrow = ArrowView(this).apply {
                visibility = View.GONE
            }

            // build row: if index 0,2,4,6 (1st,3rd,5th,7th) arrow on left
            val arrowOnLeft = (i % 2 == 0)
            if (arrowOnLeft) {
                // arrow then spacer then eView
                val lpA = LinearLayout.LayoutParams(
                    (baseSizePx * 2),
                    (baseSizePx * 2)
                )
                arrow.layoutParams = lpA
                row.addView(arrow)
                row.addView(eView)
            } else {
                // eView then arrow
                val lpA = LinearLayout.LayoutParams(
                    (baseSizePx * 2),
                    (baseSizePx * 2)
                )
                arrow.layoutParams = lpA
                row.addView(eView)
                row.addView(arrow)
            }

            llContainer.addView(row)
            rowData.add(Row(row, eView, arrow))
        }
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            if (!showingAnswer) {
                // show only E: hide arrows
                rowData.forEach { it.arrow.visibility = View.GONE }
                // randomize directions
                rowData.forEach { r ->
                    val deg = listOf(0, 90, 180, 270).random()
                    r.eView.rotationDeg = deg
                }
                showingAnswer = true
                handler.postDelayed(this, periodMs)
            } else {
                // show answers for 10s
                rowData.forEachIndexed { idx, r ->
                    // arrow visible and oriented as E
                    r.arrow.visibility = View.VISIBLE
                    r.arrow.directionDeg = r.eView.rotationDeg
                    // set arrow size = 2 * E size (update layout params)
                    val size = r.eView.sizePx * 2
                    r.arrow.layoutParams.width = size
                    r.arrow.layoutParams.height = size
                    r.arrow.requestLayout()
                }
                showingAnswer = false
                handler.postDelayed(this, periodMs)
            }
        }
    }

    private fun startSequence() {
        running = true
        btnStartStop.text = "停止"
        showingAnswer = false
        handler.post(tickRunnable)
    }

    private fun stopSequence() {
        running = false
        btnStartStop.text = "开始"
        handler.removeCallbacks(tickRunnable)
        // hide arrows
        rowData.forEach { it.arrow.visibility = View.GONE }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
