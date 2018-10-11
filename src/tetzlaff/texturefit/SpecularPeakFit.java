package tetzlaff.texturefit;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.function.Consumer;

import tetzlaff.gl.core.*;
import tetzlaff.texturefit.ParameterizedFitBase.SubdivisionRenderingCallback;

public class SpecularPeakFit<ContextType extends Context<ContextType>> implements AutoCloseable
{
    private final ParameterizedFitBase<ContextType> base;
    private final Program<ContextType> program;

    private Framebuffer<ContextType> framebuffer;

    SpecularPeakFit(Context<ContextType> context, Consumer<Drawable<ContextType>> shaderSetup,
        int viewCount, int subdiv) throws IOException
    {
        this.program = context.getShaderProgramBuilder()
            .addShader(ShaderType.VERTEX, Paths.get("shaders", "common", "texspace.vert").toFile())
            .addShader(ShaderType.FRAGMENT, Paths.get("shaders","texturefit", "specularpeakfit_imgspace.frag").toFile())
            .createProgram();
        this.base = new ParameterizedFitBase<>(context.createDrawable(this.program), viewCount, subdiv);
        shaderSetup.accept(this.base.drawable);
    }

    @Override
    public void close()
    {
        this.program.close();
    }

    void setFramebuffer(Framebuffer<ContextType> framebuffer)
    {
        this.framebuffer = framebuffer;
    }

    void fitImageSpace(Texture<ContextType> viewImages, Texture<ContextType> depthImages, Texture<ContextType> shadowImages,
        Texture<ContextType> diffuseEstimate, Texture<ContextType> normalEstimate, Texture<ContextType> peakEstimate,
        SubdivisionRenderingCallback callback) throws IOException
    {
        program.setTexture("diffuseEstimate", diffuseEstimate);
        program.setTexture("normalEstimate", normalEstimate);
        program.setTexture("peakEstimate", peakEstimate);
        base.fitImageSpace(framebuffer, viewImages, depthImages, shadowImages, callback);
    }
}
