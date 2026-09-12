#version 150

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 guiPos;
out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    guiPos = Position.xy;
    texCoord0 = UV0;
    vertexColor = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
