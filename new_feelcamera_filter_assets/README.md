# NewFeelCamera filter assets

## LUT files

- `lut/film_warm.bin`
- `lut/film_dusk.bin`

Both files were inspected as raw binary assets:

- byte size: 98,304 bytes
- layout: `32 * 32 * 32 * 3`
- channel format: RGB8 / unsigned byte
- fastest-changing LUT axis in the file: R, then G, then B

This matches uploading a 32x32x32 RGB texture to `GL_TEXTURE_3D` and sampling it with `texture(uLut, rgb)` when the upload uses the file bytes without reordering.

Recommended texture sampling state for the LUT is linear filtering with clamp-to-edge on all three axes. The exact Android/OpenGL upload code is intentionally not included here.

## Shader files

- `shader/filter_vertex.glsl`
- `shader/filter_preview_fragment.glsl`
- `shader/filter_capture_fragment.glsl`

Preview and capture intentionally use separate fragment shader entry points because their source texture types differ:

- preview: `samplerExternalOES`
- capture: `sampler2D`

The film math is currently duplicated between the two files to keep the GLSL assets simple and avoid introducing a custom `#include` preprocessor before it is needed. If the two implementations begin to drift, move the common film function into a shared shader-source composition mechanism later.

### Important design detail

`vTextureCoord` and `vEffectCoord` are separate:

- `vTextureCoord` receives the CameraX/SurfaceTexture or capture-input transform and is used only for source sampling.
- `vEffectCoord` remains in output space and is used by grain/vignette.

This prevents source rotation/crop transforms from unintentionally rotating the film grain/vignette.

## What changed from the previous shader

1. Removed unused `uTime`.
2. Replaced hard-coded vignette X scale (`1.4`) with `uOutputAspectRatio`.
3. Separated transformed source coordinates from output-space effect coordinates.
4. Split preview (`samplerExternalOES`) and capture (`sampler2D`) fragment entry points explicitly.
5. Kept the LUT size at 32 because both provided LUT binaries are exactly 32^3 RGB8.
6. Uses deterministic grain without frame-time input.

## Still Verify First

These are intentionally not frozen as architecture rules yet:

- Whether preview and capture should eventually share one generated/common shader source.
- Exact grain/vignette parameter values for each Film preset.
- Exact color-space handling of CameraX preview vs decoded capture input.
- Whether the final capture crop/aspect should be forced to exactly match the preview crop.

Those decisions should be made from actual device output rather than inferred from these asset files alone.
