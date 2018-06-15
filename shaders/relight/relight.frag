#version 330
#extension GL_ARB_texture_query_lod : enable

in vec3 fPosition;
in vec2 fTexCoord;
in vec3 fNormal;
in vec3 fTangent;
in vec3 fBitangent;

layout(location = 0) out vec4 fragColor;
layout(location = 1) out int fragObjectID;

#ifndef BRDF_MODE
#define BRDF_MODE 0
#endif

#ifndef SVD_MODE
#define SVD_MODE 0
#endif

#ifndef BUEHLER_ALGORITHM
#define BUEHLER_ALGORITHM 1
#endif

#ifndef USE_HEAPSORT
#define USE_HEAPSORT 1
#endif

#ifndef SORTING_SAMPLE_COUNT
#define SORTING_SAMPLE_COUNT 5
#endif

#ifndef RELIGHTING_ENABLED
#define RELIGHTING_ENABLED 1
#endif

#ifndef SPOTLIGHTS_ENABLED
#define SPOTLIGHTS_ENABLED 0
#endif

#ifndef SHADOWS_ENABLED
#define SHADOWS_ENABLED 0
#endif

#ifndef IMAGE_BASED_RENDERING_ENABLED
#define IMAGE_BASED_RENDERING_ENABLED 0
#endif

#ifndef FRESNEL_EFFECT_ENABLED
#define FRESNEL_EFFECT_ENABLED 0
#endif

#ifndef PHYSICALLY_BASED_MASKING_SHADOWING
#define PHYSICALLY_BASED_MASKING_SHADOWING 0
#endif

#ifndef ENVIRONMENT_ILLUMINATION_ENABLED
#define ENVIRONMENT_ILLUMINATION_ENABLED 1
#endif

#ifndef PRECOMPUTED_VIEW_WEIGHTS_ENABLED
#define PRECOMPUTED_VIEW_WEIGHTS_ENABLED 0
#endif

#ifndef VIRTUAL_LIGHT_COUNT
#if RELIGHTING_ENABLED
#define VIRTUAL_LIGHT_COUNT 4
#else
#define VIRTUAL_LIGHT_COUNT 1
#endif
#endif

#ifndef RESIDUAL_IMAGES
#define RESIDUAL_IMAGES SVD_MODE
#endif

#ifndef TANGENT_SPACE_OVERRIDE_ENABLED
#define TANGENT_SPACE_OVERRIDE_ENABLED 0
#endif

#ifndef DIFFUSE_TEXTURE_ENABLED
#define DIFFUSE_TEXTURE_ENABLED 0
#endif

#ifndef NORMAL_TEXTURE_ENABLED
#define NORMAL_TEXTURE_ENABLED 0
#endif

#ifndef SPECULAR_TEXTURE_ENABLED
#define SPECULAR_TEXTURE_ENABLED 0
#endif

#ifndef ROUGHNESS_TEXTURE_ENABLED
#define ROUGHNESS_TEXTURE_ENABLED 0
#endif

#ifndef TANGENT_SPACE_NORMAL_MAP
#define TANGENT_SPACE_NORMAL_MAP 1
#endif

#ifndef ARCHIVING_2017_ENVIRONMENT_NORMALIZATION
#define ARCHIVING_2017_ENVIRONMENT_NORMALIZATION 0
#endif

#ifndef DEFAULT_DIFFUSE_COLOR
#if !SPECULAR_TEXTURE_ENABLED && !IMAGE_BASED_RENDERING_ENABLED
#define DEFAULT_DIFFUSE_COLOR (vec3(0.125))
#else
#define DEFAULT_DIFFUSE_COLOR (vec3(0.0))
#endif // !SPECULAR_TEXTURE_ENABLED && !IMAGE_BASED_RENDERING_ENABLED
#endif // DEFAULT_DIFFUSE_COLOR

#ifndef DEFAULT_SPECULAR_COLOR
#if DIFFUSE_TEXTURE_ENABLED && !IMAGE_BASED_RENDERING_ENABLED
#define DEFAULT_SPECULAR_COLOR (vec3(0.0))
#else
#define DEFAULT_SPECULAR_COLOR (vec3(0.03125))
#endif // DIFFUSE_TEXTURE_ENABLED && !IMAGE_BASED_RENDERING_ENABLED
#endif // DEFAULT_SPECULAR_COLOR

#ifndef DEFAULT_SPECULAR_ROUGHNESS
#define DEFAULT_SPECULAR_ROUGHNESS (vec3(0.25)); // TODO pass in a default?
#endif

#if SVD_MODE
#ifdef SMITH_MASKING_SHADOWING
#undef SMITH_MASKING_SHADOWING
#endif
#define SMITH_MASKING_SHADOWING 1
#endif

