package tetzlaff.ibr;

public class IBRSettings 
{
    private float gamma = 2.2f;
    private float weightExponent = 16.0f;
    private float isotropyFactor = 0.5f;
    private boolean occlusionEnabled = true;
    private float occlusionBias = 0.0025f;
	private boolean ibrEnabled = true;
	private boolean relightingEnabled = false;
	private boolean texturesEnabled = false;
	private boolean shadowsEnabled = false;
	private boolean fresnelEnabled = false;
	private boolean pbrGeometricAttenuationEnabled = false;
	private boolean visibleLightsEnabled = false;

	public IBRSettings() 
	{
	}

	public float getGamma() 
	{
		return this.gamma;
	}

	public void setGamma(float gamma) 
	{
		this.gamma = gamma;
	}

	public float getWeightExponent() 
	{
		return this.weightExponent;
	}

	public void setWeightExponent(float weightExponent) 
	{
		this.weightExponent = weightExponent;
	}

	public float getIsotropyFactor()
	{
		return isotropyFactor;
	}

	public void setIsotropyFactor(float isotropyFactor) 
	{
		this.isotropyFactor = isotropyFactor;
	}

	public boolean isOcclusionEnabled() 
	{
		return this.occlusionEnabled;
	}

	public void setOcclusionEnabled(boolean occlusionEnabled) 
	{
		this.occlusionEnabled = occlusionEnabled;
	}

	public float getOcclusionBias() 
	{
		return this.occlusionBias;
	}

	public void setOcclusionBias(float occlusionBias) 
	{
		this.occlusionBias = occlusionBias;
	}

	public boolean isIBREnabled() 
	{
		return this.ibrEnabled;
	}

	public void setIBREnabled(boolean ibrEnabled) 
	{
		this.ibrEnabled = ibrEnabled;
	}

	public boolean isFresnelEnabled() 
	{
		return this.fresnelEnabled;
	}

	public void setFresnelEnabled(boolean fresnelEnabled) 
	{
		this.fresnelEnabled = fresnelEnabled;
	}

	public boolean isPBRGeometricAttenuationEnabled() 
	{
		return this.pbrGeometricAttenuationEnabled;
	}

	public void setPBRGeometricAttenuationEnabled(boolean pbrGeometricAttenuationEnabled) 
	{
		this.pbrGeometricAttenuationEnabled = pbrGeometricAttenuationEnabled;
	}

	public boolean isRelightingEnabled() 
	{
		return relightingEnabled;
	}

	public void setRelightingEnabled(boolean relightingEnabled) 
	{
		this.relightingEnabled = relightingEnabled;
	}

	public boolean areTexturesEnabled() 
	{
		return texturesEnabled;
	}

	public void setTexturesEnabled(boolean texturesEnabled) 
	{
		this.texturesEnabled = texturesEnabled;
	}

	public boolean areShadowsEnabled() 
	{
		return shadowsEnabled;
	}

	public void setShadowsEnabled(boolean shadowsEnabled) 
	{
		this.shadowsEnabled = shadowsEnabled;
	}
	
	public boolean areVisibleLightsEnabled()
	{
		return this.visibleLightsEnabled;
	}

	public void setVisibleLightsEnabled(boolean visibleLightsEnabled) 
	{
		this.visibleLightsEnabled = visibleLightsEnabled;
	}
}
