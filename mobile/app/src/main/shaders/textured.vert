#version 450
layout(push_constant) uniform SceneParams {
    float angle;
    float aspect;
    float pixelWidth;
    float pixelHeight;
    float pitch;
    float distance;
    float focusOffsetY;
} params;
layout(location=0) in vec3 inPosition;
layout(location=1) in vec2 inUV;
layout(location=0) out vec2 uv;
layout(location=1) out vec3 viewPosition;
void main(){
    float cy=cos(params.angle),sy=sin(params.angle);
    float cp=cos(params.pitch),sp=sin(params.pitch);
    vec3 rotated=vec3(cy*inPosition.x+sy*inPosition.z,
                      inPosition.y,-sy*inPosition.x+cy*inPosition.z);
    vec3 v=vec3(rotated.x,cp*rotated.y-sp*rotated.z,sp*rotated.y+cp*rotated.z);
    viewPosition=v;
    uv=inUV; // SSE half-float UV has been decoded by Java into float32.
    float z=v.z+params.distance;
    gl_Position=vec4(3.2*v.x/max(params.aspect,0.2),
                     -3.2*v.y+params.focusOffsetY*z,z-0.5,z);
}
