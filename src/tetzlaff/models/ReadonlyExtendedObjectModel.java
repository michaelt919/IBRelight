package tetzlaff.models;

import tetzlaff.gl.vecmath.Matrix4;

public interface ReadonlyExtendedObjectModel extends ReadonlyObjectModel
{
    /**
     * This method is intended to return whether or not the selected camera is locked.
     * It is called by the render side of the program, and when it returns true
     * the camera should not be able to be changed using the tools in the render window.
     * @return true for locked
     */
    boolean isLocked();

    Matrix4 getOrbit();
    float getRotationZ();
    float getRotationY();
    float getRotationX();
}
