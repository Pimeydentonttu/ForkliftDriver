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

    // Trukin sijainti ja liike
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
                if (event.y > height * 0.4f) {
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

        // Varaston harmaa lattia
        canvas.drawColor(0xFF202225.toInt())

        // Kosketusohjauksen alueen raja / lattiamerkintä
        paint.color = 0x33FFFFFF.toInt()
        paint.strokeWidth = 4f
        canvas.drawLine(0f, height * 0.4f, width.toFloat(), height.4f, paint)

        canvas.save()
        canvas.translate(forkliftX, forkliftY)
        canvas.rotate(forkliftAngle)

        // --- 1. RENKAAT ---
        paint.color = 0xFF111111.toInt()
        // Takarenkaat
        canvas.drawRect(-36f, 35f, -20f, 65f, paint)
        canvas.drawRect(20f, 35f, 36f, 65f, paint)
        // Eturenkaat (suuremmat vetopyörät)
        canvas.drawRect(-42f, -35f, -22f, 5f, paint)
        canvas.drawRect(22f, -35f, 42f, 5f, paint)

        // --- 2. TRUKIN RUNKO (Kullanruskea / keltainen) ---
        paint.color = 0xFFFFB300.toInt() // Premium kulta/keltainen
        canvas.drawRect(-32f, -30f, 32f, 55f, paint)

        // Vastapaino takana
        paint.color = 0xFF424242.toInt()
        canvas.drawRect(-30f, 45f, 30f, 60f, paint)

        // --- 3. OHJAAMO JA PENKKI ---
        paint.color = 0xFF212121.toInt()
        canvas.drawRect(-20f, -5f, 20f, 35f, paint) // Istuin ja kojelauta

        // --- 4. MUSTA SUOJAKEHIKKO (Overhead Guard) ---
        paint.color = 0xFF1C1C1C.toInt()
        // Pilarit ja katto
        canvas.drawRect(-32f, -32f, -26f, -26f, paint) // Vasen etupylväs
        canvas.drawRect(26f, -32f, 32f, -26f, paint)  // Oikea etupylväs
        canvas.drawRect(-32f, 30f, -26f, 36f, paint)  // Vasen takapylväs
        canvas.drawRect(26f, 30f, 32f, 36f, paint)   // Oikea takapylväs
        // Kehikon yläkatto
        canvas.drawRect(-32f, -32f, 32f, -28f, paint)

        // --- 5. MASTO JA HAARUKAT (Edessä) ---
        // Maston pystypalkit
        paint.color = 0xFF2C2C2C.toInt()
        canvas.drawRect(-18f, -75f, -10f, -30f, paint)
        canvas.drawRect(10f, -75f, 18f, -30f, paint)

        // Nostokelkka ja haarukat (liikkuvat korkeuden mukaan)
        val forkOffset = forkHeight * 0.4f
        paint.color = 0xFF3E3E3E.toInt()
        // Itse piikit (haarukat)
        canvas.drawRect(-24f, -100f - forkOffset, -14f, -70f - forkOffset, paint)
        canvas.drawRect(14f, -100f - forkOffset, 24f, -70f - forkOffset, paint)

        canvas.restore()
    }
}
