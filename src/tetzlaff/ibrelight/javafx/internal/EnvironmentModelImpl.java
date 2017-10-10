package tetzlaff.ibrelight.javafx.internal;//Created by alexk on 7/28/2017.

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Objects;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.paint.Color;
import tetzlaff.gl.vecmath.Matrix4;
import tetzlaff.gl.vecmath.Vector3;
import tetzlaff.ibrelight.javafx.MultithreadModels;
import tetzlaff.ibrelight.javafx.controllers.scene.environment.EnvironmentSetting;
import tetzlaff.models.BackgroundMode;
import tetzlaff.models.EnvironmentModel;

public class EnvironmentModelImpl implements EnvironmentModel
{
    private ObservableValue<EnvironmentSetting> selected;

    private boolean environmentMapLoaded = false;
    private boolean backplateLoaded = false;

    public void setSelected(ObservableValue<EnvironmentSetting> selected)
    {
        this.selected = selected;
        this.selected.addListener(settingChange);
    }

    private boolean doesSelectedExist()
    {
        return selected != null && selected.getValue() != null;
    }

    @Override
    public Vector3 getEnvironmentColor()
    {
        if (doesSelectedExist())
        {
            EnvironmentSetting selectedEnvironment = selected.getValue();
            if (selectedEnvironment.isEnvUseColorEnabled() && (environmentMapLoaded || !selectedEnvironment.isEnvUseImageEnabled()))
            {
                Color color = selectedEnvironment.getEnvColor();
                return new Vector3((float) color.getRed(), (float) color.getGreen(), (float) color.getBlue())
                    .times((float) selectedEnvironment.getEnvIntensity());
            }
            else if (selectedEnvironment.isEnvUseImageEnabled() && environmentMapLoaded)
            {
                return new Vector3((float) selectedEnvironment.getEnvIntensity());
            }
            else
            {
                return Vector3.ZERO;
            }
        }
        else
        {
            return Vector3.ZERO;
        }
    }

    @Override
    public Vector3 getBackgroundColor()
    {
        if (doesSelectedExist())
        {
            EnvironmentSetting selectedEnvironment = selected.getValue();

            if (selectedEnvironment.isBPUseColorEnabled() && (backplateLoaded || !selectedEnvironment.isBPUseImageEnabled()))
            {
                Color color = selectedEnvironment.getBpColor();
                return new Vector3((float) color.getRed(), (float) color.getGreen(), (float) color.getBlue())
                    .times((float) selectedEnvironment.getBackgroundIntensity());
            }
            else if (selectedEnvironment.isBPUseImageEnabled() && backplateLoaded)
            {
                return new Vector3((float) selectedEnvironment.getBackgroundIntensity());
            }
            else if (selectedEnvironment.isEnvUseColorEnabled() && (environmentMapLoaded || !selectedEnvironment.isEnvUseImageEnabled()))
            {
                Color color = selectedEnvironment.getEnvColor();
                return new Vector3((float) color.getRed(), (float) color.getGreen(), (float) color.getBlue())
                    .times((float)(selectedEnvironment.getBackgroundIntensity() * selectedEnvironment.getEnvIntensity()));
            }
            else if (selectedEnvironment.isEnvUseImageEnabled() && environmentMapLoaded)
            {
                return new Vector3((float)(selectedEnvironment.getBackgroundIntensity() * selectedEnvironment.getEnvIntensity()));
            }
            else
            {
                return Vector3.ZERO;
            }
        }
        else
        {
            return Vector3.ZERO;
        }
    }

    private final ChangeListener<File> envFileChange = (observable, oldFile, newFile) ->
    {
        if (newFile != null && !Objects.equals(oldFile, newFile))
        {
            environmentMapLoaded = false;

            new Thread(() ->
            {
                try
                {
                    System.out.println("Loading environment map file " + newFile.getName());
                    MultithreadModels.getInstance().getLoadingModel().loadEnvironmentMap(newFile);
                }
                catch (FileNotFoundException e)
                {
                    e.printStackTrace();
                }

                if (doesSelectedExist())
                {
                    selected.getValue().setEnvLoaded(true);
                }

                environmentMapLoaded = true;

            }).start();
        }
    };

