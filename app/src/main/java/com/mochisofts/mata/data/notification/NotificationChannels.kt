package com.mochisofts.mata.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.mochisofts.mata.R

object NotificationChannels {
    const val CHANNEL_ID = "todo_reminders_v1"

    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_description)
            },
        )
    }
}
