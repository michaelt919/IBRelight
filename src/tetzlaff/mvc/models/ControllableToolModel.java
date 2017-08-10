package tetzlaff.mvc.models;//Created by alexk on 7/24/2017.

import java.io.File;
import java.io.IOException;

import tetzlaff.gl.vecmath.Vector2;
import tetzlaff.gl.vecmath.Vector3;
import tetzlaff.ibr.IBRLoadOptions;
import tetzlaff.ibr.IBRRenderable;
import tetzlaff.ibr.rendering.ImageBasedRendererList;
import tetzlaff.ibr.rendering2.to_sort.IBRLoadOptions2;
import tetzlaff.ibr.rendering2.to_sort.IBRSettingsModel;
import tetzlaff.ibr.rendering2.tools2.ToolBox;

public abstract class ControllableToolModel {
    private IBRRenderable<?> ibrRenderable = null;
    protected abstract IBRSettingsModel getSettings();
    protected abstract IBRLoadOptions2 getLoadOptions();



    private ImageBasedRendererList<?> model;
    public final void setModel(ImageBasedRendererList<?> model) {
        this.model = model;
    }

    public final void loadFiles(File cameraFile, File objFile, File photoDir) throws IOException{



        ibrRenderable = model.addFromAgisoftXMLFile(cameraFile.getPath(), cameraFile, objFile, photoDir, getLoadOptions());

        ibrRenderable.setHalfResolution(true);

        ibrRenderable.setSettingsModel(getSettings());

    }

    public final void loadVset(File vsetFile) throws IOException{

        ibrRenderable = model.addFromVSETFile(vsetFile.getPath(), vsetFile, getLoadOptions());

        ibrRenderable.setHalfResolution(true);

        ibrRenderable.setSettingsModel(getSettings());
    }

    public final IBRRenderable<?> getIBRRenderable(){
        return ibrRenderable;
    }


    final void loadEV(File ev){
        model.getSelectedItem().setEnvironment(ev);
    }

    final void unloadEV(){
        model.getSelectedItem().setEnvironment(null);
    }

    public abstract ToolBox.TOOL getTool();

    public Vector3 getPoint(float x, float y){
        return model.getSelectedItem().getSceneViewportModel().get3DPositionAtCoordinates(x, y);
    }

    public Vector3 getPoint(Vector2 vector2){
        return getPoint(vector2.x, vector2.y);
    }

    public enum WHAT_CLICKED{
        OBJECT, LIGHT, OTHER
    }

    public WHAT_CLICKED whatClicked(float x, float y){
        model.getSelectedItem().getSceneViewportModel().getObjectAtCoordinates(x, y);

        return WHAT_CLICKED.OTHER;
    }
}
