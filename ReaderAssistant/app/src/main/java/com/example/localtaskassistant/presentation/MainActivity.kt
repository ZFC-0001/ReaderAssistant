package com.example.localtaskassistant.presentation

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import com.example.localtaskassistant.bootstrap.AssistantApplication
import com.example.localtaskassistant.presentation.screens.*
import kotlinx.coroutines.launch

/** 成员三入口。只装配页面和订阅状态；不得把模型/规则/数据库写回 Activity。 */
class MainActivity : AppCompatActivity() {
    private lateinit var model: AssistantViewModel
    private lateinit var content: LinearLayout
    private var page = "home"
    private var selectedTask: String? = null
    private var current = AssistantUiState()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this, AssistantViewModel.Factory(
            (application as AssistantApplication).container.facade))[AssistantViewModel::class.java]
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; fitsSystemWindows = true }
        val navigation = LinearLayout(this)
        listOf("首页" to "home", "待确认" to "pending", "待办" to "tasks", "设置" to "settings").forEach { (title, key) ->
            navigation.addView(Button(this).apply {
                text = title; setOnClickListener { page = key; selectedTask = null; render() }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(navigation)
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content) // ReaderTest 经验：ScrollView 只有一个直接子 View。
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.state.collect { current = it; render() } }
                launch { model.messages.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show() } }
            }
        }
        render()
        // TODO(C-06): 页面导航/旋转状态与正式视觉；优先迁移 Compose 时保留 Facade/ViewModel 契约。
    }
    private fun render() {
        content.removeAllViews()
        val open: (String) -> Unit = { selectedTask = it; page = "detail"; render() }
        val screen = when (page) {
            "pending" -> PendingScreen.render(this, current.tasks, open)
            "tasks" -> TaskListScreen.render(this, current.tasks, open)
            "detail" -> TaskDetailScreen.render(this, current.tasks.find { it.id == selectedTask }, model::command)
            "settings" -> SettingsScreen.render(this, current, model::settings) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            else -> HomeScreen.render(this, current, model::demo)
        }
        content.addView(screen)
    }
}
