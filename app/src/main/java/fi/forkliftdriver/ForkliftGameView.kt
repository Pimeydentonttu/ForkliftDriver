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

    // Trukin sijainti (isompi mittakaava)
    private var forkliftX = 260f
    private var forkliftY = 320f
    private var forkliftAngle = 0f
    var forkHeight = 0f
        private set

    // Eurolava ja kuorma
    private var palletX = 250f
    private var palletY = 140f
    private var palletZ = 0f
    private var isPalletLoaded = false

    // Törmäysalueet (hyllyt, seinät ja rekan runko)
    private val colliders = listOf(
        RectF(-180f, 40f, -80f, 360f),    // Vasen iso hyllystö
        RectF(60f, -200f, 380f, -100f),   // Takaseinän hyllystö
        RectF(400f, 100f, 560f, 500f)     // Lastausalueen laiturin seinämä
    )

    // Kosketusohjaus (pienennetty alaosaan)
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchCurrentX = 0f
    private var touchCurrentY = 0f
    private var isTouching = false
    private var currentThrottle = 0f
    private var currentSteering = 0f

    // Ohjausalueen korkeusraja (vain alin 30% ruudusta)
    private val controlAreaRatio = 0.70f

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
                if (event.y > height * controlAreaRatio) {
                    touchStartX = event.x
                    touchStartY = event.y
                    touchCurrentX = event.x
                    touchCurrentY = event.y
                    isTouching = true
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isTouching) {
                    touchCurrentX = event.x
                    touchCurrentY = event.y
                    val deltaX = touchCurrentX - touchStartX
                    val deltaY = touchCurrentY - touchStartY

                    currentThrottle = (-deltaY * 0.05f).coerceIn(-6f, 6f)
                    currentSteering = (deltaX * 0.04f).coerceIn(-3.5f, 3.5f)
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

            val nextX = forkliftX - (currentThrottle * sin(rad)).toFloat()
            val nextY = forkliftY + (currentThrottle * cos(rad)).toFloat()

            // Isomman trukin törmäyssäde 40f
            var collides = false
            for (box in colliders) {
                if (nextX + 40f > box.left && nextX - 40f < box.right &&
                    nextY + 40f > box.top && nextY - 40f < box.bottom
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

        // Piikkien mitta ja sijainti isommalla skaalalla
        val rad = Math.toRadians(forkliftAngle.toDouble())
        val forwardX = -sin(rad).toFloat()
        val forwardY = cos(rad).toFloat()
        val forkTipX = forkliftX + forwardX * 60f
        val forkTipY = forkliftY + forwardY * 60f

        val distToPallet = hypot(forkTipX - (palletX + 35f), forkTipY - (palletY + 30f))
        if (!isPalletLoaded) {
            if (distToPallet < 45f && forkHeight > 5f) {
                isPalletLoaded = true
            }
        } else {
            palletX = forkTipX - 35f
            palletY = forkTipY - 30f
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

    // Isometrinen muunnos (kamera seuraa loivasti trukkia pitääkseen sen keskiössä)
    private fun toIsoX(wx: Float, wy: Float): Float {
        val camOffsetX = (forkliftX - 250f) * 0.3f
        val camOffsetY = (forkliftY - 300f) * 0.3f
        return (wx - camOffsetX - (wy - camOffsetY)) * 0.866f + width * 0.5f
    }

    private fun toIsoY(wx: Float, wy: Float, wz: Float = 0f): Float {
        val camOffsetX = (forkliftX - 250f) * 0.3f
        val camOffsetY = (forkliftY - 300f) * 0.3f
        return ((wx - camOffsetX) + (wy - camOffsetY)) * 0.5f - wz + 160f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Varaston taustaväri (betoni / hallin hämärä)
        canvas.drawColor(0xFF1B1E22.toInt())

        drawWarehouseFloor(canvas)
        drawLoadingDockAndTruck(canvas)
        drawWarehouseShelves(canvas)
        drawPallet(canvas)
        drawLargeForkliftWithDriver(canvas)

        // Pienennetty Touchpad-alue alareunassa
        paint.color = 0x28000000.toInt()
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, height * controlAreaRatio, width.toFloat(), height.toFloat(), paint)

        // Ohjausalueen korosteviiva ja teksti
        paint.color = 0x55FFA000.toInt()
        paint.strokeWidth = 3f
        canvas.drawLine(0f, height * controlAreaRatio, width.toFloat(), height * controlAreaRatio, paint)

        if (isTouching) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = 0x55FFFFFF.toInt()
            canvas.drawCircle(touchStartX, touchStartY, 60f, paint)

            paint.style = Paint.Style.FILL
            paint.color = 0xCCFFB300.toInt()
            canvas.drawCircle(touchCurrentX, touchCurrentY, 26f, paint)
        }
    }

    private fun drawWarehouseFloor(canvas: Canvas) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = 0x1AFFFFFF.toInt()

        val step = 70f
        for (i in -4..12) {
            canvas.drawLine(toIsoX(i * step, -4 * step), toIsoY(i * step, -4 * step), toIsoX(i * step, 12 * step), toIsoY(i * step, 12 * step), paint)
        }
        for (j in -4..12) {
            canvas.drawLine(toIsoX(-4 * step, j * step), toIsoY(-4 * step, j * step), toIsoX(12 * step, j * step), toIsoY(12 * step, j * step), paint)
        }

        // Keltaiset turvaviivat / trukkikaistat lattiaan
        paint.color = 0x77FFD54F.toInt()
        paint.strokeWidth = 5f
        canvas.drawLine(toIsoX(-60f, -40f), toIsoY(-60f, -40f), toIsoX(-60f, 450f), toIsoY(-60f, 450f), paint)
        canvas.drawLine(toIsoX(380f, -40f), toIsoY(380f, -40f), toIsoX(380f, 450f), toIsoY(380f, 450f), paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawLoadingDockAndTruck(canvas: Canvas) {
        // Lastauslaituri (korotettu taso)
        drawIsoBox(canvas, 420f, 80f, 0f, 40f, 350f, 30f, 0xFF455A64.toInt())

        // Lastausrampin varoitusraidat (kelta-musta)
        drawIsoBox(canvas, 410f, 120f, 0f, 10f, 120f, 30f, 0xFFFFB300.toInt())

        // REKKA (puoliperävaunu laiturin perässä perä auki lastausta varten)
        // Rekan runko / kontti
        drawIsoBox(canvas, 460f, 80f, 0f, 120f, 320f, 110f, 0xFFECEFF1.toInt())
        // Rekan sisusta (auki oleva lastaustila)
        drawIsoBox(canvas, 460f, 85f, 25f, 110f, 300f, 80f, 0xFF263238.toInt())
        // Rekan pyörät laiturin alla
        drawIsoBox(canvas, 470f, 340f, 0f, 20f, 40f, 25f, 0xFF111111.toInt())
        drawIsoBox(canvas, 550f, 340f, 0f, 20f, 40f, 25f, 0xFF111111.toInt())
        // Avoimet takaovet sivuilla
        drawIsoBox(canvas, 445f, 75f, 25f, 15f, 8f, 90f, 0xFFB0BEC5.toInt())
        drawIsoBox(canvas, 580f, 75f, 25f, 15f, 8f, 90f, 0xFFB0BEC5.toInt())
    }

    private fun drawWarehouseShelves(canvas: Canvas) {
        // Iso pystyhylly vasemmalla
        drawShelfUnit(canvas, -170f, 40f, 90f, 280f, 160f)
        // Iso takahylly
        drawShelfUnit(canvas, 70f, -190f, 280f, 90f, 160f)
    }

    private fun drawShelfUnit(canvas: Canvas, ox: Float, oy: Float, sizeX: Float, sizeY: Float, heightZ: Float) {
        val colPillar = 0xFF1565C0.toInt()
        val colShelf = 0xFFE65100.toInt()
        val colBox = 0xFF8D6E63.toInt()

        // Hyllytasot (3 kpl)
        drawIsoBox(canvas, ox, oy, 50f, sizeX, sizeY, 10f, colShelf)
        drawIsoBox(canvas, ox, oy, 105f, sizeX, sizeY, 10f, colShelf)
        drawIsoBox(canvas, ox, oy, 155f, sizeX, sizeY, 10f, colShelf)

        // Laatikoita hyllyille
        drawIsoBox(canvas, ox + 15f, oy + 20f, 60f, 45f, 45f, 40f, colBox)
        drawIsoBox(canvas, ox + 15f, oy + 85f, 60f, 45f, 45f, 40f, colBox)
        drawIsoBox(canvas, ox + 20f, oy + 35f, 115f, 40f, 40f, 35f, colBox)

        // 4 paksua teräspilaria
        drawIsoBox(canvas, ox, oy, 0f, 12f, 12f, heightZ, colPillar)
        drawIsoBox(canvas, ox + sizeX - 12f, oy, 0f, 12f, 12f, heightZ, colPillar)
        drawIsoBox(canvas, ox, oy + sizeY - 12f, 0f, 12f, 12f, heightZ, colPillar)
        drawIsoBox(canvas, ox + sizeX - 12f, oy + sizeY - 12f, 0f, 12f, 12f, heightZ, colPillar)
    }

    private fun drawPallet(canvas: Canvas) {
        if (palletZ > 0f) {
            val sx = toIsoX(palletX + 35f, palletY + 30f)
            val sy = toIsoY(palletX + 35f, palletY + 30f, 0f)
            paint.color = 0x44000000.toInt()
            canvas.drawOval(sx - 45f, sy - 20f, sx + 45f, sy + 20f, paint)
        }

        // Suurempi eurolava
        drawIsoBox(canvas, palletX, palletY, palletZ, 70f, 55f, 12f, 0xFFC89D66.toInt())
        // Kuormalaatikko lavalla
        drawIsoBox(canvas, palletX + 8f, palletY + 8f, palletZ + 12f, 54f, 40f, 45f, 0xFFA17A4E.toInt())
    }

    private fun drawLargeForkliftWithDriver(canvas: Canvas) {
        val rad = Math.toRadians(forkliftAngle.toDouble())
        val forwardX = -sin(rad).toFloat()
        val forwardY = cos(rad).toFloat()
        val rightX = cos(rad).toFloat()
        val rightY = sin(rad).toFloat()

        val baseX = toIsoX(forkliftX, forkliftY)
        val baseY = toIsoY(forkliftX, forkliftY)

        // Trukin varjo
        paint.color = 0x55000000.toInt()
        canvas.drawOval(baseX - 60f, baseY - 28f, baseX + 60f, baseY + 28f, paint)

        // Isompi runko (kulta/keltainen)
        drawIsoBox(canvas, forkliftX - 35f, forkliftY - 35f, 0f, 70f, 70f, 45f, 0xFFFFB300.toInt())
        // Vastapaino takana
        drawIsoBox(canvas, forkliftX - 32f, forkliftY + 22f, 10f, 64f, 12f, 35f, 0xFF37474F.toInt())

        // Penkki
        drawIsoBox(canvas, forkliftX - 18f, forkliftY - 5f, 25f, 36f, 30f, 18f, 0xFF212121.toInt())

        // --- KULJETTAJA (KUSKI) ---
        // Vartalo & huomioliivit
        drawIsoBox(canvas, forkliftX - 12f, forkliftY, 43f, 24f, 20f, 26f, 0xFFFDD835.toInt())
        // Siniset haalarihihat
        drawIsoBox(canvas, forkliftX - 16f, forkliftY + 2f, 43f, 6f, 16f, 20f, 0xFF1976D2.toInt())
        drawIsoBox(canvas, forkliftX + 10f, forkliftY + 2f, 43f, 6f, 16f, 20f, 0xFF1976D2.toInt())
        // Pää & kypärä
        drawIsoBox(canvas, forkliftX - 8f, forkliftY + 4f, 69f, 16f, 14f, 14f, 0xFFFFE0B2.toInt()) // Kasvot
        drawIsoBox(canvas, forkliftX - 9f, forkliftY + 3f, 79f, 18f, 16f, 9f, 0xFFFF5722.toInt())  // Oranssi kypärä

        // Ohjaamon suojakehikon 4 pilaria
        drawIsoBox(canvas, forkliftX - 30f, forkliftY - 28f, 45f, 7f, 7f, 55f, 0xFF1C1C1C.toInt())
        drawIsoBox(canvas, forkliftX + 23f, forkliftY - 28f, 45f, 7f, 7f, 55f, 0xFF1C1C1C.toInt())
        drawIsoBox(canvas, forkliftX - 30f, forkliftY + 23f, 45f, 7f, 7f, 55f, 0xFF1C1C1C.toInt())
        drawIsoBox(canvas, forkliftX + 23f, forkliftY + 23f, 45f, 7f, 7f, 55f, 0xFF1C1C1C.toInt())
        // Katto
        drawIsoBox(canvas, forkliftX - 32f, forkliftY - 30f, 100f, 64f, 60f, 8f, 0xFF263238.toInt())

        // Masto edessä
        val mastX = forkliftX + forwardX * 42f
        val mastY = forkliftY + forwardY * 42f
        drawIsoBox(canvas, mastX - 22f, mastY - 5f, 0f, 44f, 10f, 95f, 0xFF455A64.toInt())

        // Piikit (nostokorkeus volyyminapin mukaan)
        val forkElevation = forkHeight * 0.5f
        val forkTipX = mastX + forwardX * 44f
        val forkTipY = mastY + forwardY * 44f

        paint.color = 0xFFB0BEC5.toInt()
        val p1x = toIsoX(mastX - 16f * rightX, mastY - 16f * rightY)
        val p1y = toIsoY(mastX - 16f * rightX, mastY - 16f * rightY, forkElevation)
        val p2x = toIsoX(forkTipX - 16f * rightX, forkTipY - 16f * rightY)
        val p2y = toIsoY(forkTipX - 16f * rightX, forkTipY - 16f * rightY, forkElevation)

        val p3x = toIsoX(mastX + 16f * rightX, mastY + 16f * rightY)
        val p3y = toIsoY(mastX + 16f * rightX, mastY + 16f * rightY, forkElevation)
        val p4x = toIsoX(forkTipX + 16f * rightX, forkTipY + 16f * rightY)
        val p4y = toIsoY(forkTipX + 16f * rightX, forkTipY + 16f * rightY, forkElevation)

        paint.strokeWidth = 8f
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

        // Oikea sivu
        paint.color = adjustAlphaAndBrightness(baseColor, 0.7f)
        path.reset()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        path.lineTo(x2, by2)
        path.lineTo(x1, by1)
        path.close()
        canvas.drawPath(path, paint)

        // Vasen sivu
        paint.color = adjustAlphaAndBrightness(baseColor, 0.85f)
        path.reset()
        path.moveTo(x2, y2)
        path.lineTo(x3, y3)
        path.lineTo(x3, by3)
        path.lineTo(x2, by2)
        path.close()
        canvas.drawPath(path, paint)

        // Kansi
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
