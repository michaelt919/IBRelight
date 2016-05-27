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
package tetzlaff.window.listeners;

import tetzlaff.window.ModifierKeys;
import tetzlaff.window.Window;

/**
 * A listener for when a mouse button is pressed.
 * @author Michael Tetzlaff
 *
 */
public interface MouseButtonPressListener 
{
	/**
	 * Called when a mouse button is pressed.
	 * @param window The window responding to the event.
	 * @param buttonIndex The index of the button that was pressed.
	 * 0 is generally used for the left mouse button, 1 for the right, and 2 for the middle.
	 * @param mods The modifier keys active when the button was pressed.
	 */
	void mouseButtonPressed(Window window, int buttonIndex, ModifierKeys mods);
}
