package com.example.readertest

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var pendingContainer: LinearLayout
    private lateinit var savedContainer: LinearLayout
    private val pendingTasks = mutableListOf<ExtractedTask>()
    private var currentTab = 0
    private var lastBufferVersion = -1

    private val refreshRunner = object : Runnable {
        override fun run() {
            refreshPending()
            handler.postDelayed(this, 2000)
        }
    }
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pendingContainer = findViewById(R.id.pendingContainer)
        savedContainer = findViewById(R.id.savedContainer)

        findViewById<Button>(R.id.btnPending).setOnClickListener { switchTab(0) }
        findViewById<Button>(R.id.btnSaved).setOnClickListener { switchTab(1) }
        findViewById<Button>(R.id.openSettingsButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        switchTab(0)
        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunner)
        refreshSaved()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunner)
    }

    private fun switchTab(tab: Int) {
        currentTab = tab
        pendingContainer.visibility = if (tab == 0) android.view.View.VISIBLE else android.view.View.GONE
        savedContainer.visibility = if (tab == 1) android.view.View.VISIBLE else android.view.View.GONE
        if (tab == 0) refreshPending() else refreshSaved()
    }

    private fun refreshPending() {
        if (CaptureBuffer.version == lastBufferVersion) return
        lastBufferVersion = CaptureBuffer.version
        val buffers = CaptureBuffer.items
        val newTasks = mutableListOf<ExtractedTask>()
        val seen = mutableSetOf<String>()
        for (item in buffers) {
            if (item.packageName in listOf("com.android.systemui", "com.miui.home", "com.android.settings", "诊断", packageName)) continue
            val tasks = TaskExtractor.extract(item.text, item.packageName)
            for (t in tasks) {
                val key = t.content.take(30)
                if (key !in seen) { seen.add(key); newTasks.add(t) }
            }
        }
        pendingTasks.clear(); pendingTasks.addAll(newTasks)
        renderPending()
    }

    private fun renderPending() {
        pendingContainer.removeAllViews()
        if (pendingTasks.isEmpty()) {
            val tv = TextView(this).apply { text = "暂无待确认任务。\n去 QQ 聊会儿天，这里会自动出现。"; textSize = 14f }
            pendingContainer.addView(tv)
            return
        }
        for ((i, t) in pendingTasks.withIndex()) {
            val card = createTaskCard(t, isPending = true, index = i)
            pendingContainer.addView(card)
        }
    }

    private fun refreshSaved() {
        lifecycleScope.launch {
            val dao = AppDatabase.get(this@MainActivity).taskDao()
            val tasks = withContext(Dispatchers.IO) { dao.getAll() }
            savedContainer.removeAllViews()
            if (tasks.isEmpty()) {
                val tv = TextView(this@MainActivity).apply { text = "暂无已保存待办"; textSize = 14f }
                savedContainer.addView(tv)
                return@launch
            }
            for (t in tasks) {
                val card = createSavedCard(t)
                savedContainer.addView(card)
            }
        }
    }

    private fun createTaskCard(t: ExtractedTask, isPending: Boolean, index: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFFF5F5F5.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }

            addView(TextView(this@MainActivity).apply {
                text = t.content; textSize = 16f; setTextColor(0xFF000000.toInt())
            })
            addView(TextView(this@MainActivity).apply {
                text = "⏰ ${TaskExtractor.formatDeadline(t.deadline)}  |  来源: ${CaptureBuffer.appName(t.sourcePackage)}  |  置信度: ${(t.confidence*100).toInt()}%"
                textSize = 12f; setTextColor(0xFF666666.toInt())
            })

            val btnRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            btnRow.addView(Button(this@MainActivity).apply {
                text = "✅ 确认"
                setOnClickListener { confirmTask(index) }
            })
            btnRow.addView(Button(this@MainActivity).apply {
                text = "❌ 忽略"
                setOnClickListener { pendingTasks.removeAt(index); renderPending() }
            })
            addView(btnRow)
        }
    }

    private fun createSavedCard(t: TaskEntity): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(if (t.completed) 0xFFE0E0E0.toInt() else 0xFFF5F5F5.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }

            addView(TextView(this@MainActivity).apply {
                text = t.content + if (t.completed) " ✓" else ""
                textSize = 16f; setTextColor(if (t.completed) 0xFF999999.toInt() else 0xFF000000.toInt())
            })
            addView(TextView(this@MainActivity).apply {
                text = "⏰ ${TaskExtractor.formatDeadline(t.deadline)}"
                textSize = 12f; setTextColor(0xFF666666.toInt())
            })

            val btnRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            btnRow.addView(Button(this@MainActivity).apply {
                text = if (t.completed) "↩️ 撤销" else "✅ 完成"
                setOnClickListener {
                    lifecycleScope.launch {
                        val dao = AppDatabase.get(this@MainActivity).taskDao()
                        withContext(Dispatchers.IO) { dao.setCompleted(t.id, !t.completed) }
                        refreshSaved()
                    }
                }
            })
            btnRow.addView(Button(this@MainActivity).apply {
                text = "🗑 删除"
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity).setTitle("确认删除？")
                        .setPositiveButton("删除") { _, _ ->
                            lifecycleScope.launch {
                                val dao = AppDatabase.get(this@MainActivity).taskDao()
                                withContext(Dispatchers.IO) {
                                    ReminderScheduler.cancel(this@MainActivity, t.id)
                                    dao.delete(t.id)
                                }
                                refreshSaved()
                            }
                        }.setNegativeButton("取消", null).show()
                }
            })
            addView(btnRow)
        }
    }

    private fun confirmTask(index: Int) {
        val t = pendingTasks[index]
        lifecycleScope.launch {
            val dao = AppDatabase.get(this@MainActivity).taskDao()
            val entity = TaskEntity(
                content = t.content, deadline = t.deadline,
                sourcePackage = t.sourcePackage, sourceText = t.sourceText
            )
            val id = withContext(Dispatchers.IO) { dao.insert(entity) }
            if (t.deadline != null && t.deadline > System.currentTimeMillis()) {
                ReminderScheduler.schedule(this@MainActivity, id, t.deadline)
            }
            pendingTasks.removeAt(index)
            renderPending()
            refreshSaved()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }
}