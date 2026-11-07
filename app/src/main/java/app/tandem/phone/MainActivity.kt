package app.tandem.phone

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.concurrent.thread

/** One screen: connect to Tandem, allow usage access, see today, send now. */
class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var col: LinearLayout
    private val dark get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val ink get() = if (dark) Color.parseColor("#ECECF3") else Color.parseColor("#14141F")
    private val mute get() = if (dark) Color.parseColor("#9A9AB0") else Color.parseColor("#6B6B80")
    private val card get() = if (dark) Color.parseColor("#1E1E28") else Color.WHITE
    private val accent = Color.parseColor("#E21E79")
    private val good = Color.parseColor("#1F9D55")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        val scroll = ScrollView(this)
        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(28)) }
        scroll.addView(col)
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom); insets
        }
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handle(intent) }
    override fun onResume() { super.onResume(); render() }

    private fun handle(i: Intent?) {
        val data = i?.data ?: return
        if (data.scheme == "tandemphone") {
            if (store.connect(data.toString())) { Sync.schedule(this); toast("Connected to Tandem"); sendNow() }
            else toast("That link didn't work. Get a fresh one in Tandem › Settings.")
        }
    }

    private fun render() {
        col.removeAllViews()
        col.addView(text("Tandem Phone", 26f, bold = true))
        col.addView(text("Sends your daily screen time to Tandem. Nothing else leaves this phone.", 14f, color = mute).also { it.setPadding(0, dp(2), 0, dp(14)) })

        // 1 · Tandem
        section("Tandem").apply {
            if (store.connected) {
                addView(status("Connected" + (store.name?.let { " to $it's Tandem" } ?: ""), true))
                addView(button("Disconnect", primary = false) {
                    Sync.cancel(this@MainActivity); store.clear(); render(); toast("Disconnected")
                })
            } else {
                addView(text("In Tandem, open Settings › Connected apps › Phone screen time › Set up, then tap Open Tandem Phone. Or copy the connect code there and paste it here.", 14f, color = mute))
                addView(button("Paste connect code") { pasteCode() })
            }
        }

        // 2 · Usage access
        val ok = Usage.granted(this)
        section("Usage access").apply {
            if (ok) addView(status("Allowed", true))
            else {
                addView(text("Android's switch that lets this app read screen time. Find Tandem Phone in the list and turn it on.", 14f, color = mute))
                addView(button("Allow usage access") {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
            }
        }

        // 3 · Today
        if (ok) section("Today").apply {
            val (ms, apps) = runCatching { Usage.forDay(this@MainActivity, 0) }.getOrDefault(0L to emptyList())
            addView(text(Usage.hm(ms), 30f, bold = true))
            apps.take(6).forEach { a ->
                addView(row(a.label, Usage.hm(a.ms)))
            }
        }

        // 4 · Sending
        if (store.connected) section("Sending").apply {
            val last = store.lastSent
            addView(text(if (last > 0) "Last sent " + DateUtils.getRelativeTimeSpanString(last, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS) else "Not sent yet", 15f))
            store.lastMsg?.takeIf { it != Sync.OK }?.let { addView(text(it, 13f, color = accent)) }
            addView(text("Every hour, today's and yesterday's totals.", 13f, color = mute))
            addView(button("Send now", primary = false) { sendNow() })
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                addView(text("Some phones stop background apps to save battery. If sending goes quiet, let this one run.", 13f, color = mute).also { it.setPadding(0, dp(10), 0, 0) })
                addView(button("Keep it running in the background", primary = false) {
                    runCatching {
                        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                    }.onFailure { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                })
            }
        }
    }

    private fun pasteCode() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (t.isBlank()) { toast("Copy the connect code in Tandem first"); return }
        if (store.connect(t)) { Sync.schedule(this); toast("Connected to Tandem"); sendNow() }
        else toast("That isn't a Tandem connect code")
    }

    private fun sendNow() {
        if (!Usage.granted(this)) { render(); toast("Allow usage access first"); return }
        toast("Sending…")
        thread {
            val r = runCatching { Sync.run(this) }.getOrElse { "Couldn't reach Tandem" }
            runOnUiThread { toast(if (r == Sync.OK) "Sent ✓" else r); render() }
        }
    }

    // ---- little view helpers ----
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun text(s: String, size: Float, bold: Boolean = false, color: Int = ink) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(0f, 1.15f)
    }
    private fun status(s: String, on: Boolean) = text((if (on) "✓  " else "") + s, 16f, bold = true, color = if (on) good else ink)
    private fun row(l: String, r: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0)
        addView(text(l, 14f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(text(r, 14f, color = mute))
    }
    private fun section(title: String): LinearLayout {
        col.addView(text(title.uppercase(), 12f, bold = true, color = mute).also { it.setPadding(dp(4), dp(14), 0, dp(6)); it.letterSpacing = 0.06f })
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply { setColor(card); cornerRadius = dp(16).toFloat() }
        }
        col.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return box
    }
    private fun button(label: String, primary: Boolean = true, onTap: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f; stateListAnimator = null
        setTextColor(if (primary) Color.WHITE else ink)
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            if (primary) setColor(accent) else { setColor(Color.TRANSPARENT); setStroke(dp(1), mute) }
        }
        gravity = Gravity.CENTER; minHeight = dp(46)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
        setOnClickListener { onTap() }
        textAlignment = View.TEXT_ALIGNMENT_CENTER
    }
}
