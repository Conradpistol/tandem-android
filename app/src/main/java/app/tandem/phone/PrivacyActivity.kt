package app.tandem.phone

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

/** Health Connect asks every app to explain what it does with health data; this is that page. */
class PrivacyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        setContentView(ScrollView(this).apply {
            addView(TextView(this@PrivacyActivity).apply {
                setPadding(pad, pad * 2, pad, pad); textSize = 15f
                text = "Tandem Phone reads your daily steps, sleep and resting heart rate from Health Connect, only if you allow each one.\n\n" +
                    "Once an hour it sends daily totals to your own Tandem account, the same way it sends screen time. Nothing is shared with anyone else, sold, or used for ads. " +
                    "You can stop it any time in Health Connect › App permissions › Tandem Phone, or by disconnecting the app in Tandem."
            })
        })
    }
}
