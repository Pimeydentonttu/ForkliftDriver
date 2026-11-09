package fi.forkliftdriver

import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var gameView: ForkliftGameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gameView = ForkliftGameView(this)
        setContentView(gameView)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                gameView.adjustForks(5f)
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                gameView.adjustForks(-5f)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}
