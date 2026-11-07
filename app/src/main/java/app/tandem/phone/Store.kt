package app.tandem.phone

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONObject

/** Everything the app remembers: where Tandem is, its public key, this phone's private key, and the last sync. */
class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("tandem", Context.MODE_PRIVATE)

    var url: String? get() = p.getString("u", null); set(v) = p.edit().putString("u", v).apply()
    var apiKey: String? get() = p.getString("a", null); set(v) = p.edit().putString("a", v).apply()
    var key: String? get() = p.getString("k", null); set(v) = p.edit().putString("k", v).apply()
    var name: String? get() = p.getString("n", null); set(v) = p.edit().putString("n", v).apply()
    var lastSent: Long get() = p.getLong("t", 0L); set(v) = p.edit().putLong("t", v).apply()
    var lastMinutes: Int get() = p.getInt("m", -1); set(v) = p.edit().putInt("m", v).apply()
    var backDays: Int get() = p.getInt("bd", 0); set(v) = p.edit().putInt("bd", v).apply()
    var lastMsg: String? get() = p.getString("msg", null); set(v) = p.edit().putString("msg", v).apply()

    val connected: Boolean get() = !url.isNullOrBlank() && !apiKey.isNullOrBlank() && !key.isNullOrBlank()

    fun clear() = p.edit().clear().apply()

    /** Accepts the link from Tandem (tandemphone://connect?c=…) or the bare code. Returns false if it isn't one. */
    fun connect(code: String): Boolean {
        val t = code.trim()
        val c = runCatching { Uri.parse(t).getQueryParameter("c") }.getOrNull()
            ?: t.substringAfter("c=", "").ifEmpty { t }
        return runCatching {
            val o = JSONObject(String(Base64.decode(c, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)))
            val u = o.getString("u"); val a = o.getString("a"); val k = o.getString("k")
            require(u.startsWith("https://") && k.startsWith("tdm_") && a.isNotBlank())
            url = u.trimEnd('/'); apiKey = a; key = k; name = null; lastSent = 0L; lastMsg = null; lastMinutes = -1
        }.isSuccess
    }
}
