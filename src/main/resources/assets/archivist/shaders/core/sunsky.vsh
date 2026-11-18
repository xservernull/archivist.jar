#version 150

in vec4 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 dir;

// The sky dome is built around the origin, so a vertex's position is its view direction in world space.
// renderSky's model-view matrix holds the camera rotation only (no translation): the sky never moves, it turns.
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position.xyz, 1.0);
    dir = Position.xyz;
}
