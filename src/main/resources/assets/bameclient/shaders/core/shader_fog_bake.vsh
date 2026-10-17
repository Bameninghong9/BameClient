#version 330

in vec3 Position;

// Panorama-Koordinate: x = Längengrad (0..1), y = Abstand vom Zenit (0 = oben, 1 = unten)
out vec2 texCoord;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    texCoord = Position.xy * 0.5 + 0.5;
}
