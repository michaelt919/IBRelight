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
package tetzlaff.gl.exceptions;

/**
 * Thrown when any error related to GLFW occurs.
 * @author Michael Tetzlaff
 *
 */
public class GLFWException extends RuntimeException 
{
	private static final long serialVersionUID = -9045950594133572264L;

	public GLFWException() 
	{
	}

	public GLFWException(String message) 
	{
		super(message);
	}

	public GLFWException(Throwable cause) 
	{
		super(cause);
	}

	public GLFWException(String message, Throwable cause) 
	{
		super(message, cause);
	}

	public GLFWException(String message, Throwable cause,
			boolean enableSuppression, boolean writableStackTrace) 
	{
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
