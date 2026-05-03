package dev.conduit.desktop.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ConduitCustomColors(
    val background: Color = Background,
    val surface: Color = Surface,
    val border: Color = Border,
    val elevated: Color = Elevated,
    val textPrimary: Color = TextPrimary,
    val textSecondary: Color = TextSecondary,
    val textMuted: Color = TextMuted,
    val accentBlue: Color = AccentBlue,
    val accentPurple: Color = AccentPurple,
    val stateRunning: Color = StateRunning,
    val stateStopped: Color = StateStopped,
    val stateStarting: Color = StateStarting,
    val stateStopping: Color = StateStopping,
    val stateCrashed: Color = StateCrashed,
    val stateInstalling: Color = StateInstalling,
    val stateDownloading: Color = StateDownloading,
    val daemonOnline: Color = DaemonOnline,
    val daemonReconnecting: Color = DaemonReconnecting,
    val daemonOffline: Color = DaemonOffline,
    val buttonPrimary: Color = ButtonPrimary,
    val buttonPrimaryText: Color = ButtonPrimaryText,
    val buttonSecondary: Color = ButtonSecondary,
    val buttonSecondaryText: Color = ButtonSecondaryText,
    val buttonDanger: Color = ButtonDanger,
    val buttonWarning: Color = ButtonWarning,
    val buttonDisabled: Color = ButtonDisabled,
    val buttonDisabledText: Color = ButtonDisabledText,
    val navRailBgTop: Color = NavRailBgTop,
    val navRailBgBottom: Color = NavRailBgBottom,
    val navRailSelectedBg: Color = NavRailSelectedBg,
    val navRailBorder: Color = NavRailBorder,
    val navRailUnselected: Color = NavRailUnselected,
)

val LocalConduitColors = staticCompositionLocalOf { ConduitCustomColors() }