#ifndef MIPMAPS_ENABLED
#define MIPMAPS_ENABLED !BUEHLER_ALGORITHM
#endif

#include "reflectanceequations.glsl"
#include "tonemap.glsl"

#if RELIGHTING_ENABLED && ENVIRONMENT_ILLUMINATION_ENABLED
#include "environment.glsl"
#endif

#if SPECULAR_TEXTURE_ENABLED
uniform sampler2D specularMap;
#endif

uniform vec3 viewPos;
uniform mat4 envMapMatrix;

#if IMAGE_BASED_RENDERING_ENABLED

#include "../colorappearance/colorappearance.glsl"

#if SVD_MODE
#include "../colorappearance/svd_unpack.glsl"
#else
#include "../colorappearance/imgspace.glsl"
#endif

#if SVD_MODE && RELIGHTING_ENABLED && ENVIRONMENT_ILLUMINATION_ENABLED
//#include "env_svd_alt.glsl"
#include "env_svd_unpack.glsl"
#endif

#if BUEHLER_ALGORITHM
#define SORTING_TOTAL_COUNT VIEW_COUNT
#include "sort.glsl"
#endif

#endif

#line 172 0

uniform int objectID;
uniform mat4 model_view;
uniform vec3 holeFillColor;

#if VIRTUAL_LIGHT_COUNT > 0

uniform vec3 lightIntensityVirtual[VIRTUAL_LIGHT_COUNT];

#if RELIGHTING_ENABLED
uniform vec3 lightPosVirtual[VIRTUAL_LIGHT_COUNT];
uniform vec3 lightOrientationVirtual[VIRTUAL_LIGHT_COUNT];

#if SPOTLIGHTS_ENABLED
uniform float lightSpotSizeVirtual[VIRTUAL_LIGHT_COUNT];
uniform float lightSpotTaperVirtual[VIRTUAL_LIGHT_COUNT];
#endif // SPOTLIGHTS_ENABLED

#if SHADOWS_ENABLED
uniform sampler2DArray shadowMaps;
uniform mat4 lightMatrixVirtual[VIRTUAL_LIGHT_COUNT];
#endif // SHADOWS_ENABLED

#endif // RELIGHTING_ENABLED

#endif // VIRTUAL_LIGHT_COUNT > 0

#if !BUEHLER_ALGORITHM
uniform float weightExponent;
uniform float isotropyFactor;
#endif

#if DIFFUSE_TEXTURE_ENABLED
uniform sampler2D diffuseMap;
#endif

#if NORMAL_TEXTURE_ENABLED
uniform sampler2D normalMap;
#endif

#if ROUGHNESS_TEXTURE_ENABLED
uniform sampler2D roughnessMap;
#endif

#if TANGENT_SPACE_OVERRIDE_ENABLED
uniform vec3 lightDirTSOverride;
uniform vec3 viewDirTSOverride;
#endif

#if PRECOMPUTED_VIEW_WEIGHTS_ENABLED
layout(std140) uniform ViewWeights
{
    vec4 viewWeights[VIEW_COUNT_DIV_4];
};

float getViewWeight(int viewIndex)
{
    return extractComponentByIndex(viewWeights[viewIndex/4], viewIndex%4);
}
#endif

#if IMAGE_BASED_RENDERING_ENABLED

#if RESIDUAL_IMAGES

vec4 getSampleFromResidual(vec4 residual, vec3 peak)
{
    return residual.a * vec4(peak * (residual.rgb + vec3(0.5)), 1.0);
}

#else

vec4 removeDiffuse(vec4 originalColor, vec3 diffuseContrib, float nDotL, float maxLuminance)
{
    if (nDotL == 0.0)
    {
        return vec4(0);
    }
    else
    {
        float cap = maxLuminance - max(diffuseContrib.r, max(diffuseContrib.g, diffuseContrib.b));
        vec3 remainder = clamp(originalColor.rgb - diffuseContrib, 0, cap);
        return vec4(remainder, originalColor.a);
    }
}

#endif

#if RELIGHTING_ENABLED && ENVIRONMENT_ILLUMINATION_ENABLED

