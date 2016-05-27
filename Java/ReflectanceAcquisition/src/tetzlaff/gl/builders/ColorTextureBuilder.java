/*
 * LF Viewer - A tool to render Agisoft PhotoScan models as light fields.
 *
 * Copyright (c) 2016
 * The Regents of the University of Minnesota
 *     and
 * Cultural Heritage Imaging
 * All rights reserved
 *
 * This file is part of LF Viewer.
 *
 *     LF Viewer is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     LF Viewer is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with LF Viewer.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package tetzlaff.gl.builders;

import tetzlaff.gl.ColorFormat;
import tetzlaff.gl.CompressionFormat;
import tetzlaff.gl.Context;
import tetzlaff.gl.Texture;

/**
 * Implements the builder design pattern for creating color textures.
 * @author Michael Tetzlaff
 *
 * @param <ContextType> The type of the GL context that the texture will be associated with.
 * @param <TextureType> The type of texture to be created.
 */
public interface ColorTextureBuilder<ContextType extends Context<ContextType>, TextureType extends Texture<ContextType>> extends TextureBuilder<ContextType, TextureType>
{
	/**
	 * Sets the internal format to an uncompressed format.
	 * @param format The uncompressed color format to use.
	 * @return The calling builder object.
	 */
	ColorTextureBuilder<ContextType, TextureType> setInternalFormat(ColorFormat format);
	
	/**
	 * Sets the internal format to an compressed format.
	 * @param format The compression format to use.
	 * @return The calling builder object.
	 */
	ColorTextureBuilder<ContextType, TextureType> setInternalFormat(CompressionFormat format);
}
