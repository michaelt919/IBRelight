/*
 *  Copyright (c) Michael Tetzlaff 2019
 *  Copyright (c) The Regents of the University of Minnesota 2019
 *
 *  Licensed under GPLv3
 *  ( http://www.gnu.org/licenses/gpl-3.0.html )
 *
 *  This code is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */

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
