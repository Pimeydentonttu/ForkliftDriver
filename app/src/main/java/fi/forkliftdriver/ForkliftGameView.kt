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
    private var forkliftY = 900f
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

        // Tumma varastolattia
        canvas.drawColor(0xFF1E2124.toInt())

        // Piirretään kevyttä ruudukkoa / lattialinjoja tuomaan syvyysvaikutelmaa
        paint.color = 0x15FFFFFF.toInt()
        paint.strokeWidth = 2f
        var step = 100f
        var x = 0f
        while (x < width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), paint)
            x += step
        }
        var y = 0f
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, paint)
            y += step
        }

        // Ohjausalueen raja
        paint.color = 0x33FFFFFF.toInt()
        canvas.drawLine(0f, height * 0.4f, width.toFloat(), height * 0.4f, paint)

        canvas.save()
        canvas.translate(forkliftX, forkliftY)
        canvas.rotate(forkliftAngle)

        // Simulaatio 3/4-perspektiivistä: venytetään hieman pystyakselilla, jotta saadaan syvyyttä
        canvas.scale(1.0f, 1.25f)

        // --- 1. RENKAAT ---
        paint.color = 0xFF111111.toInt()
        canvas.drawRect(-36f, 30f, -22f, 60f, paint) // Takavasen
        canvas.drawRect(22f, 30f, 36f, 60f, paint)  // Takaoikea
        canvas.drawRect(-42f, -30f, -22f, 5f, paint) // Etuvasen
        canvas.drawRect(22f, -30f, 42f, 5f, paint)  // Etuoikea

        // --- 2. TRUKIN RUNKO (Kulta/Keltainen teollisuusväri) ---
        paint.color = 0xFFFFB300.toInt()
        canvas.drawRect(-30f, -25f, 30f, 50f, paint)

        // Vastapaino takana (tummemman harmaa)
        paint.color = 0xFF37474F.toInt()
        canvas.drawRect(-28f, 38f, 28f, 52f, paint)

        // --- 3. OHJAAMO JA PENKKI ---
        paint.color = 0xFF263238.toInt()
        canvas.drawRect(-18f, -5f, 18f, 30f, paint)

        // --- 4. MUSTA SUOJAKEHIKKO (Overhead Guard) ---
        paint.color = 0xFF212121.toInt()
        // Pylväät
        canvas.drawRect(-30f, -28f, -25f, -23f, paint)
        canvas.drawRect(25f, -28f, 30f, -23f, paint)
        canvas.drawRect(-30f, 25f, -25f, 30f, paint)
        canvas.drawRect(25f, 25f, 30f, 30f, paint)
        // Katto
        canvas.drawRect(-30f, -28f, 30f, -24f, paint)

        // --- 5. MASTO JA HAARUKAT (Edessä) ---
        paint.color = 0xFF424242.toInt()
        canvas.drawRect(-16f, -65f, -10f, -25f, paint)
        canvas.drawRect(10f, -65f, 16f, -25f, paint)

        // Liikkuvat haarukat (korkeuden säätö volyyminapeilla)
        val forkOffset = forkHeight * 0.4f
        paint.color = 0xFF616161.toInt()
        canvas.drawRect(-22f, -85f - forkOffset, -13f, -60f - forkOffset, paint)
        canvas.drawRect(13f, -85f - forkOffset, 22f, -60f - forkOffset, paint)

        canvas.restore()
    }
}
