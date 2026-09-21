#version 300 es

in vec4 aPosition;
in vec2 aTexCoord;

// Source texture transform only.
// Preview: CameraX/SurfaceTexture transform.
// Capture: identity or the transform required by the decoded capture input.
uniform mat4 uTextureTransform;

out vec2 vTextureCoord;
out vec2 vEffectCoord;

void main() {
    gl_Position = aPosition;

    // Coordinate used only to sample the camera/capture source.
    vTextureCoord = (uTextureTransform * vec4(aTexCoord, 0.0, 1.0)).xy;

    // Stable output-space coordinate used by film effects such as grain/vignette.
    // Keeping this separate from the source transform prevents rotation/crop transforms
    // from unintentionally rotating or shifting the film effect itself.
    vEffectCoord = aTexCoord;
}
