package tetzlaff.gl.window.listeners;

import tetzlaff.gl.window.Window;

public interface CharacterListener 
{
	void characterTyped(Window<?> window, char c);
}