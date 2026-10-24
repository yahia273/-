package com.example.data.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.AppNotification

object LocalNotificationHelper {
  private const val ORDERS_CHANNEL_ID = "village_orders_live_channel"
  private const val ALERTS_CHANNEL_ID = "village_evasion_alerts_channel"
  private val dispatchedIds = LinkedHashSet<String>()

  fun ensureChannels(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val ordersChannel =
        NotificationChannel(
            ORDERS_CHANNEL_ID,
            "الطلبات الحية وتحديثاتها",
            NotificationManager.IMPORTANCE_HIGH,
          )
          .apply {
            description = "إشعارات فورية بوصول طلبات العروض الجديدة وتحديث حالتها"
            enableVibration(true)
          }

      val alertsChannel =
        NotificationChannel(
            ALERTS_CHANNEL_ID,
            "تنبيهات رقابة الإدارة والإلغاءات",
            NotificationManager.IMPORTANCE_HIGH,
          )
          .apply {
            description = "تنبيهات فورية عند تجاوز المحل للحد اليومي للإلغاءات"
            enableVibration(true)
          }

      manager.createNotificationChannel(ordersChannel)
      manager.createNotificationChannel(alertsChannel)
    }
  }

  fun notifyIfNew(context: Context, notifications: List<AppNotification>) {
    ensureChannels(context)
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED
    ) {
      return
    }

    val unreadRecent = notifications.filter { !it.isRead }.take(3)
    for (notif in unreadRecent) {
      if (notif.id.isNotBlank() && dispatchedIds.add(notif.id)) {
        val isSuspiciousAlert = notif.type == "SUSPICIOUS_CANCELLATIONS"
        val channelId = if (isSuspiciousAlert) ALERTS_CHANNEL_ID else ORDERS_CHANNEL_ID
        val builder =
          NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(notif.title)
            .setContentText(notif.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notif.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        runCatching {
          NotificationManagerCompat.from(context).notify(notif.id.hashCode(), builder.build())
        }
      }
    }
  }
}