vec4 computeEnvironmentSample(int virtualIndex, vec3 diffuseColor, vec3 normalDir,
    vec3 specularColor, float roughness, vec3 peak, float maxLuminance)
{
    mat4 cameraPose = getCameraPose(virtualIndex);
    vec3 fragmentPos = (cameraPose * vec4(fPosition, 1.0)).xyz;
    vec3 normalDirCameraSpace = normalize((cameraPose * vec4(normalDir, 0.0)).xyz);
    vec3 sampleViewDir = normalize(-fragmentPos);
    float nDotV_sample = max(0, dot(normalDirCameraSpace, sampleViewDir));

    if (nDotV_sample <= 0.0)
    {
        return vec4(0.0, 0.0, 0.0, 0.0);
    }
    else
    {
        // All in camera space
        vec3 sampleLightDirUnnorm = lightPositions[getLightIndex(virtualIndex)].xyz - fragmentPos;
        float lightDistSquared = dot(sampleLightDirUnnorm, sampleLightDirUnnorm);
        vec3 sampleLightDir = sampleLightDirUnnorm * inversesqrt(lightDistSquared);
        vec3 sampleHalfDir = normalize(sampleViewDir + sampleLightDir);
        vec3 lightIntensity = getLightIntensity(virtualIndex);

#if !INFINITE_LIGHT_SOURCES
        lightIntensity /= lightDistSquared;
#endif

        float nDotL_sample = max(0, dot(normalDirCameraSpace, sampleLightDir));
        float nDotH = max(0, dot(normalDirCameraSpace, sampleHalfDir));
        float hDotV_sample = max(0, dot(sampleHalfDir, sampleViewDir));

        vec3 diffuseContrib = diffuseColor * nDotL_sample * lightIntensity;

        float geomAttenSample = geom(roughness, nDotH, nDotV_sample, nDotL_sample, hDotV_sample);

        if (nDotL_sample > 0.0 && geomAttenSample > 0.0)
        {
            vec3 virtualViewDir =
                normalize((cameraPose * vec4(viewPos, 1.0)).xyz - fragmentPos);
            vec3 virtualLightDir = -reflect(virtualViewDir, sampleHalfDir);
            float nDotL_virtual = max(0, dot(normalDirCameraSpace, virtualLightDir));
            float nDotV_virtual = max(0.125, dot(normalDirCameraSpace, virtualViewDir));
            float hDotV_virtual = max(0, dot(sampleHalfDir, virtualViewDir));

            float geomAttenVirtual = geom(roughness, nDotH, nDotV_virtual, nDotL_virtual, hDotV_virtual);


            vec3 mfdFresnel;
            float mfdMono;

#if RESIDUAL_IMAGES
            vec4 residual = getResidual(virtualIndex);
            if (residual.w > 0)
            {
                mfdFresnel = getSampleFromResidual(residual, peak).rgb * nDotV_sample / geomAttenSample / PI;
            }
#else

            vec4 sampleColor = getLinearColor(virtualIndex);

            if (sampleColor.a == 0.0)
            {
//                return vec4(0.0);
                mfdFresnel = dist(nDotH, sqrt(specularColor / peak)) * specularColor / PI;
            }
            else
            {
                vec4 specularResid = removeDiffuse(sampleColor, diffuseContrib, nDotL_sample, maxLuminance);

                // Light intensities in view set files are assumed to be pre-divided by pi.
                // Or alternatively, the result of getLinearColor gives a result
                // where a diffuse reflectivity of 1 is represented by a value of pi.
                // See diffusefit.glsl
                vec3 mfdFresnelGeom = specularResid.rgb / (lightIntensity * PI);

#if PHYSICALLY_BASED_MASKING_SHADOWING
                mfdFresnel = mfdFresnelGeom * 4 * nDotV_sample / geomAttenSample;
#else
                mfdFresnel = mfdFresnelGeom * 4 / nDotL_sample;
#endif
            }

#endif // RESIDUAL_IMAGES

            mfdMono = getLuminance(mfdFresnel / specularColor);

            vec3 mfdNewFresnel;
#if FRESNEL_EFFECT_ENABLED
            mfdNewFresnel = fresnel(mfdFresnel, vec3(mfdMono), hDotV_virtual);
#else
            mfdNewFresnel = mfdFresnel;
#endif

            vec4 unweightedSample;
            unweightedSample.rgb = mfdNewFresnel
                * geomAttenVirtual / (4 * nDotV_virtual)
                * getEnvironment(mat3(envMapMatrix) * transpose(mat3(cameraPose)) * virtualLightDir);

#if SPECULAR_TEXTURE_ENABLED && ARCHIVING_2017_ENVIRONMENT_NORMALIZATION
            // Normalizes with respect to specular texture when available as described in our Archiving 2017 paper.
            unweightedSample.a = mfdMono * geomAttenVirtual / (4 * nDotV_virtual);
#else
            unweightedSample.a = 1.0 / (2.0 * PI);
#endif
            return unweightedSample * 4 * hDotV_virtual * (getCameraWeight(virtualIndex) * 4 * PI * VIEW_COUNT);
            // dl = 4 * h dot v * dh
            // weight * VIEW_COUNT -> brings weights back to being on the order of 1
            // This is helpful for consistency with numerical limits (i.e. clamping)
            // Everything gets normalized at the end again anyways.
        }
        else
        {
            return vec4(0.0, 0.0, 0.0, 0.0);
        }
    }
}

