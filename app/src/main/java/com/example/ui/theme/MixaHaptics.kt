package com.example.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalView

enum class MixaHapticKind {
    TAP,
    CONFIRM,
    TOGGLE_ON,
    TOGGLE_OFF,
    TICK,
    GESTURE_START,
    GESTURE_END,
    LONG_PRESS,
    REJECT
}

/**
 * Centralized tactile haptic feedback dispatcher for Mixa.
 * Maps UI gestures cleanly to modern Android HapticFeedbackConstants with safe pre-R/pre-U fallbacks.
 */
object MixaHaptics {

    fun perform(view: View?, kind: MixaHapticKind) {
        if (view == null) return
        val code = when (kind) {
            MixaHapticKind.TAP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                HapticFeedbackConstants.CONTEXT_CLICK
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            }
            MixaHapticKind.CONFIRM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            }
            MixaHapticKind.TOGGLE_ON -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HapticFeedbackConstants.TOGGLE_ON
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
            MixaHapticKind.TOGGLE_OFF -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HapticFeedbackConstants.TOGGLE_OFF
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
            MixaHapticKind.TICK -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
            MixaHapticKind.GESTURE_START -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.GESTURE_START
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
            MixaHapticKind.GESTURE_END -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.GESTURE_END
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            }
            MixaHapticKind.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
            MixaHapticKind.REJECT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
        }
        val executed = runCatching {
            view.performHapticFeedback(code)
        }.getOrDefault(false)
        if (!executed && code != HapticFeedbackConstants.KEYBOARD_TAP) {
            runCatching { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
        }
    }

    fun tap(view: View?) = perform(view, MixaHapticKind.TAP)
    fun confirm(view: View?) = perform(view, MixaHapticKind.CONFIRM)
    fun toggle(view: View?, checked: Boolean) =
        perform(view, if (checked) MixaHapticKind.TOGGLE_ON else MixaHapticKind.TOGGLE_OFF)
    fun tick(view: View?) = perform(view, MixaHapticKind.TICK)
    fun gestureStart(view: View?) = perform(view, MixaHapticKind.GESTURE_START)
    fun gestureEnd(view: View?) = perform(view, MixaHapticKind.GESTURE_END)
    fun longPress(view: View?) = perform(view, MixaHapticKind.LONG_PRESS)
    fun reject(view: View?) = perform(view, MixaHapticKind.REJECT)
}

@Composable
fun rememberMixaHaptic(): (MixaHapticKind) -> Unit {
    val view = LocalView.current
    return remember(view) {
        { kind -> MixaHaptics.perform(view, kind) }
    }
}

fun Modifier.hapticClickable(
    enabled: Boolean = true,
    kind: MixaHapticKind = MixaHapticKind.TAP,
    onClick: () -> Unit
): Modifier = composed {
    val view = LocalView.current
    clickable(enabled = enabled) {
        MixaHaptics.perform(view, kind)
        onClick()
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.hapticCombinedClickable(
    enabled: Boolean = true,
    clickKind: MixaHapticKind = MixaHapticKind.CONFIRM,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val view = LocalView.current
    combinedClickable(
        enabled = enabled,
        onClick = {
            MixaHaptics.perform(view, clickKind)
            onClick()
        },
        onLongClick = onLongClick?.let { longAction ->
            {
                MixaHaptics.longPress(view)
                longAction()
            }
        }
    )
}
