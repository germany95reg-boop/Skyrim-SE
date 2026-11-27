#version 450
// Lightweight driver-compatibility path used only when the full v2.13 shader fails.
// Keep the same vertex attributes, descriptor set and push-constant offsets.
layout(set=0,binding=0) uniform sampler2D diffuseSampler;
layout(location=0) in vec2 uv;
layout(location=2) flat in float materialSlot;
layout(location=3) in vec4 vertexColor;
layout(location=0) out vec4 fragColor;
layout(push_constant) uniform TextureOptions {
    layout(offset=28) int atlasMode;
    layout(offset=52) int diffuseAtlasGrid;
} options;
void main() {
    vec2 sampleUV=uv;
    vec2 inset=0.5/vec2(textureSize(diffuseSampler,0));
    if (options.atlasMode==1) {
        // Diffuse is the upper half of a diffuse + normal preview atlas.
        sampleUV=vec2(fract(uv.x),mix(inset.y,0.5-inset.y,fract(uv.y)));
    } else if (options.atlasMode==2||options.atlasMode==3) {
        float grid=float(clamp(options.diffuseAtlasGrid,1,4));
        float slot=clamp(floor(materialSlot+0.5),0.0,grid*grid-1.0);
        float row=floor(slot/grid);
        float col=slot-row*grid;
        vec2 lo=vec2(col,row)/grid;
        vec2 hi=(vec2(col,row)+1.0)/grid;
        if(options.atlasMode==3){lo.y*=0.5;hi.y*=0.5;}
        sampleUV=mix(lo+inset,hi-inset,fract(uv));
    }
    vec4 base=materialSlot< -0.5 ? vec4(vertexColor.rgb,1.0) :
        texture(diffuseSampler,sampleUV);
    if(base.a < 0.05) discard; // Driver-safe cutout; no extra samplers.
    fragColor=base;
}
