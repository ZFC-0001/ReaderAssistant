package com.example.localtaskassistant.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.localtaskassistant.contract.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AssistantUiState(
    val tasks: List<TaskSnapshot> = emptyList(),
    val settings: UserSettings = UserSettings(),
    val capture: CaptureStatus = CaptureStatus(),
    val model: ModelStatus = ModelStatus(false, "模型尚未接入"),
    val runtime: RuntimeStatus = RuntimeStatus(),
)

/** C 只处理展示和操作转发，不判断任务合并/过期/提醒规则，不接触数据库。 */
class AssistantViewModel(private val facade: AssistantFacade) : ViewModel() {
    val state = combine(facade.tasks, facade.settings, facade.captureStatus, facade.modelStatus, facade.runtimeStatus) {
            tasks, settings, capture, model, runtime -> AssistantUiState(tasks, settings, capture, model, runtime)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AssistantUiState())
    private val mutableMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = mutableMessages.asSharedFlow()
    val changes = facade.changes
    fun command(command: UserCommand) = runAction { facade.dispatch(command) }
    fun settings(settings: UserSettings) = runAction { facade.updateSettings(settings) }
    fun demo() = runAction { facade.installDemoFixture() }
    private fun runAction(action: suspend () -> ActionResult) {
        viewModelScope.launch {
            try { mutableMessages.emit(action().message) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableMessages.emit("操作失败，请重试；未输出来源原文") }
        }
    }
    class Factory(private val facade: AssistantFacade) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AssistantViewModel::class.java))
            return AssistantViewModel(facade) as T
        }
    }
}
