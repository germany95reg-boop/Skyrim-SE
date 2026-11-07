#version 450
// Last-resort Vulkan compatibility path: one unlit DDS for the whole mesh.
// This shader intentionally avoids push constants, flat varyings and derivatives.
layout(set=0,binding=0) uniform sampler2D diffuseSampler;
layout(location=0) in vec2 uv;
layout(location=0) out vec4 fragColor;
void main() {
    vec4 base=texture(diffuseSampler,uv);
    if(base.a < 0.05) discard; // Retains last-resort sampled DDS fallback.
    fragColor=base;
}
