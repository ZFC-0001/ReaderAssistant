package com.example.readertest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val db = AppDatabase.get(context).taskDao()
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (task in db.getPendingReminders()) {
                    task.deadline?.let { ReminderScheduler.schedule(context, task.id, it) }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}