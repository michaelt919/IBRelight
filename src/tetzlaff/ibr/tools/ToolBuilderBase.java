package tetzlaff.ibr.tools;

import tetzlaff.models.ExtendedCameraModel;
import tetzlaff.models.ReadonlyEnvironmentMapModel;
import tetzlaff.models.ReadonlyLightingModel;
import tetzlaff.models.SceneViewportModel;

abstract class ToolBuilderBase<ToolType extends Tool> implements ToolBuilder<ToolType>
{
    private ToolSelectionModel toolSelectionModel;
    private ExtendedCameraModel cameraModel;
    private ReadonlyEnvironmentMapModel environmentMapModel;
    private ReadonlyLightingModel lightingModel;
    private SceneViewportModel sceneViewportModel;

    protected ToolBuilderBase()
    {
    }

    @Override
    public ToolBuilder<ToolType> setToolSelectionModel(ToolSelectionModel toolSelectionModel)
    {
        this.toolSelectionModel = toolSelectionModel;
        return this;
    }

    @Override
    public ToolBuilder<ToolType> setCameraModel(ExtendedCameraModel cameraModel)
    {
        this.cameraModel = cameraModel;
        return this;
    }

    @Override
    public ToolBuilder<ToolType> setEnvironmentMapModel(ReadonlyEnvironmentMapModel environmentMapModel)
    {
        this.environmentMapModel = environmentMapModel;
        return this;
    }

    @Override
    public ToolBuilder<ToolType> setLightingModel(ReadonlyLightingModel lightingModel)
    {
        this.lightingModel = lightingModel;
        return this;
    }

    @Override
    public ToolBuilder<ToolType> setSceneViewportModel(SceneViewportModel sceneViewportModel)
    {
        this.sceneViewportModel = sceneViewportModel;
        return this;
    }

    ToolSelectionModel getToolSelectionModel()
    {
        return toolSelectionModel;
    }

    ExtendedCameraModel getCameraModel()
    {
        return cameraModel;
    }

    ReadonlyEnvironmentMapModel getEnvironmentMapModel()
    {
        return environmentMapModel;
    }

    ReadonlyLightingModel getLightingModel()
    {
        return lightingModel;
    }

    SceneViewportModel getSceneViewportModel()
    {
        return sceneViewportModel;
    }
}
