package tetzlaff.ibr;//Created by alexk on 7/31/2017.

import tetzlaff.util.ShadingParameterMode;

public interface IBRSettingsModel extends ReadonlyIBRSettingsModel
{
    void setGamma(float gamma);
    void setWeightExponent(float weightExponent);
    void setIsotropyFactor(float isotropyFactor);
    void setOcclusionEnabled(boolean occlusionEnabled);
    void setOcclusionBias(float occlusionBias);
    void setIBREnabled(boolean ibrEnabled);
    void setFresnelEnabled(boolean fresnelEnabled);
    void setPBRGeometricAttenuationEnabled(boolean pBRGeometricAttenuation);
    void setRelightingEnabled(boolean relightingEnabled);
    void setD3GridEnabled(boolean d3GridEnabled);
    void setCompassEnabled(boolean compassEnabled);
    void setVisibleCameraPose(boolean visibleCameraPose);
    void setVisibleSavedCameraPose(boolean visibleSavedCameraPose);
    void setMaterialsForIBR(boolean materialsForIBR);
    void setPhyMasking(boolean phyMasking);
    void setTexturesEnabled(boolean texturesEnabled);
    void setShadowsEnabled(boolean shadowsEnabled);
    void setVisibleLightsEnabled(boolean visibleLightsEnabled);
    void setRenderingMode(RenderingMode renderingMode);
    void setWeightMode(ShadingParameterMode weightMode);
    void setHalfResolutionEnabled(boolean halfResEnabled);
	void setMultisamplingEnabled(boolean multisamplingEnabled);
}
