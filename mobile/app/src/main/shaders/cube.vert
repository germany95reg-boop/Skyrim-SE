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
layout(location=0) out vec3 edgeColor;
layout(location=1) out vec3 viewPosition;
void main() {
    float cy=cos(params.angle),sy=sin(params.angle);
    float cp=cos(params.pitch),sp=sin(params.pitch);
    vec3 rotated=vec3(cy*inPosition.x+sy*inPosition.z,
                      inPosition.y,-sy*inPosition.x+cy*inPosition.z);
    vec3 v=vec3(rotated.x,cp*rotated.y-sp*rotated.z,sp*rotated.y+cp*rotated.z);
    viewPosition=v;
    float z=v.z+params.distance;
    gl_Position=vec4(3.2*v.x/max(params.aspect,0.2),-3.2*v.y+params.focusOffsetY*z,z-0.5,z);
    float fade=mod(float(gl_VertexIndex/2),11.0)/11.0;
    edgeColor=mix(vec3(0.72,0.49,0.99),vec3(0.34,0.85,1.0),fade);
}
