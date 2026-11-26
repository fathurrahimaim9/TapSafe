package my.edu.unikl.tapsafe.victim

import android.content.Context
import android.view.MotionEvent
import android.widget.Button

/** Button that rejects fully obscured touches via the framework and partial occlusion explicitly. */
class SecureActionButton(
    context: Context,
    private val onBlocked: (String) -> Unit
) : Button(context) {

    init {
        text = "CONFIRM TEST ACTION (PROTECTED)"
        // Android filters touches when another visible window obscures this view.
        filterTouchesWhenObscured = true
        isAllCaps = false
    }

    override fun onFilterTouchEventForSecurity(event: MotionEvent): Boolean {
        val isPartiallyObscured =
            event.flags and MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED != 0

        if (isPartiallyObscured) {
            onBlocked("PARTIAL_OCCLUSION_BLOCKED")
            return false
        }

        // Preserve the platform's full-obscuration filtering behaviour.
        return super.onFilterTouchEventForSecurity(event)
    }
}
