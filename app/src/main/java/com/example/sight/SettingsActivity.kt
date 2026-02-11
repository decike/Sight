package com.example.sight

import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
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
    private var scaleDetector: ScaleGestureDetector? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("sight_prefs", MODE_PRIVATE)
        val currentSize = prefs.getInt("e_size_px", 200)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24)
        }

        eView = EView(this).apply {
            sizePx = currentSize
        }

        // plus/minus
        btnPlus = Button(this).apply { text = "+" }
        btnMinus = Button(this).apply { text = "-" }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(btnMinus)
            addView(btnPlus)
        }

        // measured input
        edtMeasured = EditText(this).apply {
            hint = "输入当前 E 的毫米尺寸 (例如 72.7)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        val btnConfirm = Button(this).apply { text = "确认以 7.27mm 校准" }
        btnSave = Button(this).apply { text = "保存并返回" }

        root.addView(eView)
        root.addView(btnRow)
        root.addView(edtMeasured)
        root.addView(btnConfirm)
        root.addView(btnSave)

        setContentView(root)

        // Scale gestures
        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private var accumulated = 1.0f
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scale = detector.scaleFactor
                accumulated *= scale
                val newSize = (eView.sizePx * accumulated).toInt().coerceAtLeast(5)
                // force pixel step to multiple of 5
                val stepped = (Math.round(newSize / 5.0) * 5).toInt()
                eView.sizePx = stepped
                return true
            }
        })

        // plus/minus step by 5 px
        btnPlus.setOnClickListener {
            eView.sizePx = ((eView.sizePx + 5 + 4) / 5) * 5 // ensure multiple of 5
        }
        btnMinus.setOnClickListener {
            eView.sizePx = ((eView.sizePx - 5) / 5) * 5
            if (eView.sizePx < 5) eView.sizePx = 5
        }

        btnConfirm.setOnClickListener {
            val measuredStr = edtMeasured.text.toString()
            val measured = measuredStr.toDoubleOrNull()
            if (measured != null && measured > 0.0) {
                // current displayed pixel => measured mm
                // newPixel = currentPixel * 7.27 / measured_mm
                val newPx = (eView.sizePx * 7.27 / measured).toInt()
                val stepped = (Math.round(newPx / 5.0) * 5).toInt().coerceAtLeast(5)
                eView.sizePx = stepped
            } else {
                edtMeasured.error = "请输入有效数值"
            }
        }

        btnSave.setOnClickListener {
            prefs.edit().putInt("e_size_px", eView.sizePx).apply()
            finish()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector?.onTouchEvent(event)
        return super.onTouchEvent(event)
    }
}