vec3 getEnvironmentShading(vec3 diffuseColor, vec3 normalDir, vec3 specularColor, float roughness, vec3 peak)
{
    float maxLuminance = getMaxLuminance();

    vec4 sum = vec4(0.0);

    for (int i = 0; i < VIEW_COUNT; i++)
    {
        sum += computeEnvironmentSample(i, diffuseColor, normalDir, specularColor, roughness, peak, maxLuminance);
    }

    if (sum.a > 0.0)
    {
        return sum.rgb
            / VIEW_COUNT;                    // better spatial consistency, worse directional consistency?
        //    / clamp(sum.a, 0, 1000000.0);    // Better directional consistency, worse spatial consistency?
    }
    else
    {
        return vec3(0.0);
    }
}

#endif // RELIGHTING_ENABLED && ENVIRONMENT_ILLUMINATION_ENABLED

#if BUEHLER_ALGORITHM

vec4 computeSampleSingle(int virtualIndex, vec3 diffuseColor, vec3 normalDir,  vec3 specularColor, float roughness, vec3 peak, float maxLuminance)
{
    // All in camera space
    mat4 cameraPose = getCameraPose(virtualIndex);
    vec3 fragmentPos = (cameraPose * vec4(fPosition, 1.0)).xyz;
    vec3 sampleViewDir = normalize(-fragmentPos);
    vec3 sampleLightDirUnnorm = lightPositions[getLightIndex(virtualIndex)].xyz - fragmentPos;
    float lightDistSquared = dot(sampleLightDirUnnorm, sampleLightDirUnnorm);
    vec3 sampleLightDir = sampleLightDirUnnorm * inversesqrt(lightDistSquared);
    vec3 sampleHalfDir = normalize(sampleViewDir + sampleLightDir);
    vec3 normalDirCameraSpace = normalize((cameraPose * vec4(normalDir, 0.0)).xyz);
    float nDotH = max(0, dot(normalDirCameraSpace, sampleHalfDir));
    float nDotL = max(0, dot(normalDirCameraSpace, sampleLightDir));
    float nDotV = max(0, dot(normalDirCameraSpace, sampleViewDir));
    float hDotV = max(0, dot(sampleHalfDir, sampleViewDir));

    vec4 precomputedSample = vec4(0);

    if (nDotL > 0 && nDotV > 0)
    {
        float geomAtten = geom(roughness, nDotH, nDotV, nDotL, hDotV);

#if RESIDUAL_IMAGES
        vec4 residual = getResidual(virtualIndex);
        if (residual.w > 0)
        {
            return getSampleFromResidual(residual, peak) * vec4(vec3(nDotV), geomAtten);
        }
#else
        vec4 sampleColor = getLinearColor(virtualIndex);
        if (sampleColor.a > 0.0)
        {
            vec3 lightIntensity = getLightIntensity(virtualIndex);

#if !INFINITE_LIGHT_SOURCES
            lightIntensity /= lightDistSquared;
#endif

            vec3 diffuseContrib = diffuseColor * nDotL * lightIntensity;

#if PHYSICALLY_BASED_MASKING_SHADOWING
            vec4 specularResid = removeDiffuse(sampleColor, diffuseContrib, nDotL, maxLuminance);
            return sampleColor.a * vec4(specularResid.rgb * 4 * nDotV / lightIntensity, geomAtten);
#else
            vec4 specularResid = removeDiffuse(sampleColor, diffuseContrib, nDotL, maxLuminance);
            return sampleColor.a * vec4(specularResid.rgb * 4 / lightIntensity, nDotL);
#endif
        }
#endif // RESIDUAL_IMAGES
    }

    return vec4(0.0);
}

vec4 computeBuehler(vec3 targetDirection, vec3 diffuseColor, vec3 normalDir, vec3 specularColor, float roughness, vec3 peak)
{
    float maxLuminance = getMaxLuminance();

    float weights[SORTING_SAMPLE_COUNT];
    int indices[SORTING_SAMPLE_COUNT];

    sort(targetDirection, weights, indices);

    // Evaluate the light field
    // weights[0] should be the smallest weight
    vec4 sum = vec4(0.0);
    for (int i = 1; i < SORTING_SAMPLE_COUNT; i++)
    {
        vec4 computedSample = computeSampleSingle(indices[i], diffuseColor, normalDir, specularColor, roughness, peak, maxLuminance);
        if (computedSample.a > 0)
        {
            sum += (weights[i] - weights[0]) * computedSample;
        }
    }

    if (sum.a == 0.0)
    {
        return vec4(holeFillColor, 1.0);
    }
    else
    {
        return sum / sum.a;
    }
}

