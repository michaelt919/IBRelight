/*
 * Copyright (c) Michael Tetzlaff 2019
 * Copyright (c) The Regents of the University of Minnesota 2019
 *
 * Licensed under GPLv3
 * ( http://www.gnu.org/licenses/gpl-3.0.html )
 *
 * This code is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */

package tetzlaff.ibrelight.export.general;

import java.io.File;
import java.io.IOException;

import tetzlaff.gl.core.Context;
import tetzlaff.gl.core.Drawable;
import tetzlaff.gl.core.FramebufferObject;
import tetzlaff.gl.core.Program;
import tetzlaff.ibrelight.core.IBRRenderable;
import tetzlaff.ibrelight.core.IBRRequest;
import tetzlaff.ibrelight.core.LoadingMonitor;
import tetzlaff.ibrelight.rendering.IBRResources;
import tetzlaff.models.ReadonlySettingsModel;

class SingleFrameRenderRequest extends RenderRequestBase
{
    private final String outputImageName;

    SingleFrameRenderRequest(int width, int height, String outputImageName, ReadonlySettingsModel settingsModel,
        File vertexShader, File fragmentShader, File outputDirectory)
    {
        super(width, height, settingsModel, vertexShader, fragmentShader, outputDirectory);
        this.outputImageName = outputImageName;
    }

    static class Builder extends BuilderBase
    {
        private final String outputImageName;

        Builder(String outputImageName, ReadonlySettingsModel settingsModel, File fragmentShader, File outputDirectory)
        {
            super(settingsModel, fragmentShader, outputDirectory);
            this.outputImageName = outputImageName;
        }

        @Override
        public IBRRequest create()
        {
            return new SingleFrameRenderRequest(getWidth(), getHeight(), outputImageName, getSettingsModel(),
                getVertexShader(), getFragmentShader(), getOutputDirectory());
        }
    }

    @Override
    public <ContextType extends Context<ContextType>>
        void executeRequest(IBRRenderable<ContextType> renderable, LoadingMonitor callback)
            throws IOException
    {
        IBRResources<ContextType> resources = renderable.getResources();

        try
        (
            Program<ContextType> program = createProgram(resources);
            FramebufferObject<ContextType> framebuffer = createFramebuffer(resources.context)
        )
        {
            Drawable<ContextType> drawable = createDrawable(program, resources);

            program.setUniform("model_view", renderable.getActiveViewSet().getCameraPose(0));
            program.setUniform("projection",
                renderable.getActiveViewSet().getCameraProjection(
                    renderable.getActiveViewSet().getCameraProjectionIndex(0))
                    .getProjectionMatrix(renderable.getActiveViewSet().getRecommendedNearPlane(),
                        renderable.getActiveViewSet().getRecommendedFarPlane()));

            render(drawable, framebuffer);

            File exportFile = new File(getOutputDirectory(), outputImageName);
            getOutputDirectory().mkdirs();
            framebuffer.saveColorBufferToFile(0, "PNG", exportFile);
        }
    }
}
