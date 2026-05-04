package dev.conduit.desktop.ui.theme

import androidx.compose.ui.graphics.Color

// ── Surface ──
val Background = Color(0xFF0D1117)
val Surface = Color(0xFF161B22)
val Border = Color(0xFF21262D)
val Elevated = Color(0xFF30363D)

// ── Text ──
val TextPrimary = Color(0xFFE6EDF3)
val TextSecondary = Color(0xFF8B949E)
val TextMuted = Color(0xFF484F58)

// ── Accent ──
val AccentBlue = Color(0xFF58A6FF)
val AccentPurple = Color(0xFFA371F7)

// ── NavigationRail ──
val NavRailBgTop = Color(0xFF1A1A2E)
val NavRailBgBottom = Color(0xFF16213E)
val NavRailSelectedBg = Color(0x1F58A6FF)
val NavRailBorder = Color(0x1F58A6FF)
val NavRailUnselected = Color(0xFF6E7681)

// ── Instance States ──
val StateRunning = Color(0xFF3FB950)
val StateStopped = Color(0xFF484F58)
val StateStarting = Color(0xFFA371F7)
val StateStopping = Color(0xFFF0883E)
val StateCrashed = Color(0xFFF85149)
val StateInstalling = Color(0xFFD29922)
val StateDownloading = Color(0xFF58A6FF)

// ── Daemon States ──
val DaemonOnline = Color(0xFF3FB950)
val DaemonReconnecting = Color(0xFFD29922)
val DaemonOffline = Color(0xFF484F58)

// ── Action Buttons (Style E: transparent bg + status-color border) ──
val ButtonStart = Color(0xFF3FB950)
val ButtonStartBorder = Color(0x593FB950) // rgba(63,185,80,0.35)
val ButtonStopText = Color(0xFFE6EDF3)
val ButtonStopBorder = Color(0x9930363D) // rgba(48,54,61,0.6)
val ButtonKillText = Color(0xFF8B949E)
val ButtonKillBorder = Color(0x9921262D) // rgba(33,38,45,0.6)
val ButtonDanger = Color(0xFFF85149)
val ButtonDangerBorder = Color(0x40F85149) // rgba(248,81,73,0.25)
val ButtonWarning = Color(0xFFD29922)
val ButtonWarningBorder = Color(0x59D29922) // rgba(210,153,34,0.35)
val ButtonDisabledText = Color(0xFF484F58)
val ButtonDisabledBorder = Color(0x6621262D) // rgba(33,38,45,0.4)

@Deprecated("Use Style E tokens")
val ButtonPrimary = Color(0xFF238636)
@Deprecated("Use Style E tokens")
val ButtonPrimaryText = Color(0xFF0D1117)
@Deprecated("Use Style E tokens")
val ButtonSecondary = Color(0xFF21262D)
@Deprecated("Use Style E tokens")
val ButtonSecondaryText = Color(0xFFE6EDF3)
@Deprecated("Use Style E tokens")
val ButtonDisabled = Color(0xFF21262D)

// ── Progress Bars ──
val ProgressInstallingStart = Color(0xFFD29922)
val ProgressInstallingEnd = Color(0xFFF0B232)
val ProgressDownloadingStart = Color(0xFF58A6FF)
val ProgressDownloadingEnd = Color(0xFFA371F7)

// ── Legacy (deprecated, remove after full migration) ──
@Deprecated("Use new design tokens", ReplaceWith("Background"))
val MaterialBackground = Color(0xFF0D1117)
@Deprecated("Use new design tokens", ReplaceWith("Surface"))
val MaterialSurface = Color(0xFF161B22)
@Deprecated("Use new design tokens", ReplaceWith("Elevated"))
val SurfaceContainer = Color(0xFF30363D)
@Deprecated("Use new design tokens", ReplaceWith("Elevated"))
val SurfaceContainerHigh = Color(0xFF30363D)
@Deprecated("Use AccentBlue")
val Primary = Color(0xFF58A6FF)
@Deprecated("Use new design tokens")
val OnPrimary = Color(0xFFFFFFFF)
@Deprecated("Use AccentPurple")
val Secondary = Color(0xFFA371F7)
@Deprecated("Use StateRunning")
val Tertiary = Color(0xFF3FB950)
@Deprecated("Use StateCrashed")
val Error = Color(0xFFF85149)
@Deprecated("Use TextPrimary")
val OnSurface = Color(0xFFE6EDF3)
@Deprecated("Use TextSecondary")
val OnSurfaceVariant = Color(0xFF8B949E)