#elif VIRTUAL_LIGHT_COUNT > 0

vec4[VIRTUAL_LIGHT_COUNT] computeSample(int virtualIndex, vec3 diffuseColor, vec3 normalDir, vec3 specularColor, float roughness, vec3 peak, float maxLuminance)
{
    // All in camera space
    mat4 cameraPose = getCameraPose(virtualIndex);
    vec3 fragmentPos = (cameraPose * vec4(fPosition, 1.0)).xyz;
    vec3 sampleViewDir = normalize(-fragmentPos);
    vec3 sampleLightDirUnnorm = lightPositions[getLightIndex(virtualIndex)].xyz - fragmentPos;
    float lightDistSquared = dot(sampleLightDirUnnorm, sampleLightDirUnnorm);
    vec3 sampleLightDir = sampleLightDirUnnorm * inversesqrt(lightDistSquared);
    vec3 sampleHalfDir = normalize(sampleViewDir + sampleLightDir);
    vec3 normalDirCameraSpace = normalize((cameraPose * vec4(normalDir, 0.0)).xyz);
    float nDotH = max(0, dot(normalDirCameraSpace, sampleHalfDir));
    float nDotL = max(0, dot(normalDirCameraSpace, sampleLightDir));
    float nDotV = max(0, dot(normalDirCameraSpace, sampleViewDir));
    float hDotV = max(0, dot(sampleHalfDir, sampleViewDir));

    vec4 precomputedSample = vec4(0);

    float geomAtten = geom(roughness, nDotH, nDotV, nDotL, hDotV);

#if RESIDUAL_IMAGES
    vec4 residual = getResidual(virtualIndex);
    if (residual.w > 0)
    {
        precomputedSample = getSampleFromResidual(residual, peak) * vec4(vec3(nDotV), geomAtten);
    }
#else
    vec4 sampleColor = getLinearColor(virtualIndex);
    if (sampleColor.a > 0.0)
    {
        vec3 lightIntensity = getLightIntensity(virtualIndex);

#if !INFINITE_LIGHT_SOURCES
        lightIntensity /= lightDistSquared;
#endif

        vec3 diffuseContrib = diffuseColor * nDotL * lightIntensity;

#if PHYSICALLY_BASED_MASKING_SHADOWING
        if (geomAtten > 0.0)
        {
            vec4 specularResid = removeDiffuse(sampleColor, diffuseContrib, nDotL, maxLuminance);
            precomputedSample = sampleColor.a
                * vec4(specularResid.rgb * 4 * nDotV / lightIntensity, geomAtten);

        }
#else
        if (nDotL != 0.0)
        {
            vec4 specularResid = removeDiffuse(sampleColor, diffuseContrib, nDotL, maxLuminance);
            precomputedSample = sampleColor.a
                * vec4(specularResid.rgb * 4 / lightIntensity, nDotL);
        }
#endif
    }
#endif // RESIDUAL_IMAGES

    if (precomputedSample.w != 0)
    {
        mat3 tangentToObject = mat3(1.0);

        vec3 virtualViewDir;
#if TANGENT_SPACE_OVERRIDE_ENABLED
        vec3 gNormal = normalize(fNormal);
        vec3 tangent = normalize(fTangent - dot(gNormal, fTangent));
        vec3 bitangent = normalize(fBitangent
            - dot(gNormal, fBitangent) * gNormal
            - dot(tangent, fBitangent) * tangent);
        tangentToObject = mat3(tangent, bitangent, gNormal);

        virtualViewDir = normalize(mat3(cameraPose) * tangentToObject * viewDirTSOverride);
#else
        virtualViewDir = normalize((cameraPose * vec4(viewPos, 1.0)).xyz - fragmentPos);
#endif

        vec4 result[VIRTUAL_LIGHT_COUNT];

        for (int lightPass = 0; lightPass < VIRTUAL_LIGHT_COUNT; lightPass++)
        {
#if PRECOMPUTED_VIEW_WEIGHTS_ENABLED
            result[lightPass] = getViewWeight(virtualIndex) * precomputedSample;
#else

            vec3 virtualLightDir;
#if TANGENT_SPACE_OVERRIDE_ENABLED
            virtualLightDir = normalize(mat3(cameraPose) * tangentToObject * lightDirTSOverride);
#elif RELIGHTING_ENABLED
            virtualLightDir = normalize((cameraPose * vec4(lightPosVirtual[lightPass], 1.0)).xyz - fragmentPos);
#else
            virtualLightDir = virtualViewDir;
#endif

            // Compute sample weight
            vec3 virtualHalfDir = normalize(virtualViewDir + virtualLightDir);
            float virtualNdotH = max(0, dot(normalDirCameraSpace, virtualHalfDir));
            float correlation = isotropyFactor * (nDotH * virtualNdotH + sqrt(1 - nDotH*nDotH) * sqrt(1 - virtualNdotH*virtualNdotH))
                + (1 - isotropyFactor) * dot(virtualHalfDir, sampleHalfDir);
            float weight = 1.0 / max(0.000001, 1.0 - pow(max(0.0, correlation), weightExponent)) - 1.0;
            result[lightPass] = weight * precomputedSample;

#endif // PRECOMPUTED_VIEW_WEIGHTS_ENABLED
        }

        return result;
    }
    else
    {
        vec4 result[VIRTUAL_LIGHT_COUNT];
        for (int lightPass = 0; lightPass < VIRTUAL_LIGHT_COUNT; lightPass++)
        {
            result[lightPass] = vec4(0.0);
        }
        return result;
    }
}

