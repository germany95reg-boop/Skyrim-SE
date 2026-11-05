#version 450
layout(set=0,binding=0) uniform sampler2D diffuseSampler;
layout(location=0) in vec2 uv;
layout(location=1) in vec3 viewPosition;
layout(location=0) out vec4 fragColor;
void main(){
    vec4 base=texture(diffuseSampler,uv);
    vec3 n=cross(dFdx(viewPosition),dFdy(viewPosition));
    float strength=length(n);
    float diffuse=strength>1e-7 ? abs(dot(n/strength,normalize(vec3(0.32,0.64,0.68)))) : 0.5;
    fragColor=vec4(base.rgb*(0.45+0.55*diffuse),base.a);
}
