package tetzlaff.ibr.tools;//Created by alexk on 7/24/2017.

import tetzlaff.gl.window.CursorPosition;
import tetzlaff.gl.window.WindowSize;
import tetzlaff.models.ExtendedCameraModel;

final class DollyTool implements DragTool
{
    private static final double DOLLY_SENSITIVITY = Math.PI;
    private double dollySensitivityAdjusted;

    private float oldLog10Distance;
    private float oldTwist;

    private CursorPosition mouseStart;

    private final ExtendedCameraModel cameraModel;

    private static class Builder extends ToolBuilderBase<DollyTool>
    {
        @Override
        public DollyTool build()
        {
            return new DollyTool(getCameraModel());
        }
    }

    static ToolBuilder<DollyTool> getBuilder()
    {
        return new Builder();
    }

    private DollyTool(ExtendedCameraModel cameraModel)
    {
        this.cameraModel = cameraModel;
    }

    @Override
    public void mouseButtonPressed(CursorPosition cursorPosition, WindowSize windowSize)
    {
        this.mouseStart = cursorPosition;
        oldTwist = cameraModel.getTwist();
        oldLog10Distance = cameraModel.getLog10Distance();
        dollySensitivityAdjusted = DOLLY_SENSITIVITY / Math.min(windowSize.width, windowSize.height);
    }

    @Override
    public void cursorDragged(CursorPosition cursorPosition, WindowSize windowSize)
    {
        cameraModel.setTwist(oldTwist + (float) Math.toDegrees((cursorPosition.x - this.mouseStart.x) * dollySensitivityAdjusted));
        cameraModel.setLog10Distance((float) (oldLog10Distance + 0.5 * dollySensitivityAdjusted * (this.mouseStart.y - cursorPosition.y)));
    }
}
