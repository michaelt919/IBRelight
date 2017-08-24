package tetzlaff.ibr.rendering;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import tetzlaff.gl.Context;
import tetzlaff.gl.FramebufferObject;
import tetzlaff.ibr.core.IBRRenderable;
import tetzlaff.ibr.core.IBRRequest;
import tetzlaff.ibr.core.LoadingMonitor;
import tetzlaff.ibr.core.ViewSet;

public class ResampleRequest implements IBRRequest
{
    private final int resampleWidth;
    private final int resampleHeight;
    private final File resampleVSETFile;
    private final File resampleExportPath;
    
    public ResampleRequest(int width, int height, File targetVSETFile, File exportPath)
    {
        this.resampleWidth = width;
        this.resampleHeight = height;
        this.resampleVSETFile = targetVSETFile;
        this.resampleExportPath = exportPath;
    }

    @Override
    public <ContextType extends Context<ContextType>> void executeRequest(ContextType context, IBRRenderable<ContextType> renderable, LoadingMonitor callback) throws IOException
    {
        ViewSet targetViewSet = ViewSet.loadFromVSETFile(resampleVSETFile);
        FramebufferObject<ContextType> framebuffer = context.buildFramebufferObject(resampleWidth, resampleHeight)
                .addColorAttachment()
                .addDepthAttachment()
                .createFramebufferObject();

        for (int i = 0; i < targetViewSet.getCameraPoseCount(); i++)
        {
            framebuffer.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, /*1.0f*/0.0f);
            framebuffer.clearDepthBuffer();

            renderable.draw(framebuffer, targetViewSet.getCameraPose(i),
                    targetViewSet.getCameraProjection(targetViewSet.getCameraProjectionIndex(i))
                        .getProjectionMatrix(targetViewSet.getRecommendedNearPlane(), targetViewSet.getRecommendedFarPlane()));

            File exportFile = new File(resampleExportPath, targetViewSet.getImageFileName(i));
            exportFile.getParentFile().mkdirs();
            framebuffer.saveColorBufferToFile(0, "PNG", exportFile);

            if (callback != null)
            {
                callback.setProgress((double) i / (double) targetViewSet.getCameraPoseCount());
            }
        }

        Files.copy(resampleVSETFile.toPath(),
            new File(resampleExportPath, resampleVSETFile.getName()).toPath(),
            StandardCopyOption.REPLACE_EXISTING);
        Files.copy(renderable.getActiveViewSet().getGeometryFile().toPath(),
            new File(resampleExportPath, renderable.getActiveViewSet().getGeometryFile().getName()).toPath(),
            StandardCopyOption.REPLACE_EXISTING);
    }
}