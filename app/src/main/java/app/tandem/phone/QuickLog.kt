package app.tandem.phone

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.concurrent.thread

/** Log a bullet to today's Tandem journal: from the home-screen widget, the app, or "Share → Tandem" in any app. */
class QuickLogActivity : Activity() {
    private val kinds = listOf("note" to "– Note", "event" to "○ Event", "feel" to "♥ Feeling", "cond" to "~ Condition", "task" to "• Task")
    private var kind = "note"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(this)
        if (!store.connected) { toast("Connect Tandem Phone to Tandem first"); startActivity(Intent(this, MainActivity::class.java)); finish(); return }
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val ink = if (dark) Color.parseColor("#ECECF3") else Color.parseColor("#14141F")
        val mute = if (dark) Color.parseColor("#9A9AB0") else Color.parseColor("#6B6B80")
        val accent = Color.parseColor("#E21E79")
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
        val shared = intent?.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(18), dp(20), dp(16))
            background = GradientDrawable().apply { setColor(if (dark) Color.parseColor("#1E1E28") else Color.WHITE); cornerRadius = dp(20).toFloat() }
        }
        col.addView(TextView(this).apply { text = "Log to today’s journal"; textSize = 18f; setTextColor(ink); paint.isFakeBoldText = true })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, dp(8)) }
        val chips = mutableListOf<TextView>()
        fun paint() = chips.forEachIndexed { i, c ->
            val on = kinds[i].first == kind
            c.setTextColor(if (on) Color.WHITE else ink)
            c.background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); if (on) setColor(accent) else setStroke(dp(1), mute) }
        }
        kinds.forEach { (k, label) ->
            val c = TextView(this).apply {
                text = label; textSize = 12.5f; setPadding(dp(9), dp(5), dp(9), dp(5)); gravity = Gravity.CENTER
                setOnClickListener { kind = k; paint() }
            }
            chips += c
            row.addView(c, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) })
        }
        paint()
        col.addView(android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) })
        val box = EditText(this).apply {
            setText(shared); hint = "What happened? #tags work"; setTextColor(ink); setHintTextColor(mute); textSize = 16f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2; maxLines = 6
        }
        col.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val save = Button(this).apply {
            text = "Log it"; isAllCaps = false; setTextColor(Color.WHITE); stateListAnimator = null
            background = GradientDrawable().apply { setColor(accent); cornerRadius = dp(12).toFloat() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(12) }
        }
        save.setOnClickListener {
            val t = box.text.toString().trim()
            if (t.isEmpty()) { box.requestFocus(); return@setOnClickListener }
            save.isEnabled = false; save.text = "Logging…"
            thread {
                val r = runCatching { send(store, t) }.getOrElse { "Couldn’t reach Tandem" }
                runOnUiThread { toast(if (r == "ok") "Logged ✓" else r); if (r == "ok") finish() else { save.isEnabled = true; save.text = "Log it" } }
            }
        }
        col.addView(save)
        setContentView(col)
        window.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        box.requestFocus()
    }

    private fun send(s: Store, text: String): String {
        val now = System.currentTimeMillis()
        val body = JSONObject().put("k", s.key).put("p_text", text.take(500)).put("p_kind", kind)
            .put("p_date", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now))
            .put("p_at", SimpleDateFormat("HH:mm", Locale.US).format(now)).toString()
        val c = URL(s.url + "/rest/v1/rpc/phone_log").openConnection() as HttpURLConnection
        return try {
            c.requestMethod = "POST"; c.connectTimeout = 15_000; c.readTimeout = 20_000; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("apikey", s.apiKey)
            if (s.apiKey?.startsWith("sb_") == false) c.setRequestProperty("Authorization", "Bearer " + s.apiKey)
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            if (code in 200..299) "ok" else if (code == 404) "Tandem needs an update for quick log" else "Tandem said $code"
        } finally { c.disconnect() }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
