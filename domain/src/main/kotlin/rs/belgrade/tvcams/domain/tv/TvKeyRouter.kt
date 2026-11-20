package rs.belgrade.tvcams.domain.tv

enum class TvKeyCode {
    DPAD_CENTER,
    DPAD_LEFT,
    DPAD_RIGHT,
    DPAD_UP,
    DPAD_DOWN,
    CHANNEL_UP,
    CHANNEL_DOWN,
    INFO,
    BOOKMARK,
    MENU,
    SEARCH,
    MEDIA_PLAY_PAUSE,
    MUTE,
    BACK,
    DIGIT_0,
    DIGIT_1,
    DIGIT_2,
    DIGIT_3,
    DIGIT_4,
    DIGIT_5,
    DIGIT_6,
    DIGIT_7,
    DIGIT_8,
    DIGIT_9,
    OTHER,
    ;

    fun digitValue(): Int? = when (this) {
        DIGIT_0 -> 0
        DIGIT_1 -> 1
        DIGIT_2 -> 2
        DIGIT_3 -> 3
        DIGIT_4 -> 4
        DIGIT_5 -> 5
        DIGIT_6 -> 6
        DIGIT_7 -> 7
        DIGIT_8 -> 8
        DIGIT_9 -> 9
        else -> null
    }
}

enum class TvKeyPhase {
    DOWN,
    UP,
}

data class KeyRouteDecision(
    val consumed: Boolean,
    val action: TvRemoteAction? = null,
)

class TvKeyRouter {
    private var okLongPressConsumed: Boolean = false

    fun route(
        keyCode: TvKeyCode,
        phase: TvKeyPhase,
        repeatCount: Int,
        overlayMode: OverlayMode,
    ): KeyRouteDecision {
        // Global dedicated TV keys active in all overlay modes
        if (phase == TvKeyPhase.DOWN && repeatCount == 0) {
            when (keyCode) {
                TvKeyCode.CHANNEL_UP -> return KeyRouteDecision(true, TvRemoteAction.ZapNext)
                TvKeyCode.CHANNEL_DOWN -> return KeyRouteDecision(true, TvRemoteAction.ZapPrevious)
                TvKeyCode.INFO -> return KeyRouteDecision(true, TvRemoteAction.ToggleInfoOrSelect)
                TvKeyCode.BOOKMARK -> return KeyRouteDecision(true, TvRemoteAction.ToggleFavoriteCurrent)
                TvKeyCode.MENU -> return KeyRouteDecision(true, TvRemoteAction.OpenSettings)
                TvKeyCode.SEARCH -> return KeyRouteDecision(true, TvRemoteAction.OpenSearch)
                TvKeyCode.MEDIA_PLAY_PAUSE,
                TvKeyCode.MUTE,
                -> return KeyRouteDecision(true, TvRemoteAction.ToggleStreamAudio)
                else -> Unit
            }
        }

        // When Quick Strip, Settings drawer, or Search overlay is open, allow Compose focus to handle navigation
        if (overlayMode == OverlayMode.QUICK_STRIP ||
            overlayMode == OverlayMode.SETTINGS ||
            overlayMode == OverlayMode.SEARCH
        ) {
            return KeyRouteDecision(consumed = false)
        }

        val digit = keyCode.digitValue()
        if (digit != null) {
            return if (phase == TvKeyPhase.DOWN && repeatCount == 0) {
                KeyRouteDecision(consumed = true, action = TvRemoteAction.AppendChannelDigit(digit))
            } else {
                KeyRouteDecision(consumed = true, action = null)
            }
        }

        return when (keyCode) {
            TvKeyCode.DPAD_CENTER -> when (phase) {
                TvKeyPhase.DOWN -> {
                    if (repeatCount == 0) {
                        okLongPressConsumed = false
                        KeyRouteDecision(consumed = true, action = null)
                    } else if (!okLongPressConsumed && repeatCount >= 1) {
                        okLongPressConsumed = true
                        KeyRouteDecision(consumed = true, action = TvRemoteAction.ToggleFavoriteCurrent)
                    } else {
                        KeyRouteDecision(consumed = true, action = null)
                    }
                }
                TvKeyPhase.UP -> {
                    val shouldToggleInfo = !okLongPressConsumed
                    okLongPressConsumed = false
                    KeyRouteDecision(
                        consumed = true,
                        action = if (shouldToggleInfo) TvRemoteAction.ToggleInfoOrSelect else null,
                    )
                }
            }

            TvKeyCode.DPAD_RIGHT -> if (phase == TvKeyPhase.DOWN) {
                KeyRouteDecision(consumed = true, action = TvRemoteAction.ZapNext)
            } else {
                KeyRouteDecision(consumed = true, action = null)
            }

            TvKeyCode.DPAD_LEFT -> if (phase == TvKeyPhase.DOWN) {
                KeyRouteDecision(consumed = true, action = TvRemoteAction.ZapPrevious)
            } else {
                KeyRouteDecision(consumed = true, action = null)
            }

            TvKeyCode.DPAD_DOWN -> if (phase == TvKeyPhase.DOWN && repeatCount == 0) {
                KeyRouteDecision(consumed = true, action = TvRemoteAction.OpenQuickStrip)
            } else if (phase == TvKeyPhase.UP) {
                KeyRouteDecision(consumed = true, action = null)
            } else {
                KeyRouteDecision(consumed = false)
            }

            TvKeyCode.DPAD_UP -> if (phase == TvKeyPhase.DOWN && repeatCount == 0) {
                KeyRouteDecision(consumed = true, action = TvRemoteAction.ToggleZapScope)
            } else if (phase == TvKeyPhase.UP) {
                KeyRouteDecision(consumed = true, action = null)
            } else {
                KeyRouteDecision(consumed = false)
            }

            else -> KeyRouteDecision(consumed = false)
        }
    }
}
