package fi.forkliftdriver

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class ForkliftGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply { isAntiAlias = true }
    private val path = Path()

    private var forkliftX = 250f
    private var forkliftY = 300f
    private var forkliftAngle = 0f
    var forkHeight = 0f
        private set

    private var palletX = 250f
    private var palletY = 160f
    private var palletZ = 0f
    private var isPalletLoaded = false

    private val shelfColliders = listOf(
        RectF(-120f, 60f, -40f, 280f),
        RectF(100f, -120f, 320f, -40f)
    )

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
                if (event.y > height * 0.45f) {
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
                    currentThrottle = (-deltaY * 0.04f).coerceIn(-6f, 6f)
                    currentSteering = (deltaX * 0.03f).coerceIn(-3.5f, 3.5f)
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
            val nextAngle = forkliftAngle + currentSteering * (currentThrottle / 6f)
            val rad = Math.toRadians(nextAngle.toDouble())
            val nextX = forkliftX + (currentThrottle * sin(rad)).toFloat()
            val nextY = forkliftY - (currentThrottle * cos(rad)).toFloat()

            var collides = false
            for (box in shelfColliders) {
                if (nextX + 30f > box.left && nextX - 30f < box.right &&
                    nextY + 30f > box.top && nextY - 30f < box.bottom
                ) {
                    collides = true
                    break
                }
            }

            if (!collides) {
                forkliftAngle = nextAngle
                forkliftX = nextX
                forkliftY = nextY
            }
        }

        val rad = Math.toRadians(forkliftAngle.toDouble())
        val forwardX = -sin(rad).toFloat()
        val forwardY = cos(rad).toFloat()
        val forkTipX = forkliftX + forwardX * 45f
        val forkTipY = forkliftY + forwardY * 45f

        val distToPallet = hypot(forkTipX - (palletX + 25f), forkTipY - (palletY + 20f))
        if (!isPalletLoaded) {
            if (distToPallet < 35f && forkHeight > 5f) {
                isPalletLoaded = true
            }
        } else {
            palletX = forkTipX - 25f
            palletY = forkTipY - 20f
            palletZ = forkHeight * 0.5f

            if (forkHeight <= 2f) {
                isPalletLoaded = false
                palletZ = 0f
            }
        }
    }

    fun adjustForks(amount: Float) {
        forkHeight = (forkHeight + amount).coerceIn(0f, 100f)
    }

    private fun toIsoX(wx: Float, wy: Float): Float = (wx - wy) * 0.866f + width * 0.5f
    private fun toIsoY(wx: Float, wy: Float, wz: Float = 0f): Float = (wx + wy) * 0.5f - wz + 150f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(0xFF1E2024.toInt())

        drawIsometricFloor(canvas)
        drawShelves(canvas)
        drawPallet(canvas)
        drawIsometricForklift(canvas)

        paint.color = 0x22FFFFFF.toInt()
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, height * 0.45f, width.toFloat(), height.toFloat(), paint)

        paint.color = 0x55FFFFFF.toInt()
        paint.strokeWidth = 3f
        canvas.drawLine(0f, height * 0.45f, width.toFloat(), height * 0.45f, paint)
    }

    private fun drawIsometricFloor(canvas: Canvas) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = 0x20FFFFFF.toInt()

        val gridSize = 60f
        for (i in -4..10) {
            canvas.drawLine(
                toIsoX(i * gridSize, -4 * gridSize),
                toIsoY(i * gridSize, -4 * gridSize),
                toIsoX(i * gridSize, 10 * gridSize),
                toIsoY(i * gridSize, 10 * gridSize),
                paint
            )
        }
        for (j in -4..10) {
            canvas.drawLine(
                toIsoX(-4 * gridSize, j * gridSize),
                toIsoY(-4 * gridSize, j * gridSize),
                toIsoX(10 * gridSize, j * gridSize),
                toIsoY(10 * gridSize, j * gridSize),
                paint
            )
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawShelves(canvas: Canvas) {
        drawShelfUnit(canvas, -120f, 60f, 80f, 220f, 120f)
        drawShelfUnit(canvas, 100f, -120f, 220f, 80f, 120f)
    }

    private fun drawShelfUnit(canvas: Canvas, ox: Float, oy: Float, sizeX: Float, sizeY: Float, heightZ: Float) {
        val colPillar = 0xFF1976D2.toInt()
        val colShelf = 0xFFFFA000.toInt()
        val colBox = 0xFF8D6E63.toInt()

        drawIsoBox(canvas, ox, oy, 40f, sizeX, sizeY, 8f, colShelf)
        drawIsoBox(canvas, ox, oy, 90f, sizeX, sizeY, 8f, colShelf)

        drawIsoBox(canvas, ox + 15f, oy + 20f, 48f, 35f, 35f, 35f, colBox)
        drawIsoBox(canvas, ox + 15f, oy + 70f, 48f, 35f, 35f, 35f, colBox)
        drawIsoBox(canvas, ox + 20f, oy + 30f, 98f, 30f, 30f, 30f, colBox)

        drawIsoBox(canvas, ox, oy, 0f, 8f, 8f, heightZ, colPillar)
        drawIsoBox(canvas, ox + sizeX - 8f, oy, 0f, 8f, 8f, heightZ, colPillar)
        drawIsoBox(canvas, ox, oy + sizeY - 8f, 0f, 8f, 8f, heightZ, colPillar)
        drawIsoBox(canvas, ox + sizeX - 8f, oy + sizeY - 8f, 0f, 8f, 8f, heightZ, colPillar)
    }

    private fun drawPallet(canvas: Canvas) {
        if (palletZ > 0f) {
            val sx = toIsoX(palletX + 25f, palletY + 20f)
            val sy = toIsoY(palletX + 25f, palletY + 20f, 0f)
            paint.color = 0x44000000.toInt()
            canvas.drawOval(sx - 35f, sy - 15f, sx + 35f, sy + 15f, paint)
        }

        drawIsoBox(canvas, palletX, palletY, palletZ, 50f, 40f, 8f, 0xFFC89D66.toInt())
        drawIsoBox(canvas, palletX + 5f, palletY + 5f, palletZ + 8f, 40f, 30f, 35f, 0xFFA17A4E.toInt())
    }

    private fun drawIsometricForklift(canvas: Canvas) {
        val rad = Math.toRadians(forkliftAngle.toDouble())
        val forwardX = -sin(rad).toFloat()
        val forwardY = cos(rad).toFloat()
        val rightX = cos(rad).toFloat()
        val rightY = sin(rad).toFloat()

        val baseX = toIsoX(forkliftX, forkliftY)
        val baseY = toIsoY(forkliftX, forkliftY)

        paint.color = 0x55000000.toInt()
        canvas.drawOval(baseX - 45f, baseY - 20f, baseX + 45f, baseY + 20f, paint)

        drawIsoBox(canvas, forkliftX - 25f, forkliftY - 25f, 0f, 50f, 50f, 35f, 0xFFFFB300.toInt())

        drawIsoBox(canvas, forkliftX - 20f, forkliftY - 20f, 35f, 6f, 6f, 45f, 0xFF1C1C1C.toInt())
        drawIsoBox(canvas, forkliftX + 14f, forkliftY - 20f, 35f, 6f, 6f, 45f, 0xFF1C1C1C.toInt())
        drawIsoBox(canvas, forkliftX - 20f, forkliftY + 14f, 35f, 6f, 6f, 45f, 0xFF1C1C1C.toInt())
        drawIsoBox(canvas, forkliftX + 14f, forkliftY + 14f, 35f, 6f, 6f, 45f, 0xFF1C1C1C.toInt())
        drawIsoBox(canvas, forkliftX - 22f, forkliftY - 22f, 80f, 44f, 44f, 6f, 0xFF2B2B2B.toInt())

        val mastX = forkliftX + forwardX * 32f
        val mastY = forkliftY + forwardY * 32f
        drawIsoBox(canvas, mastX - 18f, mastY - 4f, 0f, 36f, 8f, 75f, 0xFF37474F.toInt())

        val forkElevation = forkHeight * 0.5f
        val forkTipX = mastX + forwardX * 30f
        val forkTipY = mastY + forwardY * 30f

        paint.color = 0xFF9E9E9E.toInt()
        val p1x = toIsoX(mastX - 12f * rightX, mastY - 12f * rightY)
        val p1y = toIsoY(mastX - 12f * rightX, mastY - 12f * rightY, forkElevation)
        val p2x = toIsoX(forkTipX - 12f * rightX, forkTipY - 12f * rightY)
        val p2y = toIsoY(forkTipX - 12f * rightX, forkTipY - 12f * rightY, forkElevation)

        val p3x = toIsoX(mastX + 12f * rightX, mastY + 12f * rightY)
        val p3y = toIsoY(mastX + 12f * rightX, mastY + 12f * rightY, forkElevation)
        val p4x = toIsoX(forkTipX + 12f * rightX, forkTipY + 12f * rightY)
        val p4y = toIsoY(forkTipX + 12f * rightX, forkTipY + 12f * rightY, forkElevation)

        paint.strokeWidth = 6f
        canvas.drawLine(p1x, p1y, p2x, p2y, paint)
        canvas.drawLine(p3x, p3y, p4x, p4y, paint)
    }

    private fun drawIsoBox(
        canvas: Canvas,
        x: Float, y: Float, z: Float,
        w: Float, l: Float, h: Float,
        baseColor: Int
    ) {
        val x0 = toIsoX(x, y)
        val y0 = toIsoY(x, y, z + h)
        val x1 = toIsoX(x + w, y)
        val y1 = toIsoY(x + w, y, z + h)
        val x2 = toIsoX(x + w, y + l)
        val y2 = toIsoY(x + w, y + l, z + h)
        val x3 = toIsoX(x, y + l)
        val y3 = toIsoY(x, y + l, z + h)

        val by1 = toIsoY(x + w, y, z)
        val by2 = toIsoY(x + w, y + l, z)
        val by3 = toIsoY(x, y + l, z)

        paint.color = adjustAlphaAndBrightness(baseColor, 0.7f)
        path.reset()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        path.lineTo(x2, by2)
        path.lineTo(x1, by1)
        path.close()
        canvas.drawPath(path, paint)

        paint.color = adjustAlphaAndBrightness(baseColor, 0.85f)
        path.reset()
        path.moveTo(x2, y2)
        path.lineTo(x3, y3)
        path.lineTo(x3, by3)
        path.lineTo(x2, by2)
        path.close()
        canvas.drawPath(path, paint)

        paint.color = baseColor
        path.reset()
        path.moveTo(x0, y0)
        path.lineTo(x1, y1)
        path.lineTo(x2, y2)
        path.lineTo(x3, y3)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun adjustAlphaAndBrightness(color: Int, factor: Float): Int {
        val a = (color shr 24) and 0xFF
        val r = (((color shr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = (((color shr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((color and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
