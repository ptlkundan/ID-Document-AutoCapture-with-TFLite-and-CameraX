package com.surendramaran.yolov8tflite

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class DotProgressBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val dots = mutableListOf<View>()
    private var animatorSet: AnimatorSet? = null
    private val statusTextView: TextView

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER

        // Dot container
        val dotLayout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
        }

        repeat(5) {
            val dot = View(context).apply {
                layoutParams = LayoutParams(20, 20).apply {
                    setMargins(10, 0, 10, 0)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#76FF03")) // default color
                }
                scaleX = 0.5f
                scaleY = 0.5f
            }
            dotLayout.addView(dot)
            dots.add(dot)
        }

        addView(dotLayout)

        // Status message
        statusTextView = TextView(context).apply {
            text = "Detecting card..."
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }

        addView(statusTextView)

        startAnimation()
    }

    fun startAnimation() {
        val animators = dots.flatMapIndexed { index, dot ->
            listOf(
                ObjectAnimator.ofFloat(dot, "scaleX", 0.5f, 1f, 0.5f).apply {
                    duration = 600
                    repeatCount = ValueAnimator.INFINITE
                    startDelay = index * 150L
                },
                ObjectAnimator.ofFloat(dot, "scaleY", 0.5f, 1f, 0.5f).apply {
                    duration = 600
                    repeatCount = ValueAnimator.INFINITE
                    startDelay = index * 150L
                }
            )
        }

        animatorSet = AnimatorSet().apply {
            playTogether(animators)
            start()
        }
    }

    fun stopAnimation() {
        animatorSet?.cancel()
        animate().alpha(0f).setDuration(300).withEndAction {
            visibility = View.GONE
            alpha = 1f
        }.start()
    }

    fun updateStatus(message: String) {
        statusTextView.text = message
    }

    fun setDotColor(color: Int) {
        dots.forEach {
            (it.background as GradientDrawable).setColor(color)
        }
    }

    fun reset() {
        visibility = View.VISIBLE
        alpha = 1f
        updateStatus("Detecting card...")
        setDotColor(Color.parseColor("#76FF03"))
        startAnimation()
    }
}
