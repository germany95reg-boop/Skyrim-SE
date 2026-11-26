#version 450
// v2.18 lightweight normal-map fallback for Adreno devices that reject the
// full material shader: float material varying, two texture samples only.
// Keeps the original atlas-safe diffuse-only shader as the final fallback.
layout(set=0,binding=0) uniform sampler2D diffuseSampler;
layout(location=0) in vec2 uv;
layout(location=1) in vec3 viewPosition;
layout(location=2) flat in float materialSlot;
layout(location=3) in vec4 vertexColor;
layout(location=0) out vec4 fragColor;
layout(push_constant) uniform NormalOptions {
    layout(offset=28) int atlasMode;
    layout(offset=32) int normalEnabled;
    layout(offset=36) int lightMode;
    layout(offset=40) float reliefStrength;
    layout(offset=44) float specularStrength;
    layout(offset=48) int specularSize;
    layout(offset=52) int diffuseAtlasGrid;
} options;
void main(){
    vec2 tiled=fract(uv);
    vec2 diffuseUV=uv;
    vec2 inset=0.5/vec2(textureSize(diffuseSampler,0));
    vec2 normalUV=vec2(tiled.x,mix(0.5+inset.y,1.0-inset.y,tiled.y));
    if(options.atlasMode==1){
        diffuseUV=vec2(tiled.x,mix(inset.y,0.5-inset.y,tiled.y));
    } else if(options.atlasMode==2||options.atlasMode==3){
        float grid=float(clamp(options.diffuseAtlasGrid,1,4));
        float slot=clamp(floor(materialSlot+0.5),0.0,grid*grid-1.0);
        float row=floor(slot/grid),col=slot-row*grid;
        vec2 lo=vec2(col,row)/grid;
        vec2 hi=(vec2(col,row)+1.0)/grid;
        if(options.atlasMode==3){lo.y*=0.5;hi.y*=0.5;}
        diffuseUV=mix(lo+inset,hi-inset,tiled);
        if(options.atlasMode==3)normalUV=diffuseUV+vec2(0.0,0.5);
    }
    vec4 base=materialSlot< -0.5 ? vec4(vertexColor.rgb,1.0) :
        texture(diffuseSampler,diffuseUV);
    vec3 pDx=dFdx(viewPosition),pDy=dFdy(viewPosition);
    vec3 geoN=cross(pDx,pDy);
    float nl=length(geoN);
    vec3 n=nl>1e-7?geoN/nl:vec3(0.0,0.0,1.0);
    if(materialSlot>=0.0&&options.normalEnabled!=0&&
       (options.atlasMode==1||options.atlasMode==3)){
        vec2 enc=texture(diffuseSampler,normalUV).rg*2.0-1.0;
        enc=clamp(enc*clamp(options.reliefStrength,0.5,1.5),
                  vec2(-1.0),vec2(1.0));
        float z=sqrt(max(0.0,1.0-dot(enc,enc)));
        vec2 uvDx=dFdx(uv),uvDy=dFdy(uv);
        float det=uvDx.x*uvDy.y-uvDx.y*uvDy.x;
        if(abs(det)>1e-8&&nl>1e-7){
            vec3 tangent=pDx*uvDy.y-pDy*uvDx.y;
            tangent-=n*dot(tangent,n);
            float tl=length(tangent);
            if(tl>1e-7){
                vec3 t=(tangent/tl)*sign(det);
                vec3 b=normalize(cross(n,t))*(det<0.0?-1.0:1.0);
                n=normalize(t*enc.x+b*enc.y+n*z);
            }
        }
    }
    vec3 light=vec3(0.32,0.64,0.68);
    if(options.lightMode==1)light=vec3(0.06,0.20,0.98);
    else if(options.lightMode==2)light=vec3(-0.76,0.45,0.48);
    else if(options.lightMode==3)light=vec3(0.15,0.94,0.30);
    float d=abs(dot(n,normalize(light)));
    // The proven Adreno normal-safe path also respects diffuse cutouts.
    // Never discard before tangent-space derivatives.
    if(base.a < 0.05) discard;
    fragColor=vec4(base.rgb*(0.45+0.55*d),base.a);
}