vec4[VIRTUAL_LIGHT_COUNT] computeWeightedAverages(vec3 diffuseColor, vec3 normalDir, vec3 specularColor, float roughness, vec3 peak)
{
    float maxLuminance = getMaxLuminance();

    vec4[VIRTUAL_LIGHT_COUNT] sums;
    for (int i = 0; i < VIRTUAL_LIGHT_COUNT; i++)
    {
        sums[i] = vec4(0.0);
    }

    for (int i = 0; i < VIEW_COUNT; i++)
    {
        vec4[VIRTUAL_LIGHT_COUNT] microfacetSample =
            computeSample(i, diffuseColor, normalDir, specularColor, roughness, peak, maxLuminance);

        for (int j = 0; j < VIRTUAL_LIGHT_COUNT; j++)
        {
            sums[j] += microfacetSample[j];
        }
    }

    vec4[VIRTUAL_LIGHT_COUNT] results;
    for (int i = 0; i < VIRTUAL_LIGHT_COUNT; i++)
    {
#if PRECOMPUTED_VIEW_WEIGHTS_ENABLED
        results[i] = sums[i];
#else
        if (sums[i].a > 0.0)
        {
            results[i] = vec4(sums[i].rgb / max(0.01, sums[i].a), sums[i].a);
        }
        else
        {
            results[i] = vec4(0.0);
        }
#endif
    }
    return results;
}

#endif // BUEHLER_ALGORITHM

#endif // IMAGE_BASED_RENDERING_ENABLED

