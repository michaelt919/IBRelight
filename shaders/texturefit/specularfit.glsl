#ifndef SPECULARFIT_GLSL
#define SPECULARFIT_GLSL

#line 5 2004

#define MIN_ROUGHNESS  0.00390625    // 1/256
#define MAX_ROUGHNESS  0.70710678 // sqrt(1/2)

#define MIN_SPECULAR_REFLECTIVITY 0.04 // corresponds to dielectric with index of refraction = 1.5
#define MAX_ROUGHNESS_WHEN_CLAMPING 0.25

uniform sampler2D diffuseEstimate;
uniform sampler2D normalEstimate;

uniform float fittingGamma;
uniform bool standaloneMode;

#define chromaticRoughness false
#define chromaticSpecular true

vec4 getDiffuseColor()
{
    if (standaloneMode)
    {
        return vec4(0, 0, 0, 1);
    }
    else
    {
        vec4 textureResult = texture(diffuseEstimate, fTexCoord);
        return vec4(pow(textureResult.rgb, vec3(gamma)), textureResult.a);
    }
}

vec3 getDiffuseNormalVector()
{
    if (standaloneMode)
    {
        return vec3(0,0,1);
    }
    else
    {
        return normalize(texture(normalEstimate, fTexCoord).xyz * 2 - vec3(1,1,1));
    }
}

struct ParameterizedFit
{
    vec4 diffuseColor;
    vec4 normal;
    vec4 specularColor;
    vec4 roughness;
};

vec4 removeDiffuse(vec4 originalColor, vec3 diffuseColor, vec3 light,
                    vec3 attenuatedLightIntensity, vec3 normal, float maxLuminance)
{
    vec3 diffuseContrib = diffuseColor * max(0, dot(light, normal)) * attenuatedLightIntensity;

    if (chromaticSpecular)
    {
        float cap = maxLuminance - max(diffuseContrib.r, max(diffuseContrib.g, diffuseContrib.b));
        vec3 remainder = clamp(originalColor.rgb - diffuseContrib, 0, cap);
        return vec4(remainder, cap);
    }
    else
    {
        vec3 remainder = max(originalColor.rgb - diffuseContrib, vec3(0));
        float remainderMin = min(min(remainder.r, remainder.g), remainder.b);
        return vec4(vec3(remainderMin), 1.0);
    }
}

