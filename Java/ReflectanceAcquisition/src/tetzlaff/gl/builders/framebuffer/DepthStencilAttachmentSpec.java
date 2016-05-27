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
package tetzlaff.gl.builders.framebuffer;


public class DepthStencilAttachmentSpec extends AttachmentSpec
{
	/**
	 * Whether or not floating-point storage for the depth component.
	 */
	public final boolean floatingPointDepth;
	
	/**
	 * Creates a new depth+stencil attachment specification.
	 * @param floatingPoint Whether or not floating-point storage for the depth attachment.
	 */
	public DepthStencilAttachmentSpec(boolean floatingPointDepth)
	{
		this.floatingPointDepth = floatingPointDepth;
	}
	
	@Override
	public DepthStencilAttachmentSpec setMultisamples(int samples, boolean fixedSampleLocations)
	{
		super.setMultisamples(samples, fixedSampleLocations);
		return this;
	}

	@Override
	public DepthStencilAttachmentSpec setMipmapsEnabled(boolean enabled)
	{
		super.setMipmapsEnabled(enabled);
		return this;
	}
	
	@Override
	public DepthStencilAttachmentSpec setLinearFilteringEnabled(boolean enabled)
	{
		super.setLinearFilteringEnabled(enabled);
		return this;
	}
}
