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
package tetzlaff.lightfield;

import tetzlaff.gl.helpers.Matrix4;

/**
 * An interface for a definition of 3D to 2D projection that can be expressed as a projective transformation matrix.
 * @author Michael Tetzlaff
 *
 */
public interface Projection 
{
	/**
	 * Gets the projective transformation matrix for this projection.
	 * @param nearPlane The plane in 3D Cartesian space that will get mapped to the plane z=1.
	 * @param farPlane The plane in 3D Cartesian space that will get mapped to the plane z=-1.
	 * @return The projective transformation matrix.
	 */
	Matrix4 getProjectionMatrix(float nearPlane, float farPlane);
	
	/**
	 * Convert to a string designed for use in a VSET file
	 */
	String toVSETString();
}