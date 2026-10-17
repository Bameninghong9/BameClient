#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;

// Blickrichtung in Weltkoordinaten (der Würfel liegt um die Kamera, ModelViewMat ist nur die Kameradrehung)
out vec3 skyDir;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    skyDir = Position;
}
