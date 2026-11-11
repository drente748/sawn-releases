package com.sawn.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sawn.app.MainActivity
import com.sawn.app.R
import com.sawn.app.sawnContainer
import com.sawn.app.ui.SawnNumbers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ويدجت 2×2 (RemoteViews كلاسيكي — لا حاجة لـ Glance/Space جديد):
 * عدّاد أيام محمي + حالة (محمي/متوقف) + زر يفتح التطبيق.
 *
 * يُحدَّث عند تغيّر الحالة عبر [requestUpdate] — انظر BootReceiver وGuardHealthWorker
 * وMainActivity. بلا ذلك يبقى الودجت يعرض الحالة القديمة إلى أن يطلب المشغّل
 * تحديثاً، فلا معنى لوجوده.
 */
class SawnWidgetProvider : AppWidgetProvider() {

    /**
     * `goAsync()` يبقي المُرسِل حيّاً ريثما تُقرأ السلسلة من قاعدة البيانات.
     * بدونه قد يقتل أندرويد العملية قبل انتهاء القراءة، فيبقى الودجت على حالته
     * القديمة — وهو ما كان يحدث: لم يكن هناك أي استدعاء للتحديث أصلاً.
     */
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (id in ids) {
                    updateOne(context, manager, id)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun updateOne(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val container = context.sawnContainer
        val active = runCatching { container.blockEngine.isBlockingActiveNow() }.getOrDefault(false)
        // السلسلة من سجل `protected_days` نفسه — لا رقم آخر ولا قيمة مبدوءة.
        val streak = runCatching { container.stats.currentStreak().first() }.getOrDefault(0)

        val views = RemoteViews(context.packageName, R.layout.sawn_widget)
        views.setTextViewText(
            R.id.widget_streak,
            if (streak > 0) {
                context.getString(R.string.widget_streak_format, SawnNumbers.format(streak.toLong()))
            } else {
                context.getString(R.string.app_name)
            },
        )
        views.setTextViewText(
            R.id.widget_status,
            if (active) context.getString(R.string.you_are_protected)
            else context.getString(R.string.protection_off),
        )
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pi = android.app.PendingIntent.getActivity(
            context, widgetId, open,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, pi)
        views.setOnClickPendingIntent(R.id.widget_open, pi)
        manager.updateAppWidget(widgetId, views)
    }

    companion object {
        /**
         * اطلب من المشغّل تحديث كل ودجتات التطبيق.
         *
         * يُستدعى كلما تغيّرت حالة الحماية — لا عند تغيير السلسلة فقط، فالسلسلة
         * تُقرأ من نفس السجل في كل تحديث.
         */
        fun requestUpdate(context: Context) {
            val ids = runCatching {
                AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(ComponentName(context, SawnWidgetProvider::class.java))
            }.getOrDefault(IntArray(0))
            if (ids.isEmpty()) return
            val intent = Intent(context, SawnWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            runCatching { context.sendBroadcast(intent) }
        }
    }
}
