package fi.forkliftdriver

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class ForkliftGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        isAntiAlias = true
    }

    private var forkliftX = 500f
    private var forkliftY = 800f
    private var forkliftAngle = 0f

    var forkHeight = 0f
        private set

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var isTouching = false
    private var currentThrottle = 0f
    private var currentSteering = 0f

    private val runnable = object : Runnable {
        override fun run() {
            updatePhysics()
            invalidate()
            postDelayed(this, 16)
        }
    }

    init {
        post(runnable)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (event.y > height * 0.5f) {
                    touchStartX = event.x
                    touchStartY = event.y
                    isTouching = true
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isTouching) {
                    val deltaX = event.x - touchStartX
                    val deltaY = event.y - touchStartY

                    currentThrottle = (-deltaY * 0.05f).coerceIn(-8f, 8f)
                    currentSteering = (deltaX * 0.03f).coerceIn(-4f, 4f)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTouching = false
                currentThrottle = 0f
                currentSteering = 0f
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updatePhysics() {
        if (currentThrottle != 0f) {
            forkliftAngle += currentSteering * (currentThrottle / 8f)
            val rad = Math.toRadians(forkliftAngle.toDouble())
            
            forkliftX += (currentThrottle * sin(rad)).toFloat()
            forkliftY -= (currentThrottle * cos(rad)).toFloat()
        }
    }

    fun adjustForks(amount: Float) {
        forkHeight = (forkHeight + amount).coerceIn(0f, 100f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(0xFF2B2B2B.toInt())

        paint.color = 0x22FFFFFF.toInt()
        canvas.drawRect(0f, height * 0.5f, width.toFloat(), height.toFloat(), paint)

        canvas.save()
        canvas.translate(forkliftX, forkliftY)
        canvas.rotate(forkliftAngle)

        paint.color = 0xFFFFB300.toInt()
        canvas.drawRect(-40f, -60f, 40f, 60f, paint)

        paint.color = 0xFF1E1E1E.toInt()
        canvas.drawRect(-30f, -20f, 30f, 30f, paint)

        paint.color = 0xFFB0BEC5.toInt()
        val forkOffset = forkHeight * 0.3f
        canvas.drawRect(-28f, -90f - forkOffset, -18f, -60f, paint)
        canvas.drawRect(18f, -90f - forkOffset, 28f, -60f, paint)

        canvas.restore()
    }
}
