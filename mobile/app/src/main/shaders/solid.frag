#version 450
// Solid preview deliberately uses a neutral material, NOT Skyrim textures.
layout(location=0) in vec3 edgeColor;
layout(location=1) in vec3 viewPosition;
layout(location=0) out vec4 fragColor;
void main(){
    vec3 normal=cross(dFdx(viewPosition),dFdy(viewPosition));
    float lengthNormal=length(normal);
    float diffuse=lengthNormal>1e-7 ? abs(dot(normal/lengthNormal,normalize(vec3(0.32,0.64,0.68)))) : 0.50;
    vec3 material=vec3(0.60,0.50,0.78);
    fragColor=vec4(material*(0.40+0.60*diffuse),1.0);
}
