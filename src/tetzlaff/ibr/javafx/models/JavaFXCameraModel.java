package tetzlaff.ibr.javafx.models;//Created by alexk on 7/21/2017.

import com.sun.istack.internal.NotNull;
import javafx.beans.value.ObservableValue;
import tetzlaff.gl.vecmath.Matrix4;
import tetzlaff.gl.vecmath.Vector3;
import tetzlaff.ibr.javafx.controllers.scene.camera.CameraSetting;
import tetzlaff.models.ExtendedCameraModel;
import tetzlaff.util.OrbitPolarConverter;

public class JavaFXCameraModel implements ExtendedCameraModel
{

    private ObservableValue<CameraSetting> selected;
    private final CameraSetting backup = new CameraSetting(
        0.0,
        0.0,
        0.0,
        0.0,
        0.0,
        1.0,
        0.0,
        1.0,
        1.0,
        true,
        false,
        "backup"
    );

    public void setSelectedCameraSettingProperty(ObservableValue<CameraSetting> selectedCameraSettingProperty)
    {
        this.selected = selectedCameraSettingProperty;
    }

    @NotNull
    private CameraSetting cam()
    {
        if (selected == null || selected.getValue() == null)
        {
            return backup;
        }
        else
        {
            return selected.getValue();
        }
    }

    private Matrix4 orbitCache;
    private boolean fromRender = false;

    @Override
    public Matrix4 getLookMatrix()
    {
        return Matrix4.lookAt(
            new Vector3(0, 0, getDistance()),
            Vector3.ZERO,
            new Vector3(0, 1, 0)
        ).times(getOrbit().times(
            Matrix4.translate(getCenter().negated())
        ));
    }

    @Override
    public Matrix4 getOrbit()
    {
        if (fromRender)
        {
            fromRender = false;
            return orbitCache;
        }
        Vector3 poler = new Vector3((float) cam().getAzimuth(), (float) cam().getInclination(), (float) cam().getTwist());
        return OrbitPolarConverter.getInstance().convertToOrbitMatrix(poler);
    }

    @Override
    public void setOrbit(Matrix4 orbit)
    {
        Vector3 poler = OrbitPolarConverter.getInstance().convertToPolarCoordinates(orbit);
        cam().setAzimuth(poler.x);
        cam().setInclination(poler.y);
        cam().setTwist(poler.z);
        orbitCache = orbit;
        fromRender = true;
    }

    @Override
    public float getLog10Distance()
    {
        return (float) cam().getLog10distance();
    }

    @Override
    public void setLog10Distance(float log10distance)
    {
        cam().setLog10distance(log10distance);
    }

    @Override
    public float getDistance()
    {
        return (float) Math.pow(10, cam().getLog10distance());
    }

    @Override
    public void setDistance(float distance)
    {
        cam().setLog10distance(Math.log10(distance));
    }

    @Override
    public Vector3 getCenter()
    {
        return new Vector3((float) cam().getxCenter(),
            (float) cam().getyCenter(),
            (float) cam().getzCenter());
    }

    @Override
    public void setCenter(Vector3 center)
    {
        cam().setxCenter(center.x);
        cam().setyCenter(center.y);
        cam().setzCenter(center.z);
    }

    @Override
    public float getTwist()
    {
        return (float) cam().getTwist();
    }

    @Override
    public void setTwist(float twist)
    {
        cam().setTwist(twist);
    }

    @Override
    public float getAzimuth()
    {
        return (float) cam().getAzimuth();
    }

    @Override
    public void setAzimuth(float azimuth)
    {
        cam().setAzimuth(azimuth);
    }

    @Override
    public float getInclination()
    {
        return (float) cam().getInclination();
    }

    @Override
    public void setInclination(float inclination)
    {
        cam().setInclination(inclination);
    }

    /**
     * this method is intended to return whether or not the selected camera is locked.
     * It is called by the render side of the program, and when it returns true
     * the camera should not be able to be changed using the tools in the render window.
     *
     * @return true for locked
     */
    @Override
    public boolean isLocked()
    {
        return cam().isLocked();
    }

    @Override
    public void setLookMatrix(Matrix4 lookMatrix)
    {
        throw new UnsupportedOperationException();
    }
}