ParameterizedFit fitSpecular()
{
    vec3 normal = normalize(fNormal);

    vec3 tangent = normalize(fTangent - dot(normal, fTangent));
    vec3 bitangent = normalize(fBitangent
        - dot(normal, fBitangent) * normal
        - dot(tangent, fBitangent) * tangent);

    mat3 tangentToObject = mat3(tangent, bitangent, normal);
    vec3 shadingNormalTS = getDiffuseNormalVector();
    vec3 shadingNormal = tangentToObject * shadingNormalTS;

    vec4 diffuseColor = getDiffuseColor();

    float maxLuminance = getMaxLuminance();
    vec3 maxResidual = vec3(0);
    vec2 maxResidualLuminance = vec2(0);
    vec3 attenuatedLightIntensityAtMax = vec3(0);
    //vec3 maxResidualDirection = vec3(0);

    vec3 directionSum = vec3(0);
    vec3 intensityWeightedDirectionSum = vec3(0);

    for (int i = 0; i < viewCount; i++)
    {
        vec4 color = getLinearColor(i);
        vec3 view = normalize(getViewVector(i));

        if (color.a * dot(view, normal) > 0)
        {
            vec3 lightPreNormalized = getLightVector(i);
            vec3 attenuatedLightIntensity = infiniteLightSources ?
                getLightIntensity(i) :
                getLightIntensity(i) / (dot(lightPreNormalized, lightPreNormalized));
            vec3 light = normalize(lightPreNormalized);

            vec3 colorRemainder =
                removeDiffuse(color, diffuseColor.rgb, light, attenuatedLightIntensity, shadingNormal, maxLuminance).rgb / attenuatedLightIntensity;
            float luminance = getLuminance(colorRemainder);

            vec3 halfway = normalize(view + light);
            float nDotH = dot(normal, halfway);

            if (nDotH * nDotH > 0.5)
            {
                directionSum += halfway;
                intensityWeightedDirectionSum += halfway * luminance;
            }

            if (luminance * nDotH > maxResidualLuminance[0] * maxResidualLuminance[1])
            {
                maxResidualLuminance = vec2(luminance, nDotH);
                maxResidual = colorRemainder;
                attenuatedLightIntensityAtMax = attenuatedLightIntensity;
            }
        }
    }

    vec3 maxResidualXYZ = rgbToXYZ(maxResidual);

    vec3 specularNormal;

    if (dot(intensityWeightedDirectionSum, intensityWeightedDirectionSum) < 1.0)
    {
        intensityWeightedDirectionSum += (1 - length(intensityWeightedDirectionSum)) * shadingNormal;
    }

    float directionScale = length(directionSum);
    vec3 averageDirection = directionSum / max(1, directionScale);
    float specularNormalFidelity = dot(averageDirection, normal);
    vec3 certaintyDirectionUnnormalized = cross(averageDirection - specularNormalFidelity * normal, normal);
    vec3 certaintyDirection = certaintyDirectionUnnormalized
        / max(1, length(certaintyDirectionUnnormalized));

    vec3 specularNormalEstimate = normalize(intensityWeightedDirectionSum);
    float specularNormalCertainty =
        min(1, directionScale) * dot(specularNormalEstimate, certaintyDirection);
    vec3 scaledCertaintyDirection = specularNormalCertainty * certaintyDirection;
    specularNormal = normalize(
        scaledCertaintyDirection
            + sqrt(1 - specularNormalCertainty * specularNormalCertainty
                        * dot(certaintyDirection, certaintyDirection))
                * normalize(mix(normal, normalize(specularNormalEstimate - scaledCertaintyDirection),
                    min(1, directionScale) * specularNormalFidelity)));

    vec3 roughnessSums[3];
    roughnessSums[0] = vec3(0);
    roughnessSums[1] = vec3(0);
    roughnessSums[2] = vec3(0);

    vec4 sumResidualXYZGamma = vec4(0.0);

    vec3 diffuseColorXYZ = rgbToXYZ(diffuseColor.rgb);
    vec4 adjustedDiffuseColorXYZ = vec4(0.0);

    for (int i = 0; i < viewCount; i++)
    {
        vec3 view = normalize(getViewVector(i));

        // Values of 1.0 for this color would correspond to the expected reflectance
        // for an ideal diffuse reflector (diffuse albedo of 1), which is a reflectance of 1 / pi.
        // Hence, this color corresponds to the reflectance times pi.
        // Both the Phong model and the Cook Torrance with a Beckmann distribution also have a 1/pi factor.
        // By adopting the convention that all reflectance values are scaled by pi in this shader,
        // We can avoid division by pi here as well as the 1/pi factors in the parameterized models.
        vec4 color = getLinearColor(i);

        if (color.a * dot(view, normal) > 0)
        {
            vec3 lightPreNormalized = getLightVector(i);
            vec3 attenuatedLightIntensity = infiniteLightSources ?
                getLightIntensity(i) :
                getLightIntensity(i) / (dot(lightPreNormalized, lightPreNormalized));
            vec3 light = normalize(lightPreNormalized);
            float nDotL = max(0, dot(light, specularNormal));
            float nDotV = max(0, dot(specularNormal, view));

            vec3 halfway = normalize(view + light);
            float nDotH = dot(halfway, specularNormal);
            float nDotHSquared = nDotH * nDotH;

            vec3 colorRemainderRGB =
                removeDiffuse(color, diffuseColor.rgb, light, attenuatedLightIntensity, normal, maxLuminance).rgb / attenuatedLightIntensity;
            vec3 colorRemainderXYZ = rgbToXYZ(colorRemainderRGB);

            if (nDotV > 0 /*&& nDotHSquared > 0.5 && nDotV * (1 + nDotHSquared) * (1 + nDotHSquared) > 1.0*/)
            {
                float hDotV = max(0, dot(halfway, view));

                vec3 sqrtFactor = sqrt(colorRemainderXYZ * nDotV);
                vec3 roughnessEstimate = sqrtFactor * (1 - nDotHSquared) / (sqrt(maxResidualXYZ) - sqrtFactor * nDotHSquared);
                vec3 clampedRoughnessSq = clamp(max(roughnessEstimate * roughnessEstimate, MIN_SPECULAR_REFLECTIVITY / (4 * maxResidualXYZ)),
                    MIN_ROUGHNESS * MIN_ROUGHNESS, MAX_ROUGHNESS_WHEN_CLAMPING * MAX_ROUGHNESS_WHEN_CLAMPING);
                vec3 adjustedMFDDenominatorRoot = (1 - nDotHSquared * (1 - clampedRoughnessSq));
                vec3 adjustedFactor = clampedRoughnessSq * clampedRoughnessSq * maxResidualXYZ
                    / (adjustedMFDDenominatorRoot * adjustedMFDDenominatorRoot);

                vec3 colorXYZ = rgbToXYZ(color.rgb);
                vec3 adjustedColorRemainder = clamp(adjustedFactor / nDotV, colorRemainderXYZ, colorXYZ);

                if (nDotL > 0)
                {
                    adjustedDiffuseColorXYZ += color.a * nDotL *
                        vec4(clamp(colorXYZ - adjustedColorRemainder, vec3(0), diffuseColorXYZ * nDotL), nDotL);
                }

                vec3 adjustedSqrtFactor = sqrt(adjustedColorRemainder * nDotV);

                roughnessSums[0] += nDotV // * pow(adjustedColorRemainder, vec3(1.0 / fittingGamma))
                    * adjustedSqrtFactor * (1 - nDotHSquared);

                roughnessSums[1] += nDotV // * pow(adjustedColorRemainder, vec3(1.0 / fittingGamma))
                    * adjustedSqrtFactor * nDotHSquared;

                roughnessSums[2] += nDotV ;// * pow(adjustedColorRemainder, vec3(1.0 / fittingGamma));

                sumResidualXYZGamma += nDotV * vec4(pow(adjustedColorRemainder, vec3(1.0 / fittingGamma)), 1.0);
            }
            else if (nDotL > 0)
            {
                adjustedDiffuseColorXYZ += color.a * nDotL * nDotL * vec4(diffuseColorXYZ, 1);
            }
        }
    }

    if (roughnessSums[2] == vec3(0.0) || sumResidualXYZGamma.w == 0.0 || adjustedDiffuseColorXYZ.w == 0.0)
    {
        return ParameterizedFit(diffuseColor, vec4(normalize(transpose(tangentToObject) * shadingNormal), 1), vec4(0), vec4(0));
    }

    vec3 adjustedMaxResidualXYZ = maxResidualXYZ
        + (diffuseColorXYZ - adjustedDiffuseColorXYZ.xyz / adjustedDiffuseColorXYZ.w) * attenuatedLightIntensityAtMax;

    // Estimate the roughness and specular reflectivity (in an XYZ color space) from the previous computations.
    vec3 roughnessSquared;
    vec3 specularColorXYZEstimate;

    //    Derivation:
    //    maxResidual.rgb = xyzToRGB(rgbToXYZ(specularColor) * 1.0 / roughnessSquared) / 4;
    //                    = xyzToRGB * (1.0 / roughnessSquared) * rgbToXYZ * specularColor / 4;
    //    rgbToXYZ * maxResidual.rgb = (1.0 / roughnessSquared) * rgbToXYZ * specularColor / 4;
    //    roughnessSquared * rgbToXYZ * maxResidual.rgb = rgbToXYZ * specularColor / 4;
    //    4 * xyzToRGB * roughnessSquared * rgbToXYZ * maxResidual.rgb = specularColor;
    if (chromaticRoughness)
    {
        roughnessSquared = clamp(roughnessSums[0] / max(vec3(0.0), sqrt(adjustedMaxResidualXYZ) * roughnessSums[2] - roughnessSums[1]),
            vec3(MIN_ROUGHNESS * MIN_ROUGHNESS), vec3(MAX_ROUGHNESS * MAX_ROUGHNESS));
        specularColorXYZEstimate = 4 * roughnessSquared * adjustedMaxResidualXYZ;
    }
    else
    {
         roughnessSquared = vec3(clamp(roughnessSums[0].y / max(0.0, sqrt(adjustedMaxResidualXYZ.y) * roughnessSums[2].y - roughnessSums[1].y),
             MIN_ROUGHNESS * MIN_ROUGHNESS, MAX_ROUGHNESS * MAX_ROUGHNESS));
         vec3 avgResidualXYZ = pow(sumResidualXYZGamma.xyz / sumResidualXYZGamma.w, vec3(fittingGamma));
         specularColorXYZEstimate = 4 * roughnessSquared * adjustedMaxResidualXYZ.y * avgResidualXYZ / max(0.001, avgResidualXYZ.y);

//        // Force monochrome roughness and reflectivity (for debugging)
//        vec3 specularColor = 4 * roughnessSquared * maxResidualLuminance[0];
    }

    // Convert the XYZ specular color to RGB, enforce monochrome constraints, and ensure a minimum specular reflectivity in cases where there may be
    // ambiguity between the specular and diffuse terms.
    // The roughness estimate may be updated as necessary for consistency.
    vec3 specularColor;

    if (diffuseColor.rgb != vec3(0.0) && MIN_SPECULAR_REFLECTIVITY > 0.0 && specularColorXYZEstimate.y < MIN_SPECULAR_REFLECTIVITY)
    {
        vec3 diffuseXYZ = rgbToXYZ(diffuseColor.rgb);

        if (chromaticRoughness)
        {
            vec3 specularColorBounded = max(specularColorXYZEstimate,
                min(min(MAX_ROUGHNESS_WHEN_CLAMPING * MAX_ROUGHNESS_WHEN_CLAMPING * (4 * maxResidualXYZ),
                        16 * diffuseXYZ * maxResidualXYZ + specularColorXYZEstimate),
                        vec3(MIN_SPECULAR_REFLECTIVITY)));

            roughnessSquared = clamp(specularColorBounded / (4 * maxResidualXYZ),
                vec3(MIN_ROUGHNESS * MIN_ROUGHNESS), vec3(MAX_ROUGHNESS_WHEN_CLAMPING * MAX_ROUGHNESS_WHEN_CLAMPING));

            specularColor = xyzToRGB(specularColorBounded);
        }
        else
        {
            if (chromaticSpecular)
            {
                vec3 specularColorBounded = max(specularColorXYZEstimate,
                    min(min(MAX_ROUGHNESS_WHEN_CLAMPING * MAX_ROUGHNESS_WHEN_CLAMPING * (4 * maxResidualXYZ),
                            16 * diffuseXYZ * maxResidualXYZ + specularColorXYZEstimate),
                            vec3(MIN_SPECULAR_REFLECTIVITY)));

                roughnessSquared = vec3(clamp(specularColorBounded.y / (4 * maxResidualLuminance[0]),
                    MIN_ROUGHNESS * MIN_ROUGHNESS, MAX_ROUGHNESS_WHEN_CLAMPING * MAX_ROUGHNESS_WHEN_CLAMPING));

                specularColor = xyzToRGB(specularColorBounded);
            }
            else
            {
                specularColor = vec3(max(specularColorXYZEstimate.y,
                    min(min(MAX_ROUGHNESS_WHEN_CLAMPING * MAX_ROUGHNESS_WHEN_CLAMPING * (4 * maxResidualLuminance[0]),
                            16 * diffuseXYZ.y * maxResidualLuminance[0] + specularColorXYZEstimate.y),
                            MIN_SPECULAR_REFLECTIVITY)));

                roughnessSquared = vec3(clamp(specularColor.g / (4 * maxResidualLuminance[0]),
                    MIN_ROUGHNESS * MIN_ROUGHNESS, MAX_ROUGHNESS_WHEN_CLAMPING * MAX_ROUGHNESS_WHEN_CLAMPING));
            }
        }
    }
    else
    {
        if (chromaticSpecular)
        {
            specularColor = xyzToRGB(specularColorXYZEstimate);
        }
        else
        {
            specularColor = vec3(specularColorXYZEstimate.y);
        }
    }

    // Refit the diffuse color using a simple linear regression after subtracting the final specular estimate.
    vec4 adjustedDiffuseColor;

    if (diffuseColor != vec4(0, 0, 0, 1))
    {
        vec4 sumDiffuse = vec4(0.0);

        for (int i = 0; i < viewCount; i++)
        {
            vec3 view = normalize(getViewVector(i));

            // Values of 1.0 for this color would correspond to the expected reflectance
            // for an ideal diffuse reflector (diffuse albedo of 1), which is a reflectance of 1 / pi.
            // Hence, this color corresponds to the reflectance times pi.
            // Both the Phong model and the Cook Torrance with a Beckmann distribution also have a 1/pi factor.
            // By adopting the convention that all reflectance values are scaled by pi in this shader,
            // We can avoid division by pi here as well as the 1/pi factors in the parameterized models.
            vec4 color = getLinearColor(i);

            if (color.a * dot(view, normal) > 0)
            {
                vec3 lightPreNormalized = getLightVector(i);
                vec3 attenuatedLightIntensity = infiniteLightSources ?
                    getLightIntensity(i) :
                    getLightIntensity(i) / (dot(lightPreNormalized, lightPreNormalized));
                vec3 light = normalize(lightPreNormalized);
                float nDotL = max(0, dot(light, specularNormal));
                float nDotV = max(0, dot(specularNormal, view));

                vec3 halfway = normalize(view + light);
                float nDotH = dot(halfway, specularNormal);
                float nDotHSquared = nDotH * nDotH;

                if (nDotV > 0 /*&& nDotHSquared > 0.5*/)
                {
                    vec3 q1 = roughnessSquared + (1.0 - nDotHSquared) / nDotHSquared;
                    vec3 mfdEval = roughnessSquared / (nDotHSquared * nDotHSquared * q1 * q1);

                    float hDotV = max(0, dot(halfway, view));
                    float geomRatio = min(1.0, 2.0 * nDotH * min(nDotV, nDotL) / hDotV) / (4 * nDotV);

                    vec3 specularTerm = min(vec3(1), rgbToXYZ(specularColor)) * mfdEval * geomRatio;
                    sumDiffuse += vec4(max(vec3(0.0), nDotL * (color.rgb / attenuatedLightIntensity - xyzToRGB(specularTerm))), nDotL * nDotL);
                }
            }
        }

        if (sumDiffuse.a > 0.0)
        {
            adjustedDiffuseColor = vec4(sumDiffuse.rgb / sumDiffuse.a, 1);
        }
        else
        {
            // Discard and hole fill
            adjustedDiffuseColor = vec4(0.0);
        }
    }
    else
    {
        adjustedDiffuseColor = vec4(0, 0, 0, 1);
    }

    // Dividing by the sum of weights to get the weighted average.
    // We'll put a lower cap of 1/m^2 on the alpha we divide by so that noise doesn't get amplified
    // for texels where there isn't enough information at the specular peak.
    return ParameterizedFit(adjustedDiffuseColor, vec4(normalize(transpose(tangentToObject) * specularNormal), 1),
        vec4(specularColor, 1), vec4(sqrt(roughnessSquared), 1));
}

#endif // SPECULARFIT_GLSL
