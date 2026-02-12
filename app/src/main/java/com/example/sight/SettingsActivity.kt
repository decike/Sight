package com.example.sight

import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var eView: EView
    private lateinit var prefs: SharedPreferences
    private lateinit var edtMeasured: EditText
    private lateinit var btnPlus: Button
    private lateinit var btnMinus: Button
    private lateinit var btnSave: Button

    // 新增：像素输入框
    private lateinit var edtPixel: EditText

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
        val initialSize = savedSize.coerceAtMost(screenShortSide)

        // 根布局：垂直 LinearLayout，所有子 View 水平居中
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // --- E 视图 ---
        eView = EView(this).apply {
            sizePx = initialSize
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        root.addView(eView)

        // --- 新增：像素输入行（标签 + 输入框）---
        val pixelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16
                bottomMargin = 16
            }
        }

        val tvPixelLabel = TextView(this).apply {
            text = "像素："
            textSize = 16f
        }

        edtPixel = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER   // 只能输入整数
            text = eView.sizePx.toString()
            // 失去焦点时，强制为5的倍数并生效
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val input = text.toString().toIntOrNull()
                    if (input != null && input >= 5) {
                        // 强制为5的倍数
                        val stepped = ((input + 2) / 5) * 5
                        eView.sizePx = stepped
                        // 更新输入框显示
                        setText(stepped.toString())
                        // 更新 E 视图布局参数（保持居中）
                        eView.layoutParams.width = stepped
                        eView.layoutParams.height = stepped
                        eView.requestLayout()
                    } else {
                        // 无效输入，恢复为当前实际大小
                        setText(eView.sizePx.toString())
                    }
                }
            }
        }

        pixelRow.addView(tvPixelLabel)
        pixelRow.addView(edtPixel)
        root.addView(pixelRow)

        // --- 按钮行（+ / -）---
        btnPlus = Button(this).apply { text = "+" }
        btnMinus = Button(this).apply { text = "-" }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(btnMinus)
            addView(btnPlus)
        }
        root.addView(btnRow)

        // --- 毫米校准输入 ---
        edtMeasured = EditText(this).apply {
            hint = "输入当前 E 的毫米尺寸 (用尺子量)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        root.addView(edtMeasured)

        val btnConfirm = Button(this).apply { text = "确认以 7.27mm 校准" }
        root.addView(btnConfirm)

        btnSave = Button(this).apply { text = "保存并返回" }
        root.addView(btnSave)

        setContentView(root)

        // --- 手势缩放 ---
        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private var accumulated = 1.0f
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scale = detector.scaleFactor
                accumulated *= scale
                val newSize = (eView.sizePx * accumulated).toInt().coerceAtLeast(5)
                val clamped = newSize.coerceAtMost(screenShortSide)
                val stepped = ((clamped + 2) / 5) * 5   // 强制5的倍数
                setESize(stepped)  // 统一更新方法
                return true
            }
        })

        // --- 按钮 + / - ---
        btnPlus.setOnClickListener {
            var newSize = ((eView.sizePx + 5 + 4) / 5) * 5   // 强制5的倍数
            newSize = newSize.coerceAtMost(screenShortSide)
            setESize(newSize)
        }

        btnMinus.setOnClickListener {
            var newSize = ((eView.sizePx - 5) / 5) * 5
            newSize = newSize.coerceAtLeast(5)
            setESize(newSize)
        }

        // --- 毫米校准 ---
        btnConfirm.setOnClickListener {
            val measuredStr = edtMeasured.text.toString()
            val measured = measuredStr.toDoubleOrNull()
            if (measured != null && measured > 0.0) {
                val newPx = (eView.sizePx * 7.27 / measured).toInt()
                val stepped = ((newPx + 2) / 5) * 5
                val clamped = stepped.coerceAtLeast(5).coerceAtMost(screenShortSide)
                setESize(clamped)
            } else {
                edtMeasured.error = "请输入有效数值"
            }
        }

        // --- 保存按钮 ---
        btnSave.setOnClickListener {
            prefs.edit().putInt("e_size_px", eView.sizePx).apply()
            finish()
        }
    }

    /**
     * 统一更新 E 视图大小、布局参数和像素输入框
     */
    private fun setESize(newSize: Int) {
        eView.sizePx = newSize
        // 更新布局参数（保持居中）
        eView.layoutParams.width = newSize
        eView.layoutParams.height = newSize
        eView.requestLayout()
        // 同步像素输入框
        edtPixel.setText(newSize.toString())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector?.onTouchEvent(event)
        return true
    }
}