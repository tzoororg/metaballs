# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A single-file WebGL metaballs animation, intended to become a phone screensaver. Currently a visuals-only mock. No build system, no dependencies — everything lives in `index.html`.

## Running & deploying

WebGL needs a real HTTP origin — `file://` will not run (the in-app browser preview renders `file://` as a static snapshot with scripts stripped). Always serve it:

```bash
python -m http.server 8791    # then open http://localhost:8791/
```

Deployed to GitHub Pages (public repo `tzoororg/metaballs`). Redeploy by pushing to `master`; Pages rebuilds in ~1 min:

```bash
git add -A && git commit -m "..." && git push
```

Live URL: https://tzoororg.github.io/metaballs/

## Architecture

The metaball field is computed entirely on the GPU in one fragment-shader pass (`fsrc` in `index.html`) — this is what keeps it phone-friendly. JavaScript does almost nothing per frame: it only updates ball positions and uploads them as a uniform.

- **Field**: for each pixel, sum `r²/dist²` over all balls; `f > ~1` is inside the surface. `smoothstep` on `f` gives the soft/gooey edge. There is no per-ball geometry — the merged blobs are an emergent property of the summed field.
- **Styles**: `uStyle` (0–3) selects the palette *inside the shader* (neon / lava lamp / ink / oil slick). Switching styles is just a uniform change — the field math is shared. Neon glow (style 0) is the current default.
- **Coordinate spaces** (easy to trip on): ball positions are stored in `0..1`, but passed to the shader in **aspect-corrected space** (`x * aspect`, y unchanged) so blobs stay round. In-shader color gradients use `n` = the `0..1` space, not the aspect space.
- **Motion invariant**: velocities are **unit vectors** scaled by `SPEED`. Organic wander comes from *rotating* the velocity each frame (via `spin`); walls *reflect* a component. Both operations preserve magnitude, so speed is constant and wall bounces are perfectly elastic — do not reintroduce additive positional wobble (an earlier version did, and it fought the wall bounce, making balls appear to stall at edges).

## Tuning knobs (top of the `<script>`)

- `COUNT` — number of balls. `MAX` is the shader's uniform-array size and **must be ≥ COUNT** (it's interpolated into the shader source as `uBalls[${MAX}]`).
- `SPEED` — drift speed. Ball radii and the count of "big" balls are set in the init loop (`i < 2` are large).
- Screensaver behaviors: the control chrome auto-hides after ~3.5s idle; `prefers-reduced-motion` freezes `uTime` (animation stops but the field still renders).

## Current design intent

Per the user: **neon glow** palette, medium mix (~10 balls, 2 large + 8 small), slow-but-visibly-moving pace with clearly visible merge/split action. The other three palettes are kept selectable for comparison.
