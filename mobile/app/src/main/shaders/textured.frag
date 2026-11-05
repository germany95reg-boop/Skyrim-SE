#version 450
layout(set=0,binding=0) uniform sampler2D diffuseSampler;
layout(location=0) in vec2 uv;
layout(location=1) in vec3 viewPosition;
layout(location=0) out vec4 fragColor;

// The vertex stage reads the first 28 bytes of the SAME push-constant block.
// The fragment stage only needs the two trailing 32-bit options.
layout(push_constant) uniform NormalOptions {
    layout(offset=28) int atlasMode;
    layout(offset=32) int normalEnabled;
    layout(offset=36) int lightMode;
    layout(offset=40) float reliefStrength;
    layout(offset=44) float specularStrength;
    layout(offset=48) int specularSize;
} options;

void main(){
    // UVs from NIF are repeating; the optional two-part atlas uses explicit
    // fract() + half-texel clamping to avoid bilinear bleed between its halves.
    vec2 tiled=fract(uv);
    float halfPixel=0.5/float(textureSize(diffuseSampler,0).y);
    vec2 diffuseUV=options.atlasMode!=0 ?
        vec2(tiled.x,mix(halfPixel,0.5-halfPixel,tiled.y)) : uv;
    vec4 base=texture(diffuseSampler,diffuseUV);
    vec3 dpdx=dFdx(viewPosition),dpdy=dFdy(viewPosition);
    vec2 dudx=dFdx(uv),dudy=dFdy(uv);
    vec3 crossN=cross(dpdx,dpdy);
    float strength=length(crossN);
    vec3 n=strength>1e-7?crossN/strength:vec3(0.0,0.0,1.0);

    // Per-pixel normal alpha is used as a gloss/specular *preview* mask.
    // It is not a full simulation of Skyrim's BSLightingShaderProperty.
    vec2 normalUV=vec2(tiled.x,mix(0.5+halfPixel,1.0-halfPixel,tiled.y));
    if(options.atlasMode!=0&&options.normalEnabled!=0){
        vec2 xy=texture(diffuseSampler,normalUV).rg*2.0-1.0;
        // Bounded normal-map emphasis; 100% matches the proven v2.2 preview.
        xy=clamp(xy*clamp(options.reliefStrength,0.5,1.5),
                 vec2(-1.0),vec2(1.0));
        float z=sqrt(max(0.0,1.0-dot(xy,xy)));
        float det=dudx.x*dudy.y-dudx.y*dudy.x;
        if(abs(det)>1e-8&&strength>1e-7){
            vec3 tangent=dpdx*dudy.y-dpdy*dudx.y;
            tangent-=n*dot(n,tangent);
            float tLength=length(tangent);
            if(tLength>1e-7){
                vec3 t=tangent/tLength;
                vec3 b=normalize(cross(n,t))*(det<0.0?-1.0:1.0);
                n=normalize(t*xy.x+b*xy.y+n*z);
            }
        }
    }
    // View-space light presets. Mode zero preserves exactly the v2.2 lighting.
    vec3 lightDirection=vec3(0.32,0.64,0.68);
    if(options.lightMode==1)lightDirection=vec3(0.06,0.20,0.98);
    else if(options.lightMode==2)lightDirection=vec3(-0.76,0.45,0.48);
    else if(options.lightMode==3)lightDirection=vec3(0.15,0.94,0.30);
    float diffuse=abs(dot(n,normalize(lightDirection)));
    vec3 shaded=base.rgb*(0.45+0.55*diffuse);
    if(options.atlasMode!=0&&options.specularStrength>0.001){
        // Two-sided preview geometry: orient the shaded normal toward a
        // camera-facing view vector. Approximate lighting, not game PBR.
        vec3 viewDir=normalize(vec3(0.0,0.0,-1.0)-viewPosition*0.08);
        vec3 faceN=dot(n,viewDir)<0.0 ? -n : n;
        vec3 specLight=normalize(vec3(lightDirection.xy,-abs(lightDirection.z)));
        vec3 halfVector=normalize(specLight+viewDir);
        float exponent=options.specularSize==0 ? 12.0 :
                       options.specularSize==2 ? 64.0 : 32.0;
        float highlight=pow(max(dot(faceN,halfVector),0.0),exponent);
        float glossMask=texture(diffuseSampler,normalUV).a;
        float glossWeight=mix(0.25,1.0,glossMask);
        float intensity=clamp(options.specularStrength,0.0,1.0);
        shaded=clamp(shaded+vec3(highlight*glossWeight*intensity*0.55),0.0,1.0);
    }
    fragColor=vec4(shaded,base.a);
}
