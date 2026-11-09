package fi.forkliftdriver

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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

    private val paint = Paint().apply { isAntiAlias = true }
    private val path = Path()

    // Trukin maailmankoordinaatit
    private var forkliftX = 250f
    private var forkliftY = 300f
    private var forkliftAngle = 0f

    var forkHeight = 0f
        private set

    // Ohjaus
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
            forkliftAngle += currentSteering * (currentThrottle / 6f)
            val rad = Math.toRadians(forkliftAngle.toDouble())
            forkliftX += (currentThrottle * sin(rad)).toFloat()
            forkliftY -= (currentThrottle * cos(rad)).toFloat()
        }
    }

    fun adjustForks(amount: Float) {
        forkHeight = (forkHeight + amount).coerceIn(0f, 100f)
    }

    // Isometrinen muunnos: Muuttaa 2D-maailmankoordinaatin isometriseksi näyttöpisteeksi
    private fun toIsoX(wx: Float, wy: Float): Float {
        return (wx - wy) * 0.866f + width * 0.5f
    }

    private fun toIsoY(wx: Float, wy: Float, wz: Float = 0f): Float {
        return (wx + wy) * 0.5f - wz + 150f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Tausta: Tehtaan betoninharmaa
        canvas.drawColor(0xFF1E2024.toInt())

        // 1. ISOMETRINEN LATTIALAATOITUS
        drawIsometricFloor(canvas)

        // 2. YMPÄRISTÖ: VARASTOHYLLYT JA LAATIKOT
        drawShelves(canvas)

        // 3. ISOMETRINEN TRUKKI
        drawIsometricForklift(canvas)

        // 4. TOUCHPAD-OHJAUSALUEEN VISUAALINEN OSOITIN
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
        paint.color = 0x25FFFFFF.toInt()

        val gridSize = 60f
        for (i in -4..10) {
            val startX = toIsoX(i * gridSize, -4 * gridSize)
            val startY = toIsoY(i * gridSize, -4 * gridSize)
            val endX = toIsoX(i * gridSize, 10 * gridSize)
            val endY = toIsoY(i * gridSize, 10 * gridSize)
            canvas.drawLine(startX, startY, endX, endY, paint)
        }
        for (j in -4..10) {
            val startX = toIsoX(-4 * gridSize, j * gridSize)
            val startY = toIsoY(-4 * gridSize, j * gridSize)
            val endX = toIsoX(10 * gridSize, j * gridSize)
            val endY = toIsoY(10 * gridSize, j * gridSize)
            canvas.drawLine(startX, startY, endX, endY, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawShelves(canvas: Canvas) {
        // Hyllystö vasemmalle reunalle
        drawShelfUnit(canvas, -120f, 60f, 80f, 220f, 120f)
        // Hyllystö taustalle
        drawShelfUnit(canvas, 100f, -120f, 220f, 80f, 120f)
    }

    private fun drawShelfUnit(canvas: Canvas, ox: Float, oy: Float, sizeX: Float, sizeY: Float, heightZ: Float) {
        // Palkkien värit
        val colPillar = 0xFF1976D2.toInt() // Siniset varastohyllyn tolpat
        val colShelf = 0xFFFFA000.toInt()  // Oranssit tasot
        val colBox = 0xFF8D6E63.toInt()    // Ruskeat pahvilaatikot

        // Hyllyn taso
        drawIsoBox(canvas, ox, oy, 40f, sizeX, sizeY, 8f, colShelf)
        drawIsoBox(canvas, ox, oy, 90f, sizeX, sizeY, 8f, colShelf)

        // Laatikoita tasoille
        drawIsoBox(canvas, ox + 15f, oy + 20f, 48f, 35f, 35f, 35f, colBox)
        drawIsoBox(canvas, ox + 15f, oy + 70f, 48f, 35f, 35f, 35f, colBox)
        drawIsoBox(canvas, ox + 20f, oy + 30f, 98f, 30f, 30f, 30f, colBox)

        // 4 pystypalkkia
        drawIsoBox(canvas, ox, oy, 0f, 8f, 8f, heightZ, colPillar)
        drawIsoBox(canvas, ox + sizeX - 8f, oy, 0f, 8f, 8f, heightZ, colPillar)
        drawIsoBox(canvas, ox, oy + sizeY - 8f, 0f, 8f, 8f, heightZ, colPillar)
        drawIsoBox(canvas, ox + sizeX - 8f, oy + sizeY - 8f, 0f, 8f, 8f, heightZ, colPillar)
    }

    private fun drawIsometricForklift(canvas: Canvas) {
        val rad = Math.toRadians(forkliftAngle.toDouble())
        val forwardX = -sin(rad).toFloat()
        val forwardY = cos(rad).toFloat()
        val rightX = cos(rad).toFloat()
        val rightY = sin(rad).toFloat()

        val baseX = toIsoX(forkliftX, forkliftY)
        val baseY = toIsoY(forkliftX, forkliftY)

        // 1. Varjo maassa
        paint.color = 0x55000000.toInt()
        canvas.drawOval(baseX - 45f, baseY - 20f, baseX + 45f, baseY + 20f, paint)

        // 2. Trukin runko (kulta/keltainen isometrinen blokki)
        drawIsoBox(canvas, forkliftX - 25f, forkliftY - 25f, 0f, 50f, 50f, 35f, 0xFFFFB300.toInt())

        // 3. Musta ohjaamon turvakehikko
        drawIsoBox(canvas, forkliftX - 20f, forkliftY - 20f, 35f, 6f, 6f, 45f, 0xFF1C1C1C.toInt())
        drawIsoBox(canvas, forkliftX + 14f, forkliftY - 20f, 35f, 6f, 6f, 45f, 0xFF1C1C1C.toInt())
        drawIsoBox(canvas, forkliftX - 20f, forkliftY + 14f, 35f, 6f, 6f, 45f, 0xFF1C1C1C.toInt())
        drawIsoBox(canvas, forkliftX + 14f, forkliftY + 14f, 35f, 6f, 6f, 45f, 0xFF1C1C1C.toInt())
        // Katto
        drawIsoBox(canvas, forkliftX - 22f, forkliftY - 22f, 80f, 44f, 44f, 6f, 0xFF2B2B2B.toInt())

        // 4. Masto ja nostohaarukat
        val mastX = forkliftX + forwardX * 32f
        val mastY = forkliftY + forwardY * 32f
        drawIsoBox(canvas, mastX - 18f, mastY - 4f, 0f, 36f, 8f, 75f, 0xFF37474F.toInt())

        // Piikit (haarukat maassa / nousevat säädön mukaan)
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

    // Apumetodi: Piirtää isometrisen 3D-laatikon (yläpinta, vasen sivu, oikea sivu valaistuksella)
    private fun drawIsoBox(
        canvas: Canvas,
        x: Float, y: Float, z: Float,
        w: Float, l: Float, h: Float,
        baseColor: Int
    ) {
        // Yläpinnan pisteet
        val x0 = toIsoX(x, y)
        val y0 = toIsoY(x, y, z + h)
        val x1 = toIsoX(x + w, y)
        val y1 = toIsoY(x + w, y, z + h)
        val x2 = toIsoX(x + w, y + l)
        val y2 = toIsoY(x + w, y + l, z + h)
        val x3 = toIsoX(x, y + l)
        val y3 = toIsoY(x, y + l, z + h)

        // Alakulmapisteet
        val by1 = toIsoY(x + w, y, z)
        val by2 = toIsoY(x + w, y + l, z)
        val by3 = toIsoY(x, y + l, z)

        // Oikea sivu (tummennettu)
        paint.color = adjustAlphaAndBrightness(baseColor, 0.7f)
        path.reset()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        path.lineTo(x2, by2)
        path.lineTo(x1, by1)
        path.close()
        canvas.drawPath(path, paint)

        // Etu/Vasen sivu (hieman tummennettu)
        paint.color = adjustAlphaAndBrightness(baseColor, 0.85f)
        path.reset()
        path.moveTo(x2, y2)
        path.lineTo(x3, y3)
        path.lineTo(x3, by3)
        path.lineTo(x2, by2)
        path.close()
        canvas.drawPath(path, paint)

        // Yläpinta (täysi kirkkaus)
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
