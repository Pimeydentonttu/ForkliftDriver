package fi.forkliftdriver

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
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

    enum class GameState { MENU, PLAYING, VICTORY }
    private var gameState = GameState.MENU

    private var startTimeMs = 0L
    private var completionTimeSec = 0f
    private var deliveredCount = 0
    private val totalPallets = 2

    // Trukin tila
    private var forkliftX = 260f
    private var forkliftY = 320f
    private var forkliftAngle = 0f
    private var currentSpeed = 0f
    var forkHeight = 0f
        private set

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

    private val colliders = listOf(
        RectF(-180f, 40f, -80f, 360f),
        RectF(60f, -200f, 380f, -100f),
        RectF(400f, 100f, 560f, 500f)
    )

    // Touch-ohjaus
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchCurrentX = 0f
    private var touchCurrentY = 0f
    private var isTouching = false
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
        currentSpeed = 0f
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
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTouching = false
                currentSpeed = 0f
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updatePhysics() {
        if (isTouching) {
            val deltaX = touchCurrentX - touchStartX
            val deltaY = touchCurrentY - touchStartY
            val distance = hypot(deltaX, deltaY)

            if (distance > 15f) {
                val screenAngle = Math.toDegrees(atan2(deltaY.toDouble(), deltaX.toDouble())).toFloat()
                val targetWorldAngle = screenAngle + 135f

                var angleDiff = (targetWorldAngle - forkliftAngle) % 360f
                if (angleDiff > 180f) angleDiff -= 360f
                if (angleDiff < -180f) angleDiff += 360f

                forkliftAngle += angleDiff * 0.15f

                val targetSpeed = (distance / 70f).coerceIn(0.5f, 4.5f)
                currentSpeed = currentSpeed * 0.8f + targetSpeed * 0.2f

                val rad = Math.toRadians(forkliftAngle.toDouble())
                val nextX = forkliftX - (currentSpeed * sin(rad)).toFloat()
                val nextY = forkliftY + (currentSpeed * cos(rad)).toFloat()

                var collides = false
                for (box in colliders) {
                    if (nextX + 38f > box.left && nextX - 38f < box.right &&
                        nextY + 38f > box.top && nextY - 38f < box.bottom
                    ) {
                        collides = true
                        break
                    }
                }

                if (!collides) {
                    forkliftX = nextX
                    forkliftY = nextY
                }
            } else {
                currentSpeed *= 0.5f
            }
        } else {
            currentSpeed *= 0.5f
        }

        val rad = Math.toRadians(forkliftAngle.toDouble())
        val forwardX = -sin(rad).toFloat()
        val forwardY = cos(rad).toFloat()
        val forkTipX = forkliftX + forwardX * 60f
        val forkTipY = forkliftY + forwardY * 60f

        for (pallet in pallets) {
            if (pallet.isDelivered) continue

            val dist = hypot(forkTipX - (pallet.x + 35f), forkTipY - (pallet.y + 30f))

            if (!pallet.isLoaded) {
                val anyLoaded = pallets.any { it.isLoaded }
                if (!anyLoaded && dist < 45f && forkHeight > 5f) {
                    pallet.isLoaded = true
                }
            } else {
                pallet.x = forkTipX - 35f
                pallet.y = forkTipY - 30f
                pallet.z = forkHeight * 0.5f

                if (forkHeight <= 2f) {
                    pallet.isLoaded = false
                    pallet.z = 0f

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

        // Varaston tausta
        canvas.drawColor(0xFF14171A.toInt())

        drawWarehouseWalls(canvas)
        drawWarehouseFloor(canvas)
        drawHeadlightBeam(canvas)
        drawLoadingDockAndTruck(canvas)
        drawWarehouseShelves(canvas)

        for (pallet in pallets) {
            drawPallet(canvas, pallet)
        }

        drawDetailedForklift(canvas)
        drawHUD(canvas)

        if (gameState == GameState.MENU) {
            drawMenuOverlay(canvas, "TRUKKIKUSKI", "Aloita työvuoro napauttamalla", "Tehtävä: Lastaa 2 lavaa rekan kyytiin")
        } else if (gameState == GameState.VICTORY) {
            val timeText = "Aika: ${"%.1f".format(completionTimeSec)} sekuntia"
            drawMenuOverlay(canvas, "LASTAUS VALMIS!", timeText, "Napauta pelataksesi uudelleen")
        }
    }

    private fun drawWarehouseWalls(canvas: Canvas) {
        // Varaston takaseinä (peltipaneelilinjat)
        val p1x = toIsoX(-200f, -220f)
        val p1y = toIsoY(-200f, -220f, 0f)
        val p2x = toIsoX(450f, -220f)
        val p2y = toIsoY(450f, -220f, 0f)
        val p2yTop = toIsoY(450f, -220f, 220f)
        val p1yTop = toIsoY(-200f, -220f, 220f)

        paint.color = 0xFF1C2026.toInt()
        path.reset()
        path.moveTo(p1x, p1y)
        path.lineTo(p2x, p2y)
        path.lineTo(p2x, p2yTop)
        path.lineTo(p1x, p1yTop)
        path.close()
        canvas.drawPath(path, paint)

        // Seinän metalliraidoitus
        paint.color = 0x11FFFFFF.toInt()
        paint.strokeWidth = 2f
        var z = 20f
        while (z < 220f) {
            val sy1 = toIsoY(-200f, -220f, z)
            val sy2 = toIsoY(450f, -220f, z)
            canvas.drawLine(p1x, sy1, p2x, sy2, paint)
            z += 25f
        }
    }

    private fun drawWarehouseFloor(canvas: Canvas) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        paint.color = 0x1EFFFFFF.toInt()

        val step = 70f
        for (i in -4..12) {
            canvas.drawLine(toIsoX(i * step, -4 * step), toIsoY(i * step, -4 * step), toIsoX(i * step, 12 * step), toIsoY(i * step, 12 * step), paint)
        }
        for (j in -4..12) {
            canvas.drawLine(toIsoX(-4 * step, j * step), toIsoY(-4 * step, j * step), toIsoX(12 * step, j * step), toIsoY(12 * step, j * step), paint)
        }

        // Trukkikaistan kelta-mustat huomiomerkinnät
        paint.color = 0x66FFC107.toInt()
        paint.strokeWidth = 6f
        canvas.drawLine(toIsoX(-60f, -40f), toIsoY(-60f, -40f), toIsoX(-60f, 450f), toIsoY(-60f, 450f), paint)
        canvas.drawLine(toIsoX(380f, -40f), toIsoY(380f, -40f), toIsoX(380f, 450f), toIsoY(380f, 450f), paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawHeadlightBeam(canvas: Canvas) {
        // Trukin ajovalojen keila lattialla
        val rad = Math.toRadians(forkliftAngle.toDouble())
        val forwardX = -sin(rad).toFloat()
        val forwardY = cos(rad).toFloat()
        val rightX = cos(rad).toFloat()
        val rightY = sin(rad).toFloat()

        val mastX = forkliftX + forwardX * 45f
        val mastY = forkliftY + forwardY * 45f

        val startX = toIsoX(mastX, mastY)
        val startY = toIsoY(mastX, mastY, 30f)

        val tipLeftX = mastX + forwardX * 180f - rightX * 80f
        val tipLeftY = mastY + forwardY * 180f - rightY * 80f
        val tipRightX = mastX + forwardX * 180f + rightX * 80f
        val tipRightY = mastY + forwardY * 180f + rightY * 80f

        val endL1 = toIsoX(tipLeftX, tipLeftY)
        val endL2 = toIsoY(tipLeftX, tipLeftY, 0f)
        val endR1 = toIsoX(tipRightX, tipRightY)
        val endR2 = toIsoY(tipRightX, tipRightY, 0f)

        paint.color = 0x22FFF59D.toInt() // Lämmin valokeila
        path.reset()
        path.moveTo(startX, startY)
        path.lineTo(endL1, endL2)
        path.lineTo(endR1, endR2)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawLoadingDockAndTruck(canvas: Canvas) {
        // Laituri
        drawIsoBox(canvas, 420f, 80f, 0f, 40f, 350f, 30f, 0xFF37474F.toInt())
        // Huomioraidat rampissa
        drawIsoBox(canvas, 410f, 120f, 0f, 10f, 120f, 30f, 0xFFFFB300.toInt())

        // REKKA
        drawIsoBox(canvas, 460f, 80f, 0f, 120f, 320f, 110f, 0xFFCFD8DC.toInt()) // Vaalea runko
        drawIsoBox(canvas, 460f, 85f, 25f, 110f, 300f, 80f, 0xFF1E272C.toInt()) // Sisätila
        // Kromatut/mustat pyörät
        drawIsoBox(canvas, 470f, 340f, 0f, 20f, 40f, 25f, 0xFF111111.toInt())
        drawIsoBox(canvas, 550f, 340f, 0f, 20f, 40f, 25f, 0xFF111111.toInt())

        // Vihreä kohdealue
        paint.color = 0x334CAF50.toInt()
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
        val colPillar = 0xFF0D47A1.toInt()
        val colShelf = 0xFFEF6C00.toInt()
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

        val boxColor = if (pallet.isDelivered) 0xFF43A047.toInt() else 0xFFA17A4E.toInt()

        // Kuormalavan lautakerrokset ja jalkapalat
        drawIsoBox(canvas, pallet.x, pallet.y, pallet.z, 70f, 55f, 6f, 0xFFBCAAA4.toInt())
        drawIsoBox(canvas, pallet.x, pallet.y, pallet.z + 6f, 70f, 55f, 6f, 0xFFD7CCC8.toInt())
        // Pahvilaatikko
        drawIsoBox(canvas, pallet.x + 8f, pallet.y + 8f, pallet.z + 12f, 54f, 40f, 45f, boxColor)
    }

    private fun drawDetailedForklift(canvas: Canvas) {
        val rad = Math.toRadians(forkliftAngle.toDouble())
        val forwardX = -sin(rad).toFloat()
        val forwardY = cos(rad).toFloat()
        val rightX = cos(rad).toFloat()
        val rightY = sin(rad).toFloat()

        val baseX = toIsoX(forkliftX, forkliftY)
        val baseY = toIsoY(forkliftX, forkliftY)

        // Pehmeä varjo maassa
        paint.color = 0x55000000.toInt()
        canvas.drawOval(baseX - 65f, baseY - 30f, baseX + 65f, baseY + 30f, paint)

        // 1. ISOMETRISET PYÖRÄT JA KELTAISET VANTEET
        drawWheel(canvas, forkliftX - 32f, forkliftY - 24f, rightX, rightY)
        drawWheel(canvas, forkliftX + 24f, forkliftY - 24f, rightX, rightY)
        drawWheel(canvas, forkliftX - 32f, forkliftY + 22f, rightX, rightY)
        drawWheel(canvas, forkliftX + 24f, forkliftY + 22f, rightX, rightY)

        // 2. RUNKO JA VALAISTU KORIPINTA
        drawIsoBox(canvas, forkliftX - 35f, forkliftY - 35f, 6f, 70f, 70f, 40f, 0xFFFFB300.toInt())
        // Takavastapaino
        drawIsoBox(canvas, forkliftX - 34f, forkliftY + 18f, 10f, 68f, 17f, 36f, 0xFF263238.toInt())

        // 3. OHJAAMO & KULJETTAJA
        drawIsoBox(canvas, forkliftX - 18f, forkliftY - 5f, 25f, 36f, 30f, 18f, 0xFF1A1A1A.toInt()) // Penkki

        // Kuski (turvaliivit, haalarit, kypärä)
        drawIsoBox(canvas, forkliftX - 12f, forkliftY, 43f, 24f, 20f, 26f, 0xFFFDD835.toInt())
        drawIsoBox(canvas, forkliftX - 16f, forkliftY + 2f, 43f, 6f, 16f, 20f, 0xFF1565C0.toInt())
        drawIsoBox(canvas, forkliftX + 10f, forkliftY + 2f, 43f, 6f, 16f, 20f, 0xFF1565C0.toInt())
        drawIsoBox(canvas, forkliftX - 8f, forkliftY + 4f, 69f, 16f, 14f, 14f, 0xFFFFE0B2.toInt())
        drawIsoBox(canvas, forkliftX - 9f, forkliftY + 3f, 79f, 18f, 16f, 9f, 0xFFFF6D00.toInt())

        // 4. METALLINEN SUOJA-ROPS JA KATTO
        drawIsoBox(canvas, forkliftX - 30f, forkliftY - 28f, 45f, 7f, 7f, 55f, 0xFF212121.toInt())
        drawIsoBox(canvas, forkliftX + 23f, forkliftY - 28f, 45f, 7f, 7f, 55f, 0xFF212121.toInt())
        drawIsoBox(canvas, forkliftX - 30f, forkliftY + 23f, 45f, 7f, 7f, 55f, 0xFF212121.toInt())
        drawIsoBox(canvas, forkliftX + 23f, forkliftY + 23f, 45f, 7f, 7f, 55f, 0xFF212121.toInt())
        drawIsoBox(canvas, forkliftX - 32f, forkliftY - 30f, 100f, 64f, 60f, 8f, 0xFF263238.toInt())

        // 5. HYDRAULIMASTO JA TYÖVALOT
        val mastX = forkliftX + forwardX * 42f
        val mastY = forkliftY + forwardY * 42f
        drawIsoBox(canvas, mastX - 22f, mastY - 5f, 0f, 44f, 10f, 95f, 0xFF37474F.toInt())
        // Maston teräskisko keskellä
        drawIsoBox(canvas, mastX - 4f, mastY - 5f, 10f, 8f, 8f, 85f, 0xFFB0BEC5.toInt())

        // Kirkkaat LED-työvalot maston sivuilla
        drawIsoBox(canvas, mastX - 24f, mastY - 6f, 75f, 8f, 6f, 8f, 0xFFFFF9C4.toInt())
        drawIsoBox(canvas, mastX + 16f, mastY - 6f, 75f, 8f, 6f, 8f, 0xFFFFF9C4.toInt())

        // 6. KIILTÄVÄT TERÄSHAARUKAT
        val forkElevation = forkHeight * 0.5f
        val forkTipX = mastX + forwardX * 46f
        val forkTipY = mastY + forwardY * 46f

        paint.color = 0xFFECEFF1.toInt()
        val p1x = toIsoX(mastX - 16f * rightX, mastY - 16f * rightY)
        val p1y = toIsoY(mastX - 16f * rightX, mastY - 16f * rightY, forkElevation)
        val p2x = toIsoX(forkTipX - 16f * rightX, forkTipY - 16f * rightY)
        val p2y = toIsoY(forkTipX - 16f * rightX, forkTipY - 16f * rightY, forkElevation)

        val p3x = toIsoX(mastX + 16f * rightX, mastY + 16f * rightY)
        val p3y = toIsoY(mastX + 16f * rightX, mastY + 16f * rightY, forkElevation)
        val p4x = toIsoX(forkTipX + 16f * rightX, forkTipY + 16f * rightY)
        val p4y = toIsoY(forkTipX + 16f * rightX, forkTipY + 16f * rightY, forkElevation)

        paint.strokeWidth = 9f
        canvas.drawLine(p1x, p1y, p2x, p2y, paint)
        canvas.drawLine(p3x, p3y, p4x, p4y, paint)
    }

    private fun drawWheel(canvas: Canvas, wx: Float, wy: Float, rx: Float, ry: Float) {
        // Pyörän kumi
        drawIsoBox(canvas, wx, wy, 0f, 14f, 14f, 16f, 0xFF111111.toInt())
        // Keltainen vannekeskiö
        drawIsoBox(canvas, wx + 3f, wy + 3f, 4f, 8f, 8f, 8f, 0xFFFFCA28.toInt())
    }

    private fun drawHUD(canvas: Canvas) {
        paint.color = 0x25000000.toInt()
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, height * controlAreaRatio, width.toFloat(), height.toFloat(), paint)

        paint.color = 0x55FFA000.toInt()
        paint.strokeWidth = 3f
        canvas.drawLine(0f, height * controlAreaRatio, width.toFloat(), height * controlAreaRatio, paint)

        if (gameState == GameState.PLAYING) {
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

            if (isTouching) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f
                paint.color = 0x44FFFFFF.toInt()
                canvas.drawCircle(touchStartX, touchStartY, 70f, paint)

                paint.style = Paint.Style.FILL
                paint.color = 0xDDFFB300.toInt()
                canvas.drawCircle(touchCurrentX, touchCurrentY, 24f, paint)

                paint.color = 0x88FFB300.toInt()
                paint.strokeWidth = 5f
                canvas.drawLine(touchStartX, touchStartY, touchCurrentX, touchCurrentY, paint)
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
