package tetzlaff.models;//Created by alexk on 7/25/2017.

import tetzlaff.gl.vecmath.Vector3;

public interface LightInstanceModel extends ReadonlyLightInstanceModel, ExtendedCameraModel
{
    void setColor(Vector3 color);
}
