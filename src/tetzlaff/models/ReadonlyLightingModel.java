package tetzlaff.models;

import tetzlaff.gl.vecmath.Matrix4;
import tetzlaff.gl.vecmath.Vector3;

public interface ReadonlyLightingModel 
{
    int getLightCount();
    boolean isLightVisualizationEnabled(int index);
    boolean isLightWidgetEnabled(int index);

    Vector3 getLightColor(int i);
    Vector3 getAmbientLightColor();
    boolean getEnvironmentMappingEnabled();
    Matrix4 getEnvironmentMapMatrix();
    Matrix4 getLightMatrix(int i);
    Vector3 getLightCenter(int i);
}