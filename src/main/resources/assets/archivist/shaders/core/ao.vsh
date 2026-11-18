#version 150

in vec4 Position;
in vec2 UV0;

out vec2 texCoord;

// The quad is already in screen space (-1..1), so no projection matrix.
void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    texCoord = UV0;
}
