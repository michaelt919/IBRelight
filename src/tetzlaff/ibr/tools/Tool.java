package tetzlaff.ibr.tools;//Created by alexk on 7/24/2017.

import tetzlaff.gl.window.ModifierKeys;
import tetzlaff.gl.window.Window;
import tetzlaff.gl.window.listeners.CursorPositionListener;
import tetzlaff.gl.window.listeners.KeyPressListener;
import tetzlaff.gl.window.listeners.MouseButtonPressListener;
import tetzlaff.gl.window.listeners.ScrollListener;

interface Tool extends CursorPositionListener, MouseButtonPressListener, ScrollListener, KeyPressListener
{
    @Override
    default void mouseButtonPressed(Window<?> window, int buttonIndex, ModifierKeys mods)
    {
    }

    @Override
    default void cursorMoved(Window<?> window, double xPos, double yPos)
    {
    }

    @Override
    default void scroll(Window<?> window, double xOffset, double yOffset)
    {
    }

    @Override
    default void keyPressed(Window<?> window, int keyCode, ModifierKeys mods)
    {
    }
}
