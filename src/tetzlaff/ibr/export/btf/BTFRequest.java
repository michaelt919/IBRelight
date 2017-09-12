package tetzlaff.ibr.export.btf;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import tetzlaff.gl.*;
import tetzlaff.gl.vecmath.Vector3;
import tetzlaff.ibr.core.IBRRenderable;
import tetzlaff.ibr.core.IBRRequest;
import tetzlaff.ibr.core.LoadingMonitor;
import tetzlaff.ibr.core.ReadonlySettingsModel;
import tetzlaff.ibr.rendering.IBRResources;

public class BTFRequest implements IBRRequest
{
    private final int width;
    private final int height;
    private final File exportPath;
    private final ReadonlySettingsModel settings;
    private final Vector3 lightColor;

    public BTFRequest(int width, int height, File exportPath, ReadonlySettingsModel settings, Vector3 lightColor)
    {
        this.width = width;
        this.height = height;
        this.exportPath = exportPath;
        this.settings = settings;
        this.lightColor = lightColor;
    }

    @Override
    public <ContextType extends Context<ContextType>> void executeRequest(IBRRenderable<ContextType> renderable, LoadingMonitor callback)
        throws IOException
    {
        try
        {
            IBRResources<ContextType> resources = renderable.getResources();
            ContextType context = resources.context;

            Program<ContextType> btfProgram = context.getShaderProgramBuilder()
                    .addShader(ShaderType.VERTEX, new File("shaders/common/texspace_noscale.vert"))
                    .addShader(ShaderType.FRAGMENT, new File("shaders/relight/relight.frag"))
                    .createProgram();

            FramebufferObject<ContextType> framebuffer = context.buildFramebufferObject(width, height)
                    .addColorAttachment()
                    .createFramebufferObject();

            Drawable<ContextType> drawable = context.createDrawable(btfProgram);
            drawable.addVertexBuffer("position", resources.positionBuffer);
            drawable.addVertexBuffer("texCoord", resources.texCoordBuffer);
            drawable.addVertexBuffer("normal", resources.normalBuffer);
            drawable.addVertexBuffer("tangent", resources.tangentBuffer);

            resources.setupShaderProgram(btfProgram, this.settings.getRenderingMode());

            btfProgram.setUniform("renderGamma", this.settings.getGamma());
            btfProgram.setUniform("weightExponent", this.settings.getWeightExponent());
            btfProgram.setUniform("isotropyFactor", this.settings.getIsotropyFactor());
            btfProgram.setUniform("occlusionEnabled", resources.depthTextures != null && this.settings.isOcclusionEnabled());
            btfProgram.setUniform("occlusionBias", this.settings.getOcclusionBias());
            btfProgram.setUniform("imageBasedRenderingEnabled", this.settings.getRenderingMode().isImageBased());
            btfProgram.setUniform("relightingEnabled", this.settings.isRelightingEnabled());
            btfProgram.setUniform("pbrGeometricAttenuationEnabled", this.settings.isPBRGeometricAttenuationEnabled());
            btfProgram.setUniform("fresnelEnabled", this.settings.isFresnelEnabled());

            btfProgram.setUniform("shadowsEnabled", false);
            btfProgram.setUniform("useEnvironmentTexture", false);
            btfProgram.setTexture("environmentMap", null);
            btfProgram.setUniform("ambientColor", Vector3.ZERO);

            btfProgram.setUniform("perPixelWeightsEnabled", true);

            btfProgram.setUniform("useTSOverrides", true);

            ////////////////////////////////

//            // Backscattering
//            for (int i = 1; i <= 179; i++)
//            {
//                double theta = i / 180.0f * Math.PI;
//                btfProgram.setUniform("virtualLightCount", 1);
//                btfProgram.setUniform("lightIntensityVirtual[0]", lightColor);
//                btfProgram.setUniform("lightDirTSOverride", new Vector3((float)Math.cos(theta), 0.0f, (float)Math.sin(theta)));
//                btfProgram.setUniform("viewDirTSOverride", new Vector3((float)Math.cos(theta), 0.0f, (float)Math.sin(theta)));

            // Joey's lab
            for (int i = 1; i <= 90; i++)
            {
                double theta = i / 180.0f * Math.PI;
                btfProgram.setUniform("virtualLightCount", 1);
                btfProgram.setUniform("lightIntensityVirtual[0]", lightColor);
                btfProgram.setUniform("lightDirTSOverride", new Vector3(-(float)(Math.sin(theta)*Math.sqrt(0.5)), (float)(Math.sin(theta)*Math.sqrt(0.5)), (float)Math.cos(theta)));
                btfProgram.setUniform("viewDirTSOverride", new Vector3((float)(Math.cos(theta)*Math.sqrt(0.5)), -(float)(Math.cos(theta)*Math.sqrt(0.5)), (float)Math.sin(theta)));
//                btfProgram.setUniform("lightDirTSOverride", new Vector3(-(float)Math.sin(theta), 0.0f, (float)Math.cos(theta)));
//                btfProgram.setUniform("viewDirTSOverride", new Vector3((float)Math.cos(theta), 0.0f, (float)Math.sin(theta)));

            ////////////////////////////////

                context.getState().disableBackFaceCulling();

                framebuffer.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, 0.0f);
                drawable.draw(PrimitiveMode.TRIANGLES, framebuffer);

                File exportFile = new File(exportPath, String.format("%02d.png", i));
                exportFile.getParentFile().mkdirs();
                framebuffer.saveColorBufferToFile(0, "PNG", exportFile);

                if (callback != null)
                {
                    callback.setProgress((double) i / (double) /*90*/180);
                }
            }
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
        }
    }
}
