package tetzlaff.ibr.core;

import javax.swing.*;

import tetzlaff.gl.Context;
import tetzlaff.models.ReadonlyCameraModel;
import tetzlaff.models.ReadonlyLightingModel;
import tetzlaff.models.ReadonlyObjectModel;

public interface IBRRenderableListModel<ContextType extends Context<ContextType>> extends ComboBoxModel<IBRRenderable<ContextType>>, LoadingHandler
{
    @Override
    IBRRenderable<ContextType> getSelectedItem();

    void setSettingsModel(ReadonlySettingsModel settingsModel);
    void setObjectModel(ReadonlyObjectModel objectModel);
    void setCameraModel(ReadonlyCameraModel cameraModel);
    void setLightingModel(ReadonlyLightingModel lightingModel);
}