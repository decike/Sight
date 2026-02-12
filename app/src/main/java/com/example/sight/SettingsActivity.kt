package com.example.sight

import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var eView: EView
    private lateinit var prefs: SharedPreferences
    private lateinit var edtMeasured: EditText
    private lateinit var btnPlus: Button
    private lateinit var btnMinus: Button
    private lateinit var btnSave: Button
    private var scaleDetector: ScaleGestureDetector? = null

    // 屏幕短边（像素）
    private val screenShortSide: Int by lazy {
        val metrics = resources.displayMetrics
        minOf(metrics.widthPixels, metrics.heightPixels)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("sight_prefs", MODE_PRIVATE)
        val savedSize = prefs.getInt("e_size_px", 200)
        // 确保初始尺寸不超过屏幕短边
        val initialSize = savedSize.coerceAtMost(screenShortSide)

        // 根布局：垂直 LinearLayout，子 View 水平居中
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            gravity = Gravity.CENTER_HORIZONTAL   // 所有子 View 水平居中
        }

        // E 视图：居中，尺寸受屏幕短边限制
        eView = EView(this).apply {
            sizePx = initialSize
            // 强制宽高相等，并居中显示
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        // 按钮行
        btnPlus = Button(this).apply { text = "+" }
        btnMinus = Button(this).apply { text = "-" }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL   // 按钮组也居中
            addView(btnMinus)
            addView(btnPlus)
        }

        // 输入框
        edtMeasured = EditText(this).apply {
            hint = "输入当前 E 的毫米尺寸 (例如 72.7)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        val btnConfirm = Button(this).apply { text = "确认以 7.27mm 校准" }
        btnSave = Button(this).apply { text = "保存并返回" }

        // 添加所有视图
        root.addView(eView)
        root.addView(btnRow)
        root.addView(edtMeasured)
        root.addView(btnConfirm)
        root.addView(btnSave)

        setContentView(root)

        // --- 手势缩放，增加屏幕短边限制 ---
        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private var accumulated = 1.0f
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scale = detector.scaleFactor
                accumulated *= scale
                val newSize = (eView.sizePx * accumulated).toInt().coerceAtLeast(5)
                // 限制最大尺寸 = 屏幕短边
                val clamped = newSize.coerceAtMost(screenShortSide)
                // 强制为5的倍数
                val stepped = (Math.round(clamped / 5.0) * 5).toInt().coerceAtLeast(5)
                eView.sizePx = stepped
                // 更新布局参数，使 E 视图始终居中且大小正确
                eView.layoutParams.width = stepped
                eView.layoutParams.height = stepped
                eView.requestLayout()
                return true
            }
        })

        // --- 按钮 + / -，同样应用尺寸限制 ---
        btnPlus.setOnClickListener {
            var newSize = ((eView.sizePx + 5 + 4) / 5) * 5
            newSize = newSize.coerceAtMost(screenShortSide)   // 不超过屏幕短边
            eView.sizePx = newSize
            eView.layoutParams.width = newSize
            eView.layoutParams.height = newSize
            eView.requestLayout()
        }

        btnMinus.setOnClickListener {
            var newSize = ((eView.sizePx - 5) / 5) * 5
            newSize = newSize.coerceAtLeast(5)
            // 减小尺寸无需上限限制，但保留下限
            eView.sizePx = newSize
            eView.layoutParams.width = newSize
            eView.layoutParams.height = newSize
            eView.requestLayout()
        }

        // 校准按钮：计算结果后也要应用上限限制
        btnConfirm.setOnClickListener {
            val measuredStr = edtMeasured.text.toString()
            val measured = measuredStr.toDoubleOrNull()
            if (measured != null && measured > 0.0) {
                val newPx = (eView.sizePx * 7.27 / measured).toInt()
                val stepped = (Math.round(newPx / 5.0) * 5).toInt().coerceAtLeast(5)
                val clamped = stepped.coerceAtMost(screenShortSide)   // 上限
                eView.sizePx = clamped
                eView.layoutParams.width = clamped
                eView.layoutParams.height = clamped
                eView.requestLayout()
            } else {
                edtMeasured.error = "请输入有效数值"
            }
        }

        // 保存按钮
        btnSave.setOnClickListener {
            prefs.edit().putInt("e_size_px", eView.sizePx).apply()
            finish()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector?.onTouchEvent(event)
        return true   // 消费事件，避免手势不生效
    }
}