    private final ChangeListener<File> bpFileChange = (observable, oldFile, newFile) ->
    {
        if (newFile != null && !Objects.equals(oldFile, newFile))
        {
            backplateLoaded = false;

            new Thread(() ->
            {
                try
                {
                    System.out.println("Loading backplate file " + newFile.getName());
                    MultithreadModels.getInstance().getLoadingModel().loadBackplate(newFile);
                }
                catch (FileNotFoundException e)
                {
                    e.printStackTrace();
                }

                if (doesSelectedExist())
                {
                    selected.getValue().setBPLoaded(true);
                }
            })
            .start();

            backplateLoaded = true;
        }
    };

    private final ChangeListener<EnvironmentSetting> settingChange = (observable, oldSetting, newSetting) ->
    {
        if (newSetting != null)
        {
            newSetting.envImageFileProperty().addListener(envFileChange);
            envFileChange.changed(null, oldSetting == null ? null : oldSetting.getEnvImageFile(), newSetting.getEnvImageFile());

            newSetting.bpImageFileProperty().addListener(bpFileChange);
            bpFileChange.changed(null, oldSetting == null ? null : oldSetting.getBpImageFile(), newSetting.getBpImageFile());
        }

        if (oldSetting != null)
        {
            oldSetting.envImageFileProperty().removeListener(envFileChange);
            oldSetting.bpImageFileProperty().removeListener(bpFileChange);
        }
    };

    @Override
    public BackgroundMode getBackgroundMode()
    {
        EnvironmentSetting selectedEnvironment = selected.getValue();
        if (this.backplateLoaded && doesSelectedExist() && selectedEnvironment.isBPUseImageEnabled())
        {
            return BackgroundMode.IMAGE;
        }
        else if (selectedEnvironment.isBPUseColorEnabled())
        {
            return BackgroundMode.COLOR;
        }
        else if (isEnvironmentMappingEnabled())
        {
            return BackgroundMode.ENVIRONMENT_MAP;
        }
        else
        {
            return BackgroundMode.NONE;
        }
    }

    @Override
    public boolean isEnvironmentMappingEnabled()
    {
        return this.environmentMapLoaded && doesSelectedExist() && selected.getValue().isEnvUseImageEnabled();
    }

    @Override
    public Matrix4 getEnvironmentMapMatrix()
    {
        if (doesSelectedExist())
        {
            double azimuth = selected.getValue().getEnvRotation();
            return Matrix4.rotateY(Math.toRadians(azimuth));
        }
        else
        {
            return Matrix4.IDENTITY;
        }
    }

    @Override
    public float getEnvironmentRotation()
    {
        return doesSelectedExist() ? (float)(selected.getValue().getEnvRotation() * Math.PI / 180) : 0.0f;
    }

    @Override
    public void setEnvironmentRotation(float environmentRotation)
    {
        if (doesSelectedExist())
        {
            selected.getValue().setEnvRotation(environmentRotation * 180 / Math.PI);
        }
    }

    @Override
    public float getEnvironmentIntensity()
    {
        return doesSelectedExist() ? (float)selected.getValue().getEnvIntensity() : 0.0f;
    }

    @Override
    public void setEnvironmentIntensity(float environmentIntensity)
    {
        if (doesSelectedExist())
        {
            selected.getValue().setEnvIntensity(environmentIntensity);
        }
    }

    @Override
    public float getBackgroundIntensity()
    {
        return doesSelectedExist() ? (float)selected.getValue().getBackgroundIntensity() : 0.0f;
    }

    @Override
    public void setBackgroundIntensity(float backgroundIntensity)
    {
        if (doesSelectedExist())
        {
            selected.getValue().setBackgroundIntensity(backgroundIntensity);
        }
    }
}
