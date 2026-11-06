package th.co.lotto

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 30f
            gravity = Gravity.CENTER
        }

        val status = TextView(this).apply {
            text = getString(R.string.foundation_status)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 0)
        }

        val privacy = TextView(this).apply {
            text = getString(R.string.local_data_notice)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
        }

        content.addView(title)
        content.addView(status)
        content.addView(privacy)
        setContentView(content)
    }
}
