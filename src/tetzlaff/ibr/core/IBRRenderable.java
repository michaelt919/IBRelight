package tetzlaff.ibr.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

import tetzlaff.gl.Context;
import tetzlaff.gl.Framebuffer;
import tetzlaff.gl.Program;
import tetzlaff.gl.interactive.InteractiveRenderable;
import tetzlaff.gl.util.VertexGeometry;
import tetzlaff.gl.vecmath.Matrix4;
import tetzlaff.ibr.rendering.IBRResources;
import tetzlaff.models.ReadonlyCameraModel;
import tetzlaff.models.ReadonlyLightingModel;
import tetzlaff.models.ReadonlyObjectModel;
import tetzlaff.models.SceneViewportModel;

public interface IBRRenderable<ContextType extends Context<ContextType>> extends InteractiveRenderable<ContextType>
{
    void setLoadingMonitor(LoadingMonitor callback);

    ViewSet getActiveViewSet();
    VertexGeometry getActiveGeometry();

    SceneViewportModel getSceneViewportModel();

    ReadonlySettingsModel getSettingsModel();
    void setSettingsModel(ReadonlySettingsModel ibrSettingsModel);

    void setProgram(Program<ContextType> program);
    void reloadHelperShaders();

    void loadBackplate(File backplateFile);
    void loadEnvironmentMap(File environmentFile) throws FileNotFoundException;

    void setObjectModel(ReadonlyObjectModel objectModel);
    void setCameraModel(ReadonlyCameraModel cameraModel);
    void setLightingModel(ReadonlyLightingModel lightingModel);

    void setMultiTransformationModel(List<Matrix4> multiTransformationModel);
    void setReferenceScene(VertexGeometry scene);

    IBRResources<ContextType> getResources();

    void draw(Framebuffer<ContextType> framebuffer, Matrix4 view, Matrix4 projection);

    @Override
    default void draw(Framebuffer<ContextType> framebuffer)
    {
        draw(framebuffer, null, null);
    }
}