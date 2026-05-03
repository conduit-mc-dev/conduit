# UI Alignment — Remaining Gaps

Date: 2026-05-04
Baseline: mockup commits through `28e57f8`, code commits through `22b3a4a`

---

## 1. NavigationRail Background

**Mockup:** Solid `#0d1117` flat background
- All screens (S04, S05, S12, S20, etc.): `background: #0d1117`

**Code:** Gradient `#1A1A2E` to `#16213E`
- `NavigationRail.kt:40`: `Brush.verticalGradient(listOf(NavRailBgTop, NavRailBgBottom))`
- `Color.kt:21-22`: `NavRailBgTop = Color(0xFF1A1A2E)`, `NavRailBgBottom = Color(0xFF16213E)`

**Fix:** Change `NavRailBgTop` and `NavRailBgBottom` to `#0d1117` (same as `Background`), or replace gradient with solid `Background` color.

**Priority:** P1

---

## 2. ActionButton — Style E Not Applied to Primary/Secondary

**Mockup (style E):** All action buttons are transparent background with status-color border
- Start: `background: transparent; color: #3fb950; border: 1px solid rgba(63,185,80,0.35)`
- Stop: `background: transparent; color: #e6edf3; border: 1px solid rgba(48,54,61,0.6)`
- Kill: `background: transparent; color: #8b949e; border: 1px solid rgba(33,38,45,0.6)`
- Delete: `background: transparent; color: #f85149; border: 1px solid rgba(248,81,73,0.25)`
- Disabled: `background: transparent; color: #484f58; border: 1px solid rgba(33,38,45,0.4)`

**Code:** Primary and Secondary use solid `Button()` with opaque backgrounds
- `ActionButton.kt:28-34`: Primary uses `ButtonDefaults.buttonColors(containerColor = ButtonPrimary (#238636), contentColor = ButtonPrimaryText (#0D1117))`
- `ActionButton.kt:36-42`: Secondary uses `ButtonDefaults.buttonColors(containerColor = ButtonSecondary (#21262D), contentColor = ButtonSecondaryText (#E6EDF3))`
- Only Danger uses `OutlinedButton` (correct)

**Fix:** Convert Primary, Secondary, Warning, and disabled states to `OutlinedButton` with transparent background and status-color borders matching style E. Add a new `ButtonVariant.Stop` or refactor so Stop gets `#e6edf3` text with `rgba(48,54,61,0.6)` border, and Kill gets `#8b949e` text with `rgba(33,38,45,0.6)` border.

**Priority:** P0

---

## 3. ActionButton — Missing Stop/Kill Variants

**Mockup:** Stop and Kill are distinct button styles
- Stop: transparent bg, `#e6edf3` text, `rgba(48,54,61,0.6)` border
- Kill: transparent bg, `#8b949e` text, `rgba(33,38,45,0.6)` border

**Code:** Both Stop and Kill map to `ButtonVariant.Primary` and `ButtonVariant.Secondary` respectively, which have solid backgrounds.

**Fix:** Add `ButtonVariant.Stop` and `ButtonVariant.Kill` (or rename existing) with the correct style E colors.