void main()
{
//#if IMAGE_BASED_RENDERING_ENABLED && SVD_MODE && RELIGHTING_ENABLED && ENVIRONMENT_ILLUMINATION_ENABLED
//    fragColor =
////        vec4(fNormal * 0.5 + 0.5, 1.0);
//        textureLod(environmentWeightsTexture, vec3(fTexCoord, 0), 0);
//    return;
//#endif

    vec3 viewDir;
#if TANGENT_SPACE_OVERRIDE_ENABLED
    viewDir = viewDirTSOverride;
#else
    viewDir = normalize(viewPos - fPosition);
#endif

    vec3 triangleNormal = normalize(fNormal);

#if (TANGENT_SPACE_NORMAL_MAP && NORMAL_TEXTURE_ENABLED) || TANGENT_SPACE_OVERRIDE_ENABLED
    vec3 tangent = normalize(fTangent - dot(triangleNormal, fTangent) * triangleNormal);
    vec3 bitangent = normalize(fBitangent
        - dot(triangleNormal, fBitangent) * triangleNormal
        - dot(tangent, fBitangent) * tangent);
    mat3 tangentToObject = mat3(tangent, bitangent, triangleNormal);
#endif

    vec3 normalDir;
#if NORMAL_TEXTURE_ENABLED
#if TANGENT_SPACE_NORMAL_MAP
    vec2 normalDirXY = texture(normalMap, fTexCoord).xy * 2 - vec2(1.0);
    vec3 normalDirTS = vec3(normalDirXY, sqrt(1 - dot(normalDirXY, normalDirXY)));
    normalDir = tangentToObject * normalDirTS;
#else
    normalDir = normalDirTS;
#endif // TANGENT_SPACE_NORMAL_MAP
#else
    normalDir = triangleNormal;
#endif // NORMAL_TEXTURE_ENABLED

    vec3 diffuseColor;
#if DIFFUSE_TEXTURE_ENABLED
    diffuseColor = pow(texture(diffuseMap, fTexCoord).rgb, vec3(gamma));
#else
    diffuseColor = DEFAULT_DIFFUSE_COLOR;
#endif

    vec3 specularColor;
#if SPECULAR_TEXTURE_ENABLED
    specularColor = pow(texture(specularMap, fTexCoord).rgb, vec3(gamma));
#else
    specularColor = DEFAULT_SPECULAR_COLOR;
#endif


    vec3 roughnessRGB;
#if ROUGHNESS_TEXTURE_ENABLED
    vec3 roughnessLookup = texture(roughnessMap, fTexCoord).rgb;
    vec3 sqrtRoughness = vec3(
        roughnessLookup.g + roughnessLookup.r - 16.0 / 31.0,
        roughnessLookup.g,
        roughnessLookup.g + roughnessLookup.b - 16.0 / 31.0);
    roughnessRGB = sqrtRoughness * sqrtRoughness;
#else
    roughnessRGB = DEFAULT_SPECULAR_ROUGHNESS;
#endif

    vec3 roughnessRGBSq = roughnessRGB * roughnessRGB;
    float roughnessSq = getLuminance(specularColor) / (getLuminance(specularColor / roughnessRGBSq));
    float roughness = sqrt(roughnessSq);

    float nDotV;
    float nDotV_triangle;
#if TANGENT_SPACE_OVERRIDE_ENABLED
    nDotV = viewDir.z;
    nDotV_triangle = viewDir.z;
#else
    nDotV = max(0.0, dot(normalDir, viewDir));
    nDotV_triangle = max(0.0, dot(triangleNormal, viewDir));
#endif

    if (nDotV_triangle == 0.0)
    {
        fragColor = vec4(0, 0, 0, 1);
        return;
    }

    vec3 radiance = vec3(0.0);

#if RELIGHTING_ENABLED && ENVIRONMENT_ILLUMINATION_ENABLED
    radiance += diffuseColor * getEnvironmentDiffuse((envMapMatrix * vec4(normalDir, 0.0)).xyz);

#if IMAGE_BASED_RENDERING_ENABLED

#if SVD_MODE
    radiance += getScaledEnvironmentShadingFromSVD(specularColor, roughnessRGB)
        / (max(0.125, nDotV) * roughnessRGBSq);
#else
    radiance += getEnvironmentShading(diffuseColor, normalDir, specularColor, roughness, specularColor / roughnessRGBSq);
#endif

#else

    vec3 reflectivity = min(vec3(1.0), diffuseColor + specularColor);

#if FRESNEL_EFFECT_ENABLED
    radiance += fresnel(ambientColor * reflectivity, ambientColor, nDotV);
#else
    radiance += ambientColor * reflectivity;
#endif // FRESNEL_EFFECT_ENABLED

#endif // IMAGE_BASED_RENDERING_ENABLED

#endif // RELIGHTING_ENABLED && ENVIRONMENT_ILLUMINATION_ENABLED

#if VIRTUAL_LIGHT_COUNT > 0

#if IMAGE_BASED_RENDERING_ENABLED && !BUEHLER_ALGORITHM
    vec4[VIRTUAL_LIGHT_COUNT] weightedAverages = computeWeightedAverages(diffuseColor, normalDir, specularColor, roughness, specularColor / roughnessRGBSq);
#endif

    for (int i = 0; i < VIRTUAL_LIGHT_COUNT; i++)
    {
        vec3 lightDirUnNorm;
        vec3 lightDir;
        float nDotL;
#if TANGENT_SPACE_OVERRIDE_ENABLED
        lightDirUnNorm = lightDir = lightDirTSOverride;
        nDotL = max(0.0, lightDir.z);
#elif RELIGHTING_ENABLED
        lightDirUnNorm = lightPosVirtual[i] - fPosition;
        lightDir = normalize(lightDirUnNorm);
        nDotL = max(0.0, dot(normalDir, lightDir));
#else
        lightDirUnNorm = viewPos - fPosition;
        lightDir = viewDir;
        nDotL = max(0.0, dot(normalDir, viewDir));
#endif

        if (nDotL > 0.0 && dot(triangleNormal, lightDir) > 0.0)
        {
#if RELIGHTING_ENABLED && SHADOWS_ENABLED && !TANGENT_SPACE_OVERRIDE_ENABLED
            vec4 projTexCoord = lightMatrixVirtual[i] * vec4(fPosition, 1.0);
            projTexCoord /= projTexCoord.w;
            projTexCoord = (projTexCoord + vec4(1)) / 2;

            if (projTexCoord.x >= 0 && projTexCoord.x <= 1
                && projTexCoord.y >= 0 && projTexCoord.y <= 1
                && projTexCoord.z >= 0 && projTexCoord.z <= 1
                && texture(shadowMaps, vec3(projTexCoord.xy, i)).r - projTexCoord.z >= -0.01)
#endif
            {
                vec3 halfDir = normalize(viewDir + lightDir);
                float hDotV = dot(halfDir, viewDir);

                float nDotH;
#if TANGENT_SPACE_OVERRIDE_ENABLED
                nDotH = halfDir.z;
#else
                nDotH = dot(normalDir, halfDir);
#endif

                float nDotHSq = max(0, nDotH) * max(0, nDotH);

                vec4 predictedMFD;

#if IMAGE_BASED_RENDERING_ENABLED

#if BUEHLER_ALGORITHM
                vec3 targetDirection;

#if TANGENT_SPACE_OVERRIDE_ENABLED
                targetDirection = tangentToObject * halfDir;
#else
                targetDirection = halfDir;
#endif // TANGENT_SPACE_OVERRIDE_ENABLED

                vec4 weightedAverage = computeBuehler(targetDirection, diffuseColor, normalDir, specularColor, roughness, specularColor / roughnessRGBSq);
                predictedMFD = weightedAverage;

#else
                predictedMFD = weightedAverages[i];

#endif // BUEHLER_ALGORITHM

#endif // IMAGE_BASED_RENDERING_ENABLED

                if (predictedMFD.w < 1.0)
                {
                    predictedMFD.rgb += (1 - predictedMFD.w) * holeFillColor;
                }

                vec3 mfdFresnel;

#if RELIGHTING_ENABLED && FRESNEL_EFFECT_ENABLED

#if IMAGE_BASED_RENDERING_ENABLED
                float grazingIntensity = getLuminance(predictedMFD.rgb
                    / max(vec3(1 / predictedMFD.a), specularColor));

                if (grazingIntensity <= 0.0)
                {
                    mfdFresnel = vec3(0,0,0);
                }
                else
                {
                    mfdFresnel = max(vec3(0.0),
                        fresnel(predictedMFD.rgb, vec3(grazingIntensity), hDotV));
                        // fresnel(predictedMFD.rgb, vec3(dist(nDotH, roughnessRGB)), hDotV));
                }
#else
                vec3 mfdFresnelBase = specularColor * dist(nDotH, roughnessRGB);
                mfdFresnel = fresnel(mfdFresnelBase, vec3(getLuminance(mfdFresnelBase) / getLuminance(specularColor)), hDotV);
#endif // IMAGE_BASED_RENDERING_ENABLED

#elif IMAGE_BASED_RENDERING_ENABLED
                mfdFresnel = max(vec3(0.0), predictedMFD.rgb);
#else
                mfdFresnel = specularColor * dist(nDotH, roughnessRGB);
#endif // RELIGHTING_ENABLED && FRESNEL_EFFECT_ENABLED

                vec3 lightVectorTransformed = (model_view * vec4(lightDirUnNorm, 0.0)).xyz;

                vec3 pointRadiance;

                vec3 reflectance = nDotL * diffuseColor;

#if PHYSICALLY_BASED_MASKING_SHADOWING
                reflectance += mfdFresnel * geom(roughness, nDotH, nDotV, nDotL, hDotV) / (4 * nDotV);
#else
                reflectance += mfdFresnel * nDotL / 4;
#endif

#if RELIGHTING_ENABLED
                vec3 irradiance = lightIntensityVirtual[i];

#if !TANGENT_SPACE_OVERRIDE_ENABLED
                irradiance /= dot(lightVectorTransformed, lightVectorTransformed);

#if SPOTLIGHTS_ENABLED
                float lightDirCorrelation = max(0.0, dot(lightDir, -lightOrientationVirtual[i]));
                float spotBoundaryDistance = lightSpotSizeVirtual[i] - sqrt(1 - lightDirCorrelation * lightDirCorrelation);
                irradiance *= clamp(
                    spotBoundaryDistance / max(0.001, max(lightSpotSizeVirtual[i] * lightSpotTaperVirtual[i], spotBoundaryDistance)),
                    0.0, 1.0);
#endif // RELIGHTING_ENABLED && SPOTLIGHTS_ENABLED

#endif // !TANGENT_SPACE_OVERRIDE_ENABLED

                pointRadiance = reflectance * irradiance;
#else
                pointRadiance = reflectance;
#endif

#if BRDF_MODE
                radiance += pointRadiance / nDotL;
#else
                radiance += pointRadiance;
#endif
            }
        }
    }

#endif // VIRTUAL_LIGHT_COUNT > 0

    fragColor = tonemap(radiance, 1.0);

    fragObjectID = objectID;
}
