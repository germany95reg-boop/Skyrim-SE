#version 450
layout(location = 0) in vec3 edgeColor;
layout(location = 0) out vec4 fragColor;
void main() {
    fragColor = vec4(edgeColor, 1.0);
}
