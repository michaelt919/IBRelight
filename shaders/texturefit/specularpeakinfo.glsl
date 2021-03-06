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

#ifndef PREPROCESSINFO_GLSL
#define PREPROCESSINFO_GLSL

#line 17 2007

#ifndef CHROMATIC_SPECULAR
#define CHROMATIC_SPECULAR 1
#endif

uniform sampler2D diffuseEstimate;
uniform sampler2D normalEstimate;

vec4 getDiffuseColor()
{
    vec4 textureResult = texture(diffuseEstimate, fTexCoord);
    return vec4(pow(textureResult.rgb, vec3(gamma)), textureResult.a);
}

vec3 getDiffuseNormalVector()
{
    return normalize(texture(normalEstimate, fTexCoord).xyz * 2 - vec3(1,1,1));
}

vec4 removeDiffuse(vec4 originalColor, vec3 diffuseColor, vec3 light,
                    vec3 attenuatedLightIntensity, vec3 normal, float maxLuminance)
{
    vec3 diffuseContrib = diffuseColor * max(0, dot(light, normal)) * attenuatedLightIntensity;

#if CHROMATIC_SPECULAR
    float cap = maxLuminance - max(diffuseContrib.r, max(diffuseContrib.g, diffuseContrib.b));
    vec3 remainder = clamp(originalColor.rgb - diffuseContrib, 0, cap);
    return vec4(remainder, cap);
#else
    vec3 remainder = max(originalColor.rgb - diffuseContrib, vec3(0));
    float remainderMin = min(min(remainder.r, remainder.g), remainder.b);
    return vec4(vec3(remainderMin), 1.0);
#endif
}

struct SpecularPeakInfo
{
    vec4 peak;
    vec4 offPeakSum;
    float maxNDotH;
    vec3 threshold;
    float peakNDotV;
};

SpecularPeakInfo computeSpecularPeakInfo()
{
    SpecularPeakInfo result;

    vec3 normal = normalize(fNormal);
    vec3 tangent = normalize(fTangent - dot(normal, fTangent) * normal);
    vec3 bitangent = normalize(fBitangent
        - dot(normal, fBitangent) * normal
        - dot(tangent, fBitangent) * tangent);

    mat3 tangentToObject = mat3(tangent, bitangent, normal);
    vec3 diffuseNormalTS = getDiffuseNormalVector();
    vec3 oldDiffuseNormal = tangentToObject * diffuseNormalTS;

    vec4 diffuseColor = getDiffuseColor();
    float maxLuminance = getMaxLuminance();

    for (int i = 0; i < VIEW_COUNT; i++)
    {
        vec4 color = getLinearColor(i);
        vec3 lightDisp = getLightVector(i);
        LightInfo lightInfo = getLightInfo(i);
        vec3 colorAdj =
            removeDiffuse(color, diffuseColor.rgb, lightInfo.normalizedDirection, lightInfo.attenuatedIntensity, oldDiffuseNormal, maxLuminance).rgb
                / lightInfo.attenuatedIntensity;

        vec3 view = normalize(getViewVector(i));
        float nDotV = max(0, dot(normal, view));
        float nDotH = max(0, dot(normal, normalize(lightInfo.normalizedDirection + view)));
        float offPeakWeight = 1 - nDotH * nDotH;
        vec4 colorWeighted = getCameraWeight(i) * offPeakWeight * offPeakWeight * nDotV * color.a * vec4(colorAdj, 1);

        result.offPeakSum += colorWeighted;

        vec4 candidate = sign(max(0, nDotV - 0.5) * color.a) * vec4(colorAdj, 1);

        if (getLuminance(candidate.rgb) > getLuminance(result.peak.rgb))
        {
            result.threshold = result.peak.rgb;
            result.peak = candidate;
            result.peakNDotV = nDotV;
        }
        else if (getLuminance(candidate.rgb) > getLuminance(result.threshold))
        {
            result.threshold = candidate.rgb;
        }

        result.maxNDotH = max(result.maxNDotH, nDotH);
    }

    result.offPeakSum = pow(result.offPeakSum / result.offPeakSum.a, vec4(vec3(1.0 / 2.2), 1.0));

#if SINGLE_VIEW_MASKING_ENABLED
    LightInfo lightInfo = getLightInfo();
    vec4 color = getLinearColor();
    vec3 colorAdj =
        removeDiffuse(color, diffuseColor, lightInfo.normalizedDirection, lightInfo.attenuatedIntensity, oldDiffuseNormal, maxLuminance).rgb
            / lightInfo.attenuatedIntensity;

    vec3 view = normalize(getViewVector());
    float nDotV = dot(normal, view);
    result.peak *= max(0, sign(max(0, nDotV - 0.5) * color.a * max(0, getLuminance(colorAdj) - getLuminance(result.peak.rgb) + 0.001)));
#endif

    return result;
}

#endif // PREPROCESSINFO_GLSL