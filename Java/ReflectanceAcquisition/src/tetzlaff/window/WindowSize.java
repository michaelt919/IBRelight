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
package tetzlaff.window;

/**
 * A class for representing the position of a window.
 * @author Michael Tetzlaff
 *
 */
public class WindowSize 
{
	/**
	 * The width of the window, in logical pixels.
	 */
	public final int width;
	
	/**
	 * The height of the window, in logical pixels.
	 */
	public final int height;
	
	/**
	 * Creates a new object for representing the size of a window.
	 * @param width The width of the window.
	 * @param height The height of the window.
	 */
	public WindowSize(int width, int height) 
	{
		this.width = width;
		this.height = height;
	}
}
