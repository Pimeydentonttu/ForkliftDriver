package fi.forkliftdriver

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
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
    private val textPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()

    // Pelitilat
    enum class GameState { MENU, PLAYING, VICTORY }
    private var gameState = GameState.MENU

    // Tehtävä ja ajastin
    private var startTimeMs = 0L
    private var completionTimeSec = 0f
    private var deliveredCount = 0
    private val totalPallets = 2

    // Trukin sijainti
    private var forkliftX = 260f
    private var forkliftY = 320f
    private var forkliftAngle = 0f
    var forkHeight = 0f
        private set

    // Lavat tietorakenteena
    data class Pallet(
        var x: Float,
        var y: Float,
        var z: Float,
        var isLoaded: Boolean = false,
        var isDelivered: Boolean = false
    )

    private val pallets = mutableListOf(
        Pallet(250f, 140f, 0f),
        Pallet(-10f, 150f, 0f)
    )

    // Törmäysalueet (hyllyt ja laituri)
    private val colliders = listOf(
        RectF(-180f, 40f, -80f, 360f),
        RectF(60f, -200f, 380f, -100f),
        RectF(400f, 100f, 560f, 500f)
    )

    // Kosketusohjaus
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchCurrentX = 0f
    private var touchCurrentY = 0f
    private var isTouching = false
    private var currentThrottle = 0f
    private var currentSteering = 0f
    private val controlAreaRatio = 0.70f

    private val runnable = object : Runnable {
        override fun run() {
            if (gameState == GameState.PLAYING) {
                updatePhysics()
            }
            invalidate()
            postDelayed(this, 16)
        }
    }

    init {
        post(runnable)
    }

    private fun startNewGame() {
        forkliftX = 260f
        forkliftY = 320f
        forkliftAngle = 0f
        forkHeight = 0f
        pallets[0] = Pallet(250f, 140f, 0f)
        pallets[1] = Pallet(-10f, 150f, 0f)
        deliveredCount = 0
        startTimeMs = System.currentTimeMillis()
        gameState = GameState.PLAYING
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameState != GameState.PLAYING) {
            if (event.action == MotionEvent.ACTION_DOWN) {
                // Napautus ruudulla käynnistää uuden vuoron
                startNewGame()
                return true
            }
            return true
        }

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

        val rad = Math.toRadians(forkliftAngle.toDouble())
        val forwardX = -sin(rad).toFloat()
        val forwardY = cos(rad).toFloat()
        val forkTipX = forkliftX + forwardX * 60f
        val forkTipY = forkliftY + forwardY * 60f

        // Lavojen käsittely
        for (pallet in pallets) {
            if (pallet.isDelivered) continue

            val dist = hypot(forkTipX - (pallet.x + 35f), forkTipY - (pallet.y + 30f))

            if (!pallet.isLoaded) {
                // Nosto vain jos toista lavaa ei ole jo kyydissä
                val anyLoaded = pallets.any { it.isLoaded }
                if (!anyLoaded && dist < 45f && forkHeight > 5f) {
                    pallet.isLoaded = true
                }
            } else {
                pallet.x = forkTipX - 35f
                pallet.y = forkTipY - 30f
                pallet.z = forkHeight * 0.5f

                // Lavan lasku maahan
                if (forkHeight <= 2f) {
                    pallet.isLoaded = false
                    pallet.z = 0f

                    // Tarkistetaan, onko lava jätetty rekan kontin lastausalueelle (x: 470..570, y: 100..380)
                    if (pallet.x in 450f..570f && pallet.y in 90f..390f) {
                        pallet.isDelivered = true
                        deliveredCount++
                        if (deliveredCount >= totalPallets) {
                            completionTimeSec = (System.currentTimeMillis() - startTimeMs) / 1000f
                            gameState = GameState.VICTORY
                        }
                    }
                }
            }
        }
    }

    fun adjustForks(amount: Float) {
        if (gameState == GameState.PLAYING) {
            forkHeight = (forkHeight + amount).coerceIn(0f, 100f)
        }
    }

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

        canvas.drawColor(0xFF1B1E22.toInt())

        drawWarehouseFloor(canvas)
        drawLoadingDockAndTruck(canvas)
        drawWarehouseShelves(canvas)

        for (pallet in pallets) {
            drawPallet(canvas, pallet)
        }

        drawLargeForkliftWithDriver(canvas)
        drawHUD(canvas)

        // Valikkonäytöt
        if (gameState == GameState.MENU) {
            drawMenuOverlay(canvas, "TRUKKIKUSKI", "Aloita työvuoro napauttamalla", "Tehtävä: Lastaa 2 lavaa rekan kyytiin")
        } else if (gameState == GameState.VICTORY) {
            val timeText = "Aika: ${"%.1f".format(completionTimeSec)} sekuntia"
            drawMenuOverlay(canvas, "LASTAUS VALMIS!", timeText, "Napauta pelataksesi uudelleen")
        }
    }

    private fun drawHUD(canvas: Canvas) {
        // Ohjausalueen korostus
        paint.color = 0x25000000.toInt()
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, height * controlAreaRatio, width.toFloat(), height.toFloat(), paint)

        paint.color = 0x55FFA000.toInt()
        paint.strokeWidth = 3f
        canvas.drawLine(0f, height * controlAreaRatio, width.toFloat(), height * controlAreaRatio, paint)

        if (gameState == GameState.PLAYING) {
            // Yläpalkki (HUD)
            paint.color = 0xCC111315.toInt()
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(20f, 30f, width - 20f, 130f, 16f, 16f, paint)

            val elapsedSec = ((System.currentTimeMillis() - startTimeMs) / 1000f).toInt()
            textPaint.color = Color.WHITE
            textPaint.textSize = 34f
            textPaint.isFakeBoldText = true
            canvas.drawText("LAVAT: $deliveredCount / $totalPallets", width * 0.3f, 90f, textPaint)

            textPaint.color = 0xFFFFD54F.toInt()
            canvas.drawText("AIKA: ${elapsedSec}s", width * 0.75f, 90f, textPaint)

            // Joystick
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
    }

    private fun drawMenuOverlay(canvas: Canvas, title: String, subtitle: String, hint: String) {
        paint.color = 0xDD0B0D0E.toInt()
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        textPaint.color = 0xFFFFB300.toInt()
        textPaint.textSize = 62f
        textPaint.isFakeBoldText = true
        canvas.drawText(title, width * 0.5f, height * 0.38f, textPaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 34f
        textPaint.isFakeBoldText = false
        canvas.drawText(subtitle, width * 0.5f, height * 0.46f, textPaint)

        textPaint.color = 0xFF9E9E9E.toInt()
        textPaint.textSize = 28f
        canvas.drawText(hint, width * 0.5f, height * 0.54f, textPaint)
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

        // Keltaiset turvakaistat
        paint.color = 0x77FFD54F.toInt()
        paint.strokeWidth = 5f
        canvas.drawLine(toIsoX(-60f, -40f), toIsoY(-60f, -40f), toIsoX(-60f, 450f), toIsoY(-60f, 450f), paint)
        canvas.drawLine(toIsoX(380f, -40f), toIsoY(380f, -40f), toIsoX(380f, 450f), toIsoY(380f, 450f), paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawLoadingDockAndTruck(canvas: Canvas) {
        // Laituri
        drawIsoBox(canvas, 420f, 80f, 0f, 40f, 350f, 30f, 0xFF455A64.toInt())
        // Rampin varoitusväri
        drawIsoBox(canvas, 410f, 120f, 0f, 10f, 120f, 30f, 0xFFFFB300.toInt())

        // REKKA
        drawIsoBox(canvas, 460f, 80f, 0f, 120f, 320f, 110f, 0xFFECEFF1.toInt())
        drawIsoBox(canvas, 460f, 85f, 25f, 110f, 300f, 80f, 0xFF263238.toInt()) // Kontin sisus
        drawIsoBox(canvas, 470f, 340f, 0f, 20f, 40f, 25f, 0xFF111111.toInt())
        drawIsoBox(canvas, 550f, 340f, 0f, 20f, 40f, 25f, 0xFF111111.toInt())

        // Vihreä maali-/purkualue kontin sisään
        paint.color = 0x444CAF50.toInt()
        val g1x = toIsoX(465f, 95f)
        val g1y = toIsoY(465f, 95f, 26f)
        val g2x = toIsoX(565f, 95f)
        val g2y = toIsoY(565f, 95f, 26f)
        val g3x = toIsoX(565f, 375f)
        val g3y = toIsoY(565f, 375f, 26f)
        val g4x = toIsoX(465f, 375f)
        val g4y = toIsoY(465f, 375f, 26f)

        path.reset()
        path.moveTo(g1x, g1y)
        path.lineTo(g2x, g2y)
        path.lineTo(g3x, g3y)
        path.lineTo(g4x, g4y)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawWarehouseShelves(canvas: Canvas) {
        drawShelfUnit(canvas, -170f, 40f, 90f, 280f, 160f)
        drawShelfUnit(canvas, 70f, -190f, 280f, 90f, 160f)
    }

    private fun drawShelfUnit(canvas: Canvas, ox: Float, oy: Float, sizeX: Float, sizeY: Float, heightZ: Float) {
        val colPillar = 0xFF1565C0.toInt()
        val colShelf = 0xFFE65100.toInt()
        val colBox = 0xFF8D6E63.toInt()

        drawIsoBox(canvas, ox, oy, 50f, sizeX, sizeY, 10f, colShelf)
        drawIsoBox(canvas, ox, oy, 105f, sizeX, sizeY, 10f, colShelf)
        drawIsoBox(canvas, ox, oy, 155f, sizeX, sizeY, 10f, colShelf)

        drawIsoBox(canvas, ox + 15f, oy + 20f, 60f, 45f, 45f, 40f, colBox)
        drawIsoBox(canvas, ox + 15f, oy + 85f, 60f, 45f, 45f, 40f, colBox)
        drawIsoBox(canvas, ox + 20f, oy + 35f, 115f, 40f, 40f, 35f, colBox)

        drawIsoBox(canvas, ox, oy, 0f, 12f, 12f, heightZ, colPillar)
        drawIsoBox(canvas, ox + sizeX - 12f, oy, 0f, 12f, 12f, heightZ, colPillar)
        drawIsoBox(canvas, ox, oy + sizeY - 12f, 0f, 12f, 12f, heightZ, colPillar)
        drawIsoBox(canvas, ox + sizeX - 12f, oy + sizeY - 12f, 0f, 12f, 12f, heightZ, colPillar)
    }

    private fun drawPallet(canvas: Canvas, pallet: Pallet) {
        if (pallet.z > 0f) {
            val sx = toIsoX(pallet.x + 35f, pallet.y + 30f)
            val sy = toIsoY(pallet.x + 35f, pallet.y + 30f, 0f)
            paint.color = 0x44000000.toInt()
            canvas.drawOval(sx - 45f, sy - 20f, sx + 45f, sy + 20f, paint)
        }

        // Toimitettu lava vaihtaa kuorman vihreäksi merkiksi
        val boxColor = if (pallet.isDelivered) 0xFF43A047.toInt() else 0xFFA17A4E.toInt()

        drawIsoBox(canvas, pallet.x, pallet.y, pallet.z, 70f, 55f, 12f, 0xFFC89D66.toInt())
        drawIsoBox(canvas, pallet.x + 8f, pallet.y + 8f, pallet.z + 12f, 54f, 40f, 45f, boxColor)
    }

    private fun drawLargeForkliftWithDriver(canvas: Canvas) {
        val rad = Math.toRadians(forkliftAngle.toDouble())
        val forwardX = -sin(rad).toFloat()
        val forwardY = cos(rad).toFloat()
        val rightX = cos(rad).toFloat()
        val rightY = sin(rad).toFloat()

        val baseX = toIsoX(forkliftX, forkliftY)
        val baseY = toIsoY(forkliftX, forkliftY)

        paint.color = 0x55000000.toInt()
        canvas.drawOval(baseX - 60f, baseY - 28f, baseX + 60f, baseY + 28f, paint)

        drawIsoBox(canvas, forkliftX - 35f, forkliftY - 35f, 0f, 70f, 70f, 45f, 0xFFFFB300.toInt())
        drawIsoBox(canvas, forkliftX - 32f, forkliftY + 22f, 10f, 64f, 12f, 35f, 0xFF37474F.toInt())
        drawIsoBox(canvas, forkliftX - 18f, forkliftY - 5f, 25f, 36f, 30f, 18f, 0xFF212121.toInt())

        // Kuski
        drawIsoBox(canvas, forkliftX - 12f, forkliftY, 43f, 24f, 20f, 26f, 0xFFFDD835.toInt())
        drawIsoBox(canvas, forkliftX - 16f, forkliftY + 2f, 43f, 6f, 16f, 20f, 0xFF1976D2.toInt())
        drawIsoBox(canvas, forkliftX + 10f, forkliftY + 2f, 43f, 6f, 16f, 20f, 0xFF1976D2.toInt())
        drawIsoBox(canvas, forkliftX - 8f, forkliftY + 4f, 69f, 16f, 14f, 14f, 0xFFFFE0B2.toInt())
        drawIsoBox(canvas, forkliftX - 9f, forkliftY + 3f, 79f, 18f, 16f, 9f, 0xFFFF5722.toInt())

        // Ohjaamon kehikko
        drawIsoBox(canvas, forkliftX - 30f, forkliftY - 28f, 45f, 7f, 7f, 55f, 0xFF1C1C1C.toInt())
        drawIsoBox(canvas, forkliftX + 23f, forkliftY - 28f, 45f, 7f, 7f, 55f, 0xFF1C1C1C.toInt())
        drawIsoBox(canvas, forkliftX - 30f, forkliftY + 23f, 45f, 7f, 7f, 55f, 0xFF1C1C1C.toInt())
        drawIsoBox(canvas, forkliftX + 23f, forkliftY + 23f, 45f, 7f, 7f, 55f, 0xFF1C1C1C.toInt())
        drawIsoBox(canvas, forkliftX - 32f, forkliftY - 30f, 100f, 64f, 60f, 8f, 0xFF263238.toInt())

        val mastX = forkliftX + forwardX * 42f
        val mastY = forkliftY + forwardY * 42f
        drawIsoBox(canvas, mastX - 22f, mastY - 5f, 0f, 44f, 10f, 95f, 0xFF455A64.toInt())

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
