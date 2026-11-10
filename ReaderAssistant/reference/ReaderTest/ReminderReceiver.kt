package com.example.readertest

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.example.readertest.REMINDER") return
        val taskId = intent.getLongExtra("task_id", -1L)
        if (taskId < 0) return

        val db = AppDatabase.get(context).taskDao()
        val pendingResult = goAsync()
        android.util.Log.i("ReaderTest", "收到提醒广播 taskId=$taskId")
        CoroutineScope(Dispatchers.IO).launch {
            try {
            val task = db.getById(taskId) ?: return@launch
            if (task.completed || task.reminderFired) return@launch

            createChannel(context)
            db.setReminderFired(taskId)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else PendingIntent.FLAG_UPDATE_CURRENT
            val pi = PendingIntent.getActivity(context, taskId.toInt(), openIntent, flags)

            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("📋 待办提醒")
                .setContentText(task.content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(task.content))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()

            withContext(Dispatchers.Main) {
                try { NotificationManagerCompat.from(context).notify(taskId.toInt(), notif) } catch (_: Exception) {}
            }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, "待办提醒", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(channel)
        }
    }

    companion object { const val CHANNEL_ID = "task_reminders" }
}