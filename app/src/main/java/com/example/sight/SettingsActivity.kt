package com.example.sight

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt

class SettingsActivity : AppCompatActivity() {

    private lateinit var eView: EView
    private lateinit var prefs: SharedPreferences
    private lateinit var edtMeasured: EditText
    private lateinit var btnPlus: Button
    private lateinit var btnMinus: Button
    private lateinit var btnSave: Button

    // 像素输入框 与 确认按钮
    private lateinit var edtPixel: EditText
    private lateinit var btnPixelConfirm: Button

    // 时长输入（题目/答案，单位：秒）
    private lateinit var edtQuestionSec: EditText
    private lateinit var edtAnswerSec: EditText

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

        // 时长默认值（秒）
        val defaultQuestionSec = prefs.getFloat("duration_question_s", 10f)
        val defaultAnswerSec = prefs.getFloat("duration_answer_s", 10f)

        // 根布局：垂直 LinearLayout，所有子 View 水平居中
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dpToPx(12)
            setPadding(pad, pad, pad, pad)
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
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(12)
                bottomMargin = dpToPx(12)
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
            // 按 Done 收起键盘，并清除焦点
            setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    hideKeyboard()
                    v.clearFocus()
                    true
                } else false
            }
            // 给输入框设置一个合理的宽度（wrap_content 有时太窄）
            layoutParams = LinearLayout.LayoutParams(dpToPx(80), LinearLayout.LayoutParams.WRAP_CONTENT)
            gravity = Gravity.CENTER
        }

        // Pixel 确认按钮（显式设置宽高，缩小尺寸）
        btnPixelConfirm = Button(this).apply {
            text = "确认"
            // 文字大小与内边距都缩小
            textSize = 12f
            val density = resources.displayMetrics.density
            setPadding((6 * density).toInt(), (3 * density).toInt(), (6 * density).toInt(), (3 * density).toInt())
            // 清除默认 min，允许更小
            minWidth = 0
            minHeight = 0

            // 显式 LayoutParams：把按钮显式缩小（例如 44x28 dp）
            val w = dpToPx(44)
            val h = dpToPx(28)
            layoutParams = LinearLayout.LayoutParams(w, h).apply {
                leftMargin = dpToPx(8)
            }

            setOnClickListener {
                val input = edtPixel.text.toString().toIntOrNull()
                if (input != null && input >= 5) {
                    val stepped = roundToNearest5(input)
                    val clamped = stepped.coerceAtLeast(5).coerceAtMost(screenShortSide)
                    setESize(clamped)
                    hideKeyboard()
                } else {
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
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.leftMargin = dpToPx(4)
            lp.rightMargin = dpToPx(4)
            addView(btnMinus, lp)
            addView(btnPlus, lp)
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
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(8)
            }
        }
        root.addView(edtMeasured)

        val btnAuto = Button(this).apply { text = "自动校准" }
        root.addView(btnAuto)

        // --- 时长设置行（题目 + 答案）---
        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(12)
                bottomMargin = dpToPx(12)
            }
        }

        val tvQ = TextView(this).apply {
            text = "题目(s)："
            textSize = 14f
        }
        edtQuestionSec = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format("%.1f", defaultQuestionSec))
            layoutParams = LinearLayout.LayoutParams(dpToPx(60), LinearLayout.LayoutParams.WRAP_CONTENT)
            gravity = Gravity.CENTER
        }

        val tvA = TextView(this).apply {
            text = " 答案(s)："
            textSize = 14f
        }
        edtAnswerSec = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format("%.1f", defaultAnswerSec))
            layoutParams = LinearLayout.LayoutParams(dpToPx(60), LinearLayout.LayoutParams.WRAP_CONTENT)
            gravity = Gravity.CENTER
        }

        timeRow.addView(tvQ)
        timeRow.addView(edtQuestionSec)
        timeRow.addView(tvA)
        timeRow.addView(edtAnswerSec)
        root.addView(timeRow)

        // --- 保存按钮 ---
        btnSave = Button(this).apply { text = "保存并返回" }
        root.addView(btnSave)

        setContentView(root)

        // --- + / - 逻辑（步进5px）---
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
        btnAuto.setOnClickListener {
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

        // --- 保存按钮逻辑：把像素和时长写入 prefs 并返回 ---
        btnSave.setOnClickListener {
            val editor = prefs.edit()
            editor.putInt("e_size_px", eView.sizePx)

            // 保存时长（秒，float）
            val q = edtQuestionSec.text.toString().trim().replace(',', '.').toFloatOrNull() ?: 10f
            val a = edtAnswerSec.text.toString().trim().replace(',', '.').toFloatOrNull() ?: 10f
            // 限定范围（至少0.5s）
            val qClamped = q.coerceAtLeast(0.5f)
            val aClamped = a.coerceAtLeast(0.5f)
            editor.putFloat("duration_question_s", qClamped)
            editor.putFloat("duration_answer_s", aClamped)

            editor.apply()
            hideKeyboard()
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
        return (kotlin.math.round(x / 5.0).toInt() * 5)
    }

    // 隐藏键盘工具
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val v = currentFocus ?: window.decorView
        imm?.hideSoftInputFromWindow(v.windowToken, 0)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).roundToInt()
    }
}
