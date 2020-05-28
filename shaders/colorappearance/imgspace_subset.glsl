/*
 * Copyright (c) Michael Tetzlaff 2019
 * Copyright (c) The Regents of the University of Minnesota 2019
 *
 * Licensed under GPLv3
 * ( http://www.gnu.org/licenses/gpl-3.0.html )
 *
 * This code is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */

#ifndef IMGSPACE_SUBSET_GLSL
#define IMGSPACE_SUBSET_GLSL

#include "colorappearance_subset.glsl"

#line 7 1121

#define MAX_CAMERA_PROJECTION_COUNT 1024

#ifndef SUPPRESS_MIPMAPS
#define SUPPRESS_MIPMAPS 0
#endif

uniform sampler2DArray viewImages;
uniform sampler2DArray depthImages;
uniform sampler2DArray shadowImages;

uniform bool occlusionEnabled;
uniform bool shadowTestEnabled;
uniform float occlusionBias;

layout(std140) uniform CameraProjections
{
    mat4 cameraProjections[MAX_CAMERA_PROJECTION_COUNT];
};

layout(std140) uniform CameraProjectionIndices
{
    ivec4 cameraProjectionIndices[MAX_CAMERA_POSE_COUNT_DIV_4];
};

layout(std140) uniform ShadowMatrices
{
    mat4 shadowMatrices[MAX_CAMERA_POSE_COUNT];
};

int getCameraProjectionIndex(int virtualIndex)
{
    int viewIndex = getViewIndex(virtualIndex);
    return extractComponentByIndex(cameraProjectionIndices[viewIndex/4], viewIndex%4);
}

vec4 getColor(int virtualIndex)
{
    int viewIndex = getViewIndex(virtualIndex);
    vec4 projTexCoord = cameraProjections[getCameraProjectionIndex(virtualIndex)] * cameraPoses[viewIndex] *
                            vec4(fPosition, 1.0);
    projTexCoord /= projTexCoord.w;
    projTexCoord = (projTexCoord + vec4(1)) / 2;

    if (projTexCoord.x < 0 || projTexCoord.x > 1 || projTexCoord.y < 0 || projTexCoord.y > 1)
    {
        return vec4(0);
    }
    else
    {
        if (projTexCoord.z >= 0 && projTexCoord.z <= 1)
        {
            if (occlusionEnabled)
            {
                float imageDepth = texture(depthImages, vec3(projTexCoord.xy, viewIndex)).r;
                if (abs(projTexCoord.z - imageDepth) > occlusionBias)
                {
                    // Occluded
                    return vec4(0);
                }
            }

            if (shadowTestEnabled)
            {
                vec4 shadowTexCoord = shadowMatrices[viewIndex] * vec4(fPosition, 1.0);
                shadowTexCoord /= shadowTexCoord.w;
                shadowTexCoord = (shadowTexCoord + vec4(1)) / 2;

                if (shadowTexCoord.x >= 0 && shadowTexCoord.x <= 1 &&
                     shadowTexCoord.y >= 0 && shadowTexCoord.y <= 1 &&
                     shadowTexCoord.z >= 0 && shadowTexCoord.z <= 1)
                {
                    float shadowImageDepth = texture(shadowImages, vec3(shadowTexCoord.xy, viewIndex)).r;
                    if (abs(shadowTexCoord.z - shadowImageDepth) > occlusionBias)
                    {
                        // Occluded
                        return vec4(0);
                    }
                }
            }
        }

#if SUPPRESS_MIPMAPS
        return textureLod(viewImages, vec3(projTexCoord.xy, viewIndex), 0);
#else
        return texture(viewImages, vec3(projTexCoord.xy, viewIndex));
#endif
    }
}

#endif // IMGSPACE_SUBSET_GLSL
