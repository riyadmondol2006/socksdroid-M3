package com.riyadm.socksdroid.vpn

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.riyadm.socksdroid.R
import com.riyadm.socksdroid.data.Profile
import com.riyadm.socksdroid.ui.MainActivity

/** Builds the ongoing foreground-service notification shown while the VPN is up. */
internal class VpnNotifications(private val context: Context) {

    fun createChannel() {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.vpn_channel_name))
                .setDescription(context.getString(R.string.vpn_channel_description))
                .setShowBadge(false)
                .setSound(null, null)
                .setVibrationEnabled(false)
                .build(),
        )
    }

    /** [connectedSince] is an elapsed-realtime timestamp, or null while connecting. */
    fun build(profile: Profile, connectedSince: Long?): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_vpn)
            .setContentTitle(profile.name)
            .setContentIntent(openAppIntent(context))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, context.getString(R.string.vpn_action_disconnect), disconnectIntent())
        if (connectedSince == null) {
            builder.setContentText(context.getString(R.string.vpn_notification_connecting, profile.endpoint))
                .setShowWhen(false)
        } else {
            val wallClock = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - connectedSince)
            builder.setContentText(profile.endpoint)
                .setWhen(wallClock)
                .setShowWhen(true)
                .setUsesChronometer(true)
        }
        return builder.build()
    }

    private fun disconnectIntent(): PendingIntent = PendingIntent.getService(
        context,
        REQUEST_DISCONNECT,
        SocksVpnService.intent(context, SocksVpnService.ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "vpn_status"

        private const val REQUEST_OPEN_APP = 0
        private const val REQUEST_DISCONNECT = 1

        fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