**Priority:** P0 (combined with gap #2)

---

## 4. SearchBar — Missing Focus State

**Mockup:** Search box has a visible focus state
- `border-color: rgba(88,166,255,0.3)` on focus-within
- Search icon changes to `stroke: #58a6ff` on focus

**Code:** No focus state handling
- `SearchBar.kt:30-31`: Static `border(1.dp, Border, RoundedCornerShape(8.dp))` — no focus detection

**Fix:** Add `InteractionSource` to detect focus, animate border color to `AccentBlue.copy(alpha = 0.3f)` and icon tint to `AccentBlue` when focused.

**Priority:** P1

---

## 5. SearchBar — Border Radius Mismatch

**Mockup (S02):** `border-radius: 8px` (from paired-empty-v2 mockup which was committed after the radius update)
**Mockup (most others):** `border-radius: 6px`

**Code:** `RoundedCornerShape(8.dp)` in `SearchBar.kt:30`

**Note:** The mockups are inconsistent here — S02 uses 8px while S04/S05/S12/S19-0/S20 all use 6px. The commit message for `28e57f8` says "Search box radius to 6dp". This suggests 6px is the intended value but S02 was not updated.

**Fix:** Change `SearchBar.kt:30` from `RoundedCornerShape(8.dp)` to `RoundedCornerShape(6.dp)` to match the majority of mockups and the stated design intent.

**Priority:** P2

---

## 6. CrashBanner — Missing Border

**Mockup (S12):** Crash banner has a visible border
- `border: 1px solid rgba(248,81,73,0.3); border-radius: 6px`

**Code:** No border on CrashBanner
- `InstanceDetailTabScreen.kt:205-208`: `.clip(RoundedCornerShape(8.dp)).background(StateCrashed.copy(alpha = 0.1f))` — no `.border()` call

**Fix:** Add `.border(1.dp, StateCrashed.copy(alpha = 0.3f), RoundedCornerShape(6.dp))` before the `.clip()`.

**Priority:** P1

---

## 7. CrashBanner — Border Radius Should Be 6dp

**Mockup (S12):** `border-radius: 6px`
**Code:** `RoundedCornerShape(8.dp)` at `InstanceDetailTabScreen.kt:206`

**Fix:** Change to `RoundedCornerShape(6.dp)`.

**Priority:** P2

---

## 8. ReconnectBanner — Missing Border

**Mockup (S20):** Reconnect banner has a border
- `border: 1px solid rgba(210,153,34,0.4); border-radius: 6px`

**Code:** No border on ReconnectBanner
- `InstanceDetailTabScreen.kt:246-254`: Background color only, no border

**Fix:** Add `.border(1.dp, DaemonReconnecting.copy(alpha = 0.4f), RoundedCornerShape(6.dp))`.

**Priority:** P1

---

## 9. ReconnectBanner — Background Alpha Mismatch

**Mockup (S20):** `background: rgba(210,153,34,0.1)`
**Code:** `DaemonReconnecting.copy(alpha = 0.06f)` at `InstanceDetailTabScreen.kt:247`

**Fix:** Change alpha from `0.06f` to `0.1f`.

**Priority:** P2

---

## 10. ReconnectBanner — Missing Edit Button

**Mockup (S20):** Reconnect banner includes an "Edit" button on the right
- `margin-left:auto; background:#21262d; color:#58a6ff; padding:5px 14px; border-radius:6px; font-size:12px; font-weight:600`

**Code:** No Edit button in `ReconnectBanner` composable (`InstanceDetailTabScreen.kt:237-277`)

**Fix:** Add an Edit button to the `ReconnectBanner` row, pushed right with `Spacer(Modifier.weight(1f))`.

**Priority:** P1

---

## 11. ConduitCard — Progress Bar Position and Style

**Mockup (S13):** Progress bar spans the full width of the card, positioned at absolute bottom
- `.card-progress { position: absolute; bottom: 0; left: 0; right: 0; height: 3px; }`
- `.card-progress-fill { background: #d29922; }` (solid color, not gradient)

**Code:** Progress bar uses gradient and is inset from the card padding
- `ConduitCard.kt:96-103`: `fillMaxWidth(0.5f)`, positioned at `Alignment.BottomStart`, uses `Brush.horizontalGradient(listOf(ProgressInstallingStart, ProgressInstallingEnd))`

**Fix:** Remove the gradient, use solid `StateInstalling` color. Remove padding constraints so it spans full card width edge-to-edge.

**Priority:** P2

---

## 12. InstallProgressScreen — Progress Bar Background Color

**Mockup (S13):** Progress bar track is `#0d1117` (Background)
- `.progress-bar-outer { background: #0d1117; }`

**Code:** Progress bar track uses `Elevated` (#30363D)
- `InstallProgressScreen.kt:61`: `.background(Elevated)`

**Fix:** Change to `.background(Background)`.

**Priority:** P2

---

## 13. Dialogs (S17, S18, S22) — Using Material3 AlertDialog Instead of Custom Dialog

**Mockup (S17, S18, S22):** Custom dialog with:
- Icon in a 40x40dp rounded box with tinted background (`rgba(248,81,73,0.1)` + `border: 1px solid rgba(248,81,73,0.2)`)
- Title at 16px/700 weight
- Body text at 13px with `#8b949e` color
- Server/daemon reference cards with `background: #0d1117; border: 1px solid #21262d; border-radius: 8px`
- Warning box with `background: rgba(248,81,73,0.06); border: 1px solid rgba(248,81,73,0.15); border-radius: 8px`
- Button row: Cancel (#21262d bg, #e6edf3 text) + Danger (#f85149 bg, #fff text) or Save (#58a6ff bg, #0d1117 text)
- Dialog border-radius: 14px, padding: 28px

**Code:** Uses `Material3 AlertDialog`
- `ConduitDialog.kt:14-29`: `AlertDialog` with `containerColor = Surface` but no custom shape, no server reference card, no warning box, no custom icon container

**Fix:** Replace `AlertDialog` with a custom `Dialog` composable that matches the mockup's layout: icon-in-box, server reference card, warning box, and solid-background action buttons. Material3's `AlertDialog` applies its own shape/padding that doesn't match.

**Priority:** P1

---

## 14. PairedEmptyScreen — Create Server Button Style

**Mockup (S02):** Single accent button
- `background: #58a6ff; color: #0d1117; padding: 10px 24px; border-radius: 8px; font-size: 13px; font-weight: 600`

**Code:** Uses Material3 `Button` with `containerColor = AccentBlue, contentColor = Background`
- `PairedEmptyScreen.kt:45-55`: Matches the mockup colors correctly

**Gap:** The `shape` is `RoundedCornerShape(8.dp)` and content padding is `PaddingValues(horizontal = 24.dp, vertical = 10.dp)` — this matches. No gap here.

**Priority:** N/A (already correct)

---

## 15. PlayerCard — Missing Player Detail Line

**Mockup (S07):** Player cards show "Connected since 12:35" detail text
- `.player-detail { font-size: 11px; color: #8b949e; margin-top: 2px; }`

**Code:** `PlayerCard` only shows name, no detail text
- `PlayersTab.kt:129-134`: Only renders `name` as `bodyMedium`, no secondary detail line

**Fix:** Add a secondary text line below the player name showing connection time or other detail (requires API data).

**Priority:** P2 (data-dependent)

---

## 16. PlayerCard — Max Players Color

**Mockup (S07):** Max players number is `#8b949e` (TextSecondary)
- `.stat-number.gray { color: #8b949e; }`

**Code:** Max players uses `TextMuted` (#484F58)
- `PlayersTab.kt:60`: `color = TextMuted`

**Fix:** Change to `TextSecondary`.

**Priority:** P2

---

## 17. NavItem — Active Font Weight

**Mockup:** Active nav item label uses `font-weight: 700`
- `.nav-item.active span { color: #58a6ff; font-weight: 700; }`

**Code:** Uses `FontWeight.Bold` (which is 700) — matches.
- `NavigationRail.kt:83`: `fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal`

**Priority:** N/A (already correct)

---

## 18. TabBar — Tab Padding

**Mockup:** Tab items use `padding: 8px 16px`
- `.tab { padding: 8px 16px; }`

**Code:** Uses `padding(horizontal = 16.dp, vertical = 10.dp)`
- `TabBar.kt:50`: `.padding(horizontal = 16.dp, vertical = 10.dp)`

**Fix:** Change vertical padding from `10.dp` to `8.dp`.

**Priority:** P2

---

## 19. ContentHeader — Missing Bottom Border

**Mockup:** Content header has a bottom border
- `.content-header { border-bottom: 1px solid #21262d; }`

**Code:** No explicit bottom border on ContentHeader
- `ContentHeader.kt:29`: `.background(Surface).padding(...)` — no border

**Fix:** Add a bottom border using `drawBehind` or `.border()`.

**Priority:** P2

---

## 20. DaemonGroupHeader — Daemon Dot Size

**Mockup:** Daemon status dot is 7x7px
- `.daemon-dot { width: 7px; height: 7px; }`

**Code:** Uses `StatusDotSize.Small` which is 6dp
- `InstanceListPanel.kt:133`: `DaemonStatusDot(group.connectionState, StatusDotSize.Small)`
- `StatusDot.kt:25`: `Small(6.dp)`

**Fix:** Add a `StatusDotSize.Smaller(7.dp)` or change `Small` to `7.dp`. However, the card-level status dots are also 6dp in the mockup (`.card-dot { width: 6px; height: 6px; }`), so this may need a dedicated daemon-dot size.

**Priority:** P2

---

## 21. ContentHeader — Status Dot Size

**Mockup:** Header status dot is 8x8px
- `.header-dot { width: 8px; height: 8px; }`

**Code:** Uses `StatusDotSize.Medium` which is 8dp
- `ContentHeader.kt:35`: `StatusDot(instanceState, StatusDotSize.Medium)`
- `StatusDot.kt:26`: `Medium(8.dp)`

**Priority:** N/A (already correct)

---

## 22. Card Info Text Font Size

**Mockup:** Card info text is `font-size: 10px`
- `.card-info { font-size: 10px; }`

**Code:** Uses `MaterialTheme.typography.labelMedium` which is `10.sp, FontWeight.Bold, letterSpacing = 0.5.sp`
- `ConduitCard.kt:82`: `style = MaterialTheme.typography.labelMedium`

**Gap:** The mockup info text is not bold (weight 600 for the name, normal for info). The code's `labelMedium` has `FontWeight.Bold`. This makes info text bolder than intended.

**Fix:** Use `labelMedium.copy(fontWeight = FontWeight.Normal)` or define a dedicated style.

**Priority:** P2

---

## 23. Gear Dropdown Menu — Missing Disconnect Item

**Mockup (S19-0):** Gear dropdown only shows Edit and Forget
- Edit item + separator + Forget item (danger)

**Code:** Gear dropdown shows Edit, Disconnect, and Forget
- `InstanceListPanel.kt:161-179`: Three items with dividers

**Gap:** The mockup does not show a "Disconnect" option. The code adds it.

**Note:** This may be an intentional code addition not yet reflected in mockups. Flag for design review.

**Priority:** P2 (design decision, not visual bug)

---

## 24. CrashBanner — Sub-text Font Size

**Mockup (S12):** Crash banner sub-text is `font-size: 11px`
- `.crash-banner-sub { font-size: 11px; color: #8b949e; }`

**Code:** Sub-text uses `fontSize = 10.sp`
- `InstanceDetailTabScreen.kt:229`: `fontSize = 10.sp`

**Fix:** Change to `11.sp`.

**Priority:** P2

---

## 25. ReconnectBanner — Sub-text Font Size

**Mockup (S20):** "Retrying connection" text is `font-size: 11px`
- `.reconnect-sub { font-size: 11px; color: #8b949e; }`

**Code:** Uses `fontSize = 11.sp` — matches.
- `InstanceDetailTabScreen.kt:274`: `fontSize = 11.sp`

**Priority:** N/A (already correct)

---

## 26. CrashBanner — Title Font Weight

**Mockup (S12):** "Server crashed" text is bold (implied by `.crash-banner-text { font-size: 12px; color: #f85149; }` — no explicit weight, so browser default ~400)

**Code:** Uses `fontWeight = FontWeight.SemiBold` (600)
- `InstanceDetailTabScreen.kt:221`: `fontWeight = FontWeight.SemiBold`

**Gap:** Code is bolder than mockup. However, this is a minor improvement — Semibold reads better at 12px. Keep as-is unless strict mockup parity is required.

**Priority:** P3 (acceptable deviation)

---

## Summary by Priority

| Priority | Count | Items |
|----------|-------|-------|
| P0       | 2     | #2/#3 ActionButton style E |
| P1       | 5     | #1 NavRail bg, #4 SearchBar focus, #6 CrashBanner border, #8 ReconnectBanner border, #10 ReconnectBanner Edit btn, #13 Dialogs |
| P2       | 8     | #5 SearchBar radius, #7 CrashBanner radius, #9 ReconnectBanner alpha, #11 Card progress bar, #12 InstallProgress track, #15 Player detail, #16 MaxPlayers color, #18 Tab padding, #19 ContentHeader border, #20 Daemon dot size, #22 Card info weight, #23 Gear menu items, #24 CrashBanner sub font |
| P3       | 1     | #26 CrashBanner title weight |

**Total: 16 actionable gaps** (excluding already-correct items and P3).
