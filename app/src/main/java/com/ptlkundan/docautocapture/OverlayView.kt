package com.ptlkundan.docautocapture

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results = listOf<BoundingBox>()
    private var previewWidth = 1
    private var previewHeight = 1

    private val boxPaint = Paint().apply {
        color = ContextCompat.getColor(context!!, R.color.bounding_box_color)
        strokeWidth = 8f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        textSize = 42f
        isAntiAlias = true
    }

    private val textBackgroundPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        alpha = 160
    }

    var showBoundingBoxes: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private val guideRectPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
    }

    private val bounds = Rect()

    fun setResults(boundingBoxes: List<BoundingBox>, inputWidth: Int = previewWidth, inputHeight: Int = previewHeight) {
        results = boundingBoxes
        previewWidth = if (inputWidth != 0) inputWidth else 1
        previewHeight = if (inputHeight != 0) inputHeight else 1
        invalidate()
    }

    fun clear() {
        results = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (results.isNotEmpty() && showBoundingBoxes) {
            val viewAspectRatio = width.toFloat() / height
            val imageAspectRatio = previewWidth.toFloat() / previewHeight

            val scale: Float
            val offsetX: Float
            val offsetY: Float

            if (imageAspectRatio > viewAspectRatio) {
                scale = width.toFloat() / previewWidth
                val scaledHeight = previewHeight * scale
                offsetX = 0f
                offsetY = (height - scaledHeight) / 2
            } else {
                scale = height.toFloat() / previewHeight
                val scaledWidth = previewWidth * scale
                offsetX = (width - scaledWidth) / 2
                offsetY = 0f
            }

            results.forEach { box ->
                val left = box.x1 * previewWidth * scale + offsetX
                val top = box.y1 * previewHeight * scale + offsetY
                val right = box.x2 * previewWidth * scale + offsetX
                val bottom = box.y2 * previewHeight * scale + offsetY

                // Draw bounding box
                canvas.drawRect(left, top, right, bottom, boxPaint)

                // Draw label
                val label = box.clsName
                textPaint.getTextBounds(label, 0, label.length, bounds)
                val textWidth = bounds.width().toFloat()
                val textHeight = bounds.height().toFloat()

                val backgroundRect = RectF(
                    left,
                    top - textHeight - 16f,
                    left + textWidth + 24f,
                    top
                )

                canvas.drawRoundRect(backgroundRect, 8f, 8f, textBackgroundPaint)
                canvas.drawText(label, left + 12f, top - 8f, textPaint)
            }
        }

        drawCenterGuideBox(canvas)
    }

    private fun drawCenterGuideBox(canvas: Canvas) {
        val rectWidth = width * 0.9f
        val rectHeight = rectWidth * 0.63f // Standard ID card aspect ratio

        val centerX = width / 2f
        val centerY = height / 2f

        val left = centerX - rectWidth / 2
        val top = centerY - rectHeight / 2
        val right = centerX + rectWidth / 2
        val bottom = centerY + rectHeight / 2

        canvas.drawRect(left, top, right, bottom, guideRectPaint)
    }
}
