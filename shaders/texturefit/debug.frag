#version 330

in vec3 fPosition;
in vec2 fTexCoord;
in vec3 fNormal;
in vec3 fTangent;
in vec3 fBitangent;

#include "../colorappearance/linearize.glsl"

#line 12 0

layout(location = 0) out vec4 diffuseColor;
layout(location = 1) out vec4 normal;
layout(location = 2) out vec4 specularColor;
layout(location = 3) out vec4 roughness;

void main()
{
    diffuseColor = vec4(pow(xyzToRGB(vec3(0.0)), vec3(1.0 / 2.2)), 1.0);
    normal = vec4(0.5, 0.5, 1.0, 1.0);
    specularColor = vec4(pow(sqrt(fTexCoord.xyx) / 4, vec3(1.0 / 2.2)), 1.0);
    roughness = vec4(vec3(sqrt(rgbToXYZ(sqrt(fTexCoord.xyx) / 2))), 1.0);
}