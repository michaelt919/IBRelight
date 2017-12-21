package tetzlaff.gl.builders;

import tetzlaff.gl.core.Context;
import tetzlaff.gl.core.Texture;

public interface TextureBuilder<ContextType extends Context<ContextType>, TextureType extends Texture<ContextType>>
{
    TextureBuilder<ContextType, TextureType> setMultisamples(int samples, boolean fixedSampleLocations);
    TextureBuilder<ContextType, TextureType> setMipmapsEnabled(boolean enabled);
    TextureBuilder<ContextType, TextureType> setLinearFilteringEnabled(boolean enabled);
    TextureBuilder<ContextType, TextureType> setMaxAnisotropy(float maxAnisotropy);

    TextureType createTexture();
}
