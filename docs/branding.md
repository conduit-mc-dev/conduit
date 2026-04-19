# Conduit MC — Brand & Visual Assets

This directory holds the project's visual identity.
All files here are part of **Conduit MC** and are licensed under **GPLv3**
(same as the rest of the project).

## Files

| File | Purpose | Format | Notes |
|---|---|---|---|
| `logo-icon.svg` | Primary icon (square) | SVG, 320×320 viewBox | Use this for avatars, favicons, app icons |
| `logo-icon-512.png` | Rasterized icon at 512px | PNG | For platforms that need a raster PNG |
| `logo.png` | Rasterized icon at 256px | PNG | README-size |
| `logo-full.svg` | Full lockup (icon + wordmark + tagline) | SVG, 1120×320 viewBox | For banners, headers, social cards |
| `logo-full.png` | Rasterized lockup at 1600px wide | PNG | For places that don't render SVG |

## Color palette

| Role | Hex | Usage |
|---|---|---|
| **Conduit Cyan** (primary) | `#2BCEE9` | Main brand color, accents, "MC" in wordmark |
| **Deep Teal** (dark) | `#0B3D4E` | Outlines, "Conduit" in wordmark |
| **Core White** (highlight) | `#E9FBFF` | Center glow of the icon |
| **Slate** (muted) | `#547080` | Tagline and secondary text |

## Typeface

The wordmark uses a geometric sans-serif font stack with safe system fallbacks:

```
"Inter", "SF Pro Display", "Segoe UI",
"Helvetica Neue", Arial, sans-serif
```

No custom font file is bundled, so the logo reads well even in environments where Inter isn't installed.

## Design notes

The icon is a **9×9 pixel-art silhouette** evoking a beacon/conduit core —
a distant, respectful nod to Minecraft's in-game Conduit block, without using any
Mojang/Microsoft trademark or asset.

- Each "pixel" is a 32×32 `<rect>` in SVG, making the icon **crisp at any size**.
- Center glow is a 3×3 cross to suggest light emanating from a core.
- Outline is deep teal to keep the icon legible on both light and dark backgrounds.

## Using the logo

✅ **You may:**
- Use the logo to link back to this project.
- Embed it in articles, tutorials, or videos about Conduit MC.
- Create forks/derivatives; if you do, please use a distinct name and logo to avoid confusion.

❌ **Please don't:**
- Use the logo to imply an official endorsement from the Conduit MC project.
- Modify the icon to represent a different product while keeping the name "Conduit MC".
- Claim authorship of unmodified assets.

## Trademark disclaimer

"Minecraft" is a trademark of **Mojang Studios / Microsoft Corporation**.
Conduit MC is an independent open-source project and is **not affiliated with,
endorsed by, or sponsored by** Mojang or Microsoft.
