package com.decike.sight

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class ArrowView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF000000.toInt()
    }
    private val path = Path()

    // 0=right, 90=down, 180=left, 270=up
    var directionDeg: Int = 0
        set(value) { field = ((value%360)+360)%360; invalidate() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // size determined by layout params externally
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        // center then rotate
        canvas.save()
        canvas.rotate(directionDeg.toFloat(), w/2f, h/2f)
        path.reset()
        // draw a right-pointing triangular arrow with stem (simple)
        val shaftW = w * 0.4f
        val headW = w - shaftW
        val centerY = h/2f
        val halfH = h/2f
        // rectangle shaft
        path.moveTo(0f, centerY - halfH*0.4f)
        path.lineTo(shaftW, centerY - halfH*0.4f)
        path.lineTo(shaftW, centerY - halfH)
        path.lineTo(w, centerY)
        path.lineTo(shaftW, centerY + halfH)
        path.lineTo(shaftW, centerY + halfH*0.4f)
        path.lineTo(0f, centerY + halfH*0.4f)
        path.close()
        canvas.drawPath(path, paint)
        canvas.restore()
    }
}
