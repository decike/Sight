package com.example.sight

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.math.roundToInt
import kotlin.random.Random
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

    // countdown TextView placed between Start and Settings
    private var tvCountdown: TextView? = null
    private var nextToggleTimeMs: Long = 0L
    private val countdownUpdateInterval = 100L // update every 100ms

    data class Row(val container: LinearLayout, val eView: EView, val arrow: ArrowView)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("sight_prefs", MODE_PRIVATE)
        baseSizePx = prefs.getInt("e_size_px", 200)

        btnStartStop = findViewById(R.id.btnStartStop)
        btnSettings = findViewById(R.id.btnSettings)
        llContainer = findViewById(R.id.llContainer)

        // 插入/创建倒计时文本 (放在 btnStartStop 与 btnSettings 中间)
        insertCountdownTextView()

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
            // update each row's E size, arrow default size and row spacing
            rowData.forEach { r ->
                // update eView size and layout
                r.eView.sizePx = baseSizePx
                r.eView.layoutParams.width = baseSizePx
                r.eView.layoutParams.height = baseSizePx
                r.eView.requestLayout()

                // set arrow default size to equal E size (so row height == E size)
                val lpArrow = r.arrow.layoutParams
                lpArrow.width = baseSizePx
                lpArrow.height = baseSizePx
                r.arrow.layoutParams = lpArrow
                r.arrow.requestLayout()

                // update container (row) topMargin = baseSizePx so spacing equals E edge length
                val lpRow = r.container.layoutParams as LinearLayout.LayoutParams
                lpRow.topMargin = baseSizePx
                r.container.layoutParams = lpRow
            }
        }
    }

    /**
     * Build 8 rows. Each row's topMargin = baseSizePx (so spacing between E rows equals E edge length).
     * Arrow default size = baseSizePx so it doesn't change row height until answers are shown.
     */
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
                    // spacing between rows equals E edge length
                    topMargin = baseSizePx
                }
                gravity = Gravity.CENTER
            }

            val eView = EView(this).apply {
                sizePx = baseSizePx
                rotationDeg = 0
                layoutParams = LinearLayout.LayoutParams(baseSizePx, baseSizePx)
            }

            // arrow default size equals E size (so it doesn't increase row height)
            val arrow = ArrowView(this).apply {
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(baseSizePx, baseSizePx)
            }

            val arrowOnLeft = (i % 2 == 0)
            if (arrowOnLeft) {
                row.addView(arrow)
                row.addView(eView)
            } else {
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
                // show only E: hide arrows (and reset arrow size to avoid row height changes when hiding)
                rowData.forEach { 
                    it.arrow.visibility = View.GONE
                    val lp = it.arrow.layoutParams
                    lp.width = it.eView.sizePx
                    lp.height = it.eView.sizePx
                    it.arrow.layoutParams = lp
                    it.arrow.requestLayout()
                }
                // randomize directions
                rowData.forEach { r ->
                    val deg = listOf(0, 90, 180, 270).random()
                    r.eView.rotationDeg = deg
                }
                // schedule next toggle and next countdown
                nextToggleTimeMs = SystemClock.uptimeMillis() + periodMs
                showingAnswer = true
                handler.postDelayed(this, periodMs)
            } else {
                // show answers for 10s -> arrow visible and oriented as E, arrow size = 3 * E size
                rowData.forEachIndexed { idx, r ->
                    r.arrow.visibility = View.VISIBLE
                    r.arrow.directionDeg = r.eView.rotationDeg
                    val size = r.eView.sizePx * 3  // arrow 3x E as requested earlier
                    val lp = r.arrow.layoutParams
                    lp.width = size
                    lp.height = size
                    r.arrow.layoutParams = lp
                    r.arrow.requestLayout()
                }
                // schedule next toggle and countdown
                nextToggleTimeMs = SystemClock.uptimeMillis() + periodMs
                showingAnswer = false
                handler.postDelayed(this, periodMs)
            }
        }
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
        // set next toggle time and start both runnables
        nextToggleTimeMs = SystemClock.uptimeMillis() + periodMs
        handler.post(tickRunnable)
        handler.post(countdownRunnable)
    }

    private fun stopSequence() {
        running = false
        btnStartStop.text = "开始"
        handler.removeCallbacks(tickRunnable)
        handler.removeCallbacks(countdownRunnable)
        // hide arrows
        rowData.forEach { it.arrow.visibility = View.GONE }
        // clear countdown display
        tvCountdown?.text = ""
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * 在 btnStartStop 与 btnSettings 之间插入一个居中的 TextView 用作倒计时显示。
     * 这里通过父容器（ConstraintLayout）动态插入并约束。如果布局不是 ConstraintLayout
     * 会尽量将其添加为兄弟并居中（退化方案）。
     */
    private fun insertCountdownTextView() {
        try {
            val parent = btnStartStop.parent
            if (parent is ConstraintLayout) {
                // 创建 TextView
                val tv = TextView(this).apply {
                    id = View.generateViewId()
                    textSize = 16f
                    text = ""
                }
                // 创建约束参数：top 对齐父顶端，start 连接 startBtn end，end 连接 settingsBtn start
                val lp = ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.WRAP_CONTENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    startToEnd = btnStartStop.id
                    endToStart = btnSettings.id
                    topMargin = dpToPx(8)
                }
                (parent).addView(tv, lp)
                tvCountdown = tv
            } else {
                // 退化：将一个 TextView 添加到 llContainer 的上方（相对简单的替代）
                val tv = TextView(this).apply {
                    textSize = 16f
                    text = ""
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                // 插入在 llContainer 之前
                val root = llContainer.parent
                if (root is LinearLayout) {
                    val index = root.indexOfChild(llContainer)
                    root.addView(tv, index)
                    tvCountdown = tv
                }
            }
        } catch (ex: Exception) {
            // 忽略任何异常（以防布局不符合预期），不影响主流程
            tvCountdown = null
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).roundToInt()
    }
}
