package com.decike.sight

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class EView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF000000.toInt()
    }

    var sizePx: Int = 200   // E 的外边长（像素）
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    // rotation in degrees: 0,90,180,270
    var rotationDeg: Int = 0
        set(value) {
            field = ((value % 360) + 360) % 360
            invalidate()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // view size will be sizePx (but allow wrap_content)
        val w = sizePx
        setMeasuredDimension(w, w)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val S = sizePx.toFloat()
        if (S <= 0f) return

        // center and rotate
        val cx = width / 2f
        val cy = height / 2f
        canvas.save()
        canvas.rotate(rotationDeg.toFloat(), cx, cy)

        val cell = S / 5f
        // grid origin top-left so draw relative to center (we measured view to be SxS)
        val left = 0f
        val top = 0f

        // pattern: 5x5, fill cells by E pattern:
        // row0: all on
        // row1: col0 on
        // row2: all on
        // row3: col0 on
        // row4: all on
        val fill = Array(5) { BooleanArray(5) }
        for (c in 0..4) fill[0][c] = true
        fill[1][0] = true
        for (c in 0..4) fill[2][c] = true
        fill[3][0] = true
        for (c in 0..4) fill[4][c] = true

        for (r in 0..4) {
            for (c in 0..4) {
                if (fill[r][c]) {
                    val l = left + c * cell
                    val t = top + r * cell
                    canvas.drawRect(l, t, l + cell, t + cell, paint)
                }
            }
        }

        canvas.restore()
    }
}
