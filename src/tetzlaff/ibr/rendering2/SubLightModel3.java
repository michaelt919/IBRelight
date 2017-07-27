package tetzlaff.ibr.rendering2;//Created by alexk on 7/25/2017.

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.paint.Color;
import tetzlaff.gl.vecmath.Matrix4;
import tetzlaff.gl.vecmath.Vector3;
import tetzlaff.ibr.gui2.controllers.scene.lights.LightType;
import tetzlaff.ibr.gui2.controllers.scene.lights.SubLightSetting;
import tetzlaff.ibr.gui2.other.OrbitPolarConverter;
import tetzlaff.mvc.models.ControllableSubLightModel;

public class SubLightModel3 implements ControllableSubLightModel {

    private ObservableValue<SubLightSetting> subLightSettingObservableValue;
    private final SubLightSetting backup= new SubLightSetting(
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            true,
            "backup",
            LightType.PointLight,
            Color.BLACK,
            new SimpleBooleanProperty(true));

    public void setSubLightSettingObservableValue(ObservableValue<SubLightSetting> subLightSettingObservableValue){
        this.subLightSettingObservableValue = subLightSettingObservableValue;
    }

    private SubLightSetting cam(){
        if(subLightSettingObservableValue == null || subLightSettingObservableValue.getValue() == null){
//            System.out.println("Using SubLight Backup");
            return backup;
        }
        else {
//            System.out.println("Win");
            return subLightSettingObservableValue.getValue();
        }
    }

    private Matrix4 orbitCash;
    private boolean fromRender = false;
    @Override
    public Matrix4 getLookMatrix() {
        return Matrix4.lookAt(
                new Vector3(0,0,1/getZoom()),
                Vector3.ZERO,
                new Vector3(0,1,0)
        ).times(getOrbit().times(
                Matrix4.translate(getCenter())
        ));
    }

    @Override
    public Matrix4 getOrbit() {
        if(fromRender){
            fromRender = false;
            return orbitCash;
        }
        Vector3 poler = new Vector3((float) cam().getAzimuth(), (float) cam().getInclination(), 0 );
        return OrbitPolarConverter.self.convertLeft(poler);
    }

    @Override
    public void setOrbit(Matrix4 orbit) {
        Vector3 poler = OrbitPolarConverter.self.convertRight(orbit);
        cam().setAzimuth(poler.x);
        cam().setInclination(poler.y);
        orbitCash = orbit;
        fromRender = true;
    }

    @Override
    public Float getZoom() {
        return (float) cam().getDistance();
    }

    @Override
    public void setZoom(Float zoom) {
        cam().setDistance(zoom);
    }

    @Override
    public Vector3 getCenter() {
        return new Vector3((float) cam().getxCenter(),
                (float) cam().getyCenter(),
                (float) cam().getzCenter());
    }

    @Override
    public void setCenter(Vector3 center) {
        cam().setxCenter(center.x);
        cam().setyCenter(center.y);
        cam().setzCenter(center.z);
    }

    @Override
    public Double getTwist() {
        return 0.0;
    }

    @Override
    public void setTwist(Double twist) {
    }

    @Override
    public Double getAzimuth() {
        return cam().getAzimuth();
    }

    @Override
    public void setAzimuth(Double azimuth) {
        cam().setAzimuth(azimuth);
    }

    @Override
    public Double getInclination() {
        return cam().getInclination();
    }

    @Override
    public void setInclination(Double inclination) {
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
    public boolean getLocked() {
        return (cam().isLocked() || cam().getGroupLocked());
    }

    @Override
    public Vector3 getColor() {
        Color color = cam().getColor();
        Vector3 out = new Vector3((float) color.getRed(), (float) color.getBlue(), (float) color.getGreen());
//        System.out.println("Light Color: " + out);
        return out;
    }

    @Override
    public void setColor(Vector3 color) {
        cam().setColor(
                new Color(color.x, color.y, color.z, 1)
        );
    }
}
