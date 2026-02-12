package com.example.sight

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
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

    private lateinit var scaleDetector: ScaleGestureDetector

    // 屏幕短边（像素）
    private val screenShortSide: Int by lazy {
        val metrics = resources.displayMetrics
        minOf(metrics.widthPixels, metrics.heightPixels)
    }

    // 缩放起始基准（用于避免累积误差）
    private var scaleStartSize = 0

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
            // 关键：使根布局可获取焦点且可点击，以便点击空白处可清除 EditText 焦点
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
        }

        // 点击空白处清除焦点并隐藏键盘
        root.setOnClickListener {
            currentFocus?.clearFocus()
            hideKeyboard()
        }

        // --- E 视图 ---
        eView = EView(this).apply {
            sizePx = initialSize
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        // 把手势绑定到 eView，避免 Activity.onTouchEvent 在 EditText 焦点时收不到事件
        eView.setOnTouchListener { _, ev ->
            scaleDetector.onTouchEvent(ev)
            // 返回 false 以便允许后续事件（如点击）继续传播；如果你要消费事件可返回 true
            false
        }
        root.addView(eView)

        // --- 像素输入行（标签 + 输入框）---
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

            // IME action Done - 按键盘完成时也会提交
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    v.clearFocus()  // 触发下面的 onFocusChange
                    hideKeyboard()
                    true
                } else false
            }

            // 失去焦点时，强制为5的倍数并生效
            onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val input = text.toString().toIntOrNull()
                    if (input != null && input >= 5) {
                        val stepped = roundToNearest5(input)
                        setESize(stepped)
                    } else {
                        // 恢复为当前实际大小
                        setText(eView.sizePx.toString())
                    }
                }
            }

            // 同时，为了更好的交互，添加 TextWatcher（可选：实时预览）
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    // 不强制每一字符都更新 E（会很跳），这里不作自动更新，依赖失焦/Done 提交
                }
            })
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
            // 方便用户按完成键提交
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    v.clearFocus()
                    hideKeyboard()
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

        // --- 手势缩放（改为基于 scaleStartSize 的实现，避免漂移） ---
        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                scaleStartSize = eView.sizePx
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newSizeFloat = scaleStartSize * detector.scaleFactor
                val newSize = newSizeFloat.toInt().coerceAtLeast(5)
                val clamped = newSize.coerceAtMost(screenShortSide)
                val stepped = roundToNearest5(clamped)
                setESize(stepped)
                return true
            }
        })

        // --- 按钮 + / - ---
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

        // --- 毫米校准 ---
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
        // 更新布局参数（保持居中）
        eView.layoutParams.width = final
        eView.layoutParams.height = final
        eView.requestLayout()
        // 同步像素输入框（但避免触发焦点变化）
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

    // Activity 的 onTouchEvent 保持默认行为
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 如果你还要在 Activity 级别处理手势，这里可以调用 scaleDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }
}
