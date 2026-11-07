package app.tandem.phone

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Home-screen button: one tap opens the quick log. */
class LogWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, QuickLogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        ids.forEach { id ->
            mgr.updateAppWidget(id, RemoteViews(ctx.packageName, R.layout.widget_log).apply { setOnClickPendingIntent(R.id.w_root, pi) })
        }
    }
}
