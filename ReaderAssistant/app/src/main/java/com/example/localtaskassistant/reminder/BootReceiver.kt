package com.example.localtaskassistant.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.localtaskassistant.bootstrap.AssistantApplication
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val container = (context.applicationContext as AssistantApplication).container
        container.scope.launch {
            try { container.outbox.drain() } finally { pending.finish() }
        }
    }
}
