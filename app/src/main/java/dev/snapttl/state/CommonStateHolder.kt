package dev.snapttl.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CommonState(
    val serviceEnabled: Boolean = true,
    val autoDeleteDurationSeconds: Long = dev.snapttl.domain.DeletionDuration.DEFAULT_SECONDS,
    val noDialogAutoDelete: Boolean = false
)

/**
 * Process-wide, observable app state (service toggle, default deletion timer,
 * silent auto-delete) without depending on Compose so services and workers can
 * read it too.
 */
class CommonStateHolder {
    private val _state = MutableStateFlow(CommonState())
    val state: StateFlow<CommonState> = _state.asStateFlow()

    fun update(transform: (CommonState) -> CommonState) {
        _state.update(transform)
    }

    fun snapshot(): CommonState = _state.value
}
