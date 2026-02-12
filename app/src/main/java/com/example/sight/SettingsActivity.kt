package com.example.sight

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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

    // 像素输入框
    private lateinit var edtPixel: EditText
    // 像素确认按钮（新增）
    private lateinit var btnPixelConfirm: Button

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

        // --- 像素输入行（标签 + 输入框 + 确认按钮）---
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
            setText(initialSize.toString())
            imeOptions = EditorInfo.IME_ACTION_DONE
            // 不在这里执行确认：改为按确认按钮生效（按 Done 只收起键盘）
            setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    // 仅隐藏键盘，确认由按钮负责
                    hideKeyboard()
                    v.clearFocus()
                    true
                } else false
            }
            // 不在失去焦点时自动应用，确认由按钮触发
            onFocusChangeListener = null
        }

        btnPixelConfirm = Button(this).apply {
            text = "确认"
            setOnClickListener {
                // 读取像素输入并应用
                val input = edtPixel.text.toString().toIntOrNull()
                if (input != null && input >= 5) {
                    val stepped = roundToNearest5(input)
                    val clamped = stepped.coerceAtLeast(5).coerceAtMost(screenShortSide)
                    setESize(clamped)
                    hideKeyboard()
                } else {
                    // 恢复为当前实际大小并提示（通过重写文本）
                    edtPixel.setText(eView.sizePx.toString())
                }
            }
        }

        pixelRow.addView(tvPixelLabel)
        pixelRow.addView(edtPixel)
        pixelRow.addView(btnPixelConfirm)
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
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    hideKeyboard()
                    v.clearFocus()
                    true
                } else false
            }
        }
        root.addView(edtMeasured)

        val btnConfirm = Button(this).apply { text = "确认以 7.27mm 校准" }
        root.addView(btnConfirm)

        btnSave = Button(this).apply { text = "保存并返回" }
        root.addView(btnSave)

        setContentView(root)

        // --- 按钮 + / - 逻辑（保持每次步进5px）---
        btnPlus.setOnClickListener {
            var newSize = eView.sizePx + 5
            newSize = newSize.coerceAtMost(screenShortSide)
            newSize = roundToNearest5(newSize)
            setESize(newSize)
        }

        btnMinus.setOnClickListener {
            var newSize = eView.sizePx - 5
            newSize = newSize.coerceAtLeast(5)
            newSize = roundToNearest5(newSize)
            setESize(newSize)
        }

        // --- 毫米校准按钮逻辑 ---
        btnConfirm.setOnClickListener {
            val measuredStr = edtMeasured.text.toString().trim().replace(',', '.')
            val measured = measuredStr.toDoubleOrNull()
            if (measured != null && measured > 0.0) {
                val newPx = (eView.sizePx * 7.27 / measured).toInt()
                val stepped = roundToNearest5(newPx)
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
        val final = newSize.coerceAtLeast(5).coerceAtMost(screenShortSide)
        eView.sizePx = final
        eView.layoutParams.width = final
        eView.layoutParams.height = final
        eView.requestLayout()
        if (edtPixel.text.toString() != final.toString()) {
            edtPixel.setText(final.toString())
        }
    }

    // 将 int 四舍五入到最接近的 5 的倍数
    private fun roundToNearest5(x: Int): Int {
        return (Math.round(x / 5.0) * 5).toInt()
    }

    // 隐藏键盘工具
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val v = currentFocus ?: window.decorView
        imm?.hideSoftInputFromWindow(v.windowToken, 0)
    }
}
