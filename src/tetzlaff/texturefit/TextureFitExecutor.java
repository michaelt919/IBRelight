package tetzlaff.texturefit;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.function.IntFunction;
import java.util.function.IntToDoubleFunction;

import javax.imageio.ImageIO;

import tetzlaff.gl.ColorFormat;
import tetzlaff.gl.CompressionFormat;
import tetzlaff.gl.Context;
import tetzlaff.gl.Drawable;
import tetzlaff.gl.Framebuffer;
import tetzlaff.gl.FramebufferObject;
import tetzlaff.gl.PrimitiveMode;
import tetzlaff.gl.Program;
import tetzlaff.gl.ShaderType;
import tetzlaff.gl.Texture;
import tetzlaff.gl.Texture2D;
import tetzlaff.gl.Texture3D;
import tetzlaff.gl.UniformBuffer;
import tetzlaff.gl.VertexBuffer;
import tetzlaff.gl.builders.framebuffer.ColorAttachmentSpec;
import tetzlaff.gl.nativebuffer.NativeDataType;
import tetzlaff.gl.nativebuffer.NativeVectorBuffer;
import tetzlaff.gl.nativebuffer.NativeVectorBufferFactory;
import tetzlaff.gl.util.VertexGeometry;
import tetzlaff.gl.vecmath.Matrix4;
import tetzlaff.gl.vecmath.Vector2;
import tetzlaff.gl.vecmath.Vector3;
import tetzlaff.gl.vecmath.Vector4;
import tetzlaff.ibr.IBRLoadOptions;
import tetzlaff.ibr.ViewSet;
import tetzlaff.ibr.rendering.IBRResources;

public class TextureFitExecutor<ContextType extends Context<ContextType>>
{
	private static final int SHADOW_MAP_FAR_PLANE_CUSHION = 2; // TODO decide where this should be defined
	private static final int ROUGHNESS_TEXTURE_SIZE = 1; // TODO decide where this should be defined
	
	private static final double FITTING_GAMMA = 2.2; // TODO make this configurable from the interface
	private static final double FITTING_GAMMA_INV = 1.0 / FITTING_GAMMA;
	

	private ContextType context;
	private File vsetFile;
	private File objFile;
	private File imageDir;
	private File maskDir;
	private File rescaleDir;
	private File outputDir;
	private File tmpDir;
	private File auxDir;
	private TextureFitParameters param;
	
	private String materialFileName;
	private String materialName;
	
	private ViewSet viewSet;
	private IBRResources<ContextType> viewSetResources;
	
	private Program<ContextType> depthRenderingProgram;
	private Program<ContextType> projTexProgram;
	private Program<ContextType> lightFitProgram;
	private Program<ContextType> diffuseFitProgram;
	private Program<ContextType> specularFitProgram;
	private Program<ContextType> specularFit2Program;
	private Program<ContextType> specularResidProgram;
	private Program<ContextType> adjustFitProgram;
	private Program<ContextType> errorCalcProgram;
	private Program<ContextType> diffuseDebugProgram;
	private Program<ContextType> specularDebugProgram;
	private Program<ContextType> textureRectProgram;
	private Program<ContextType> holeFillProgram;
	private Program<ContextType> finalizeProgram;
	
	private VertexBuffer<ContextType> positionBuffer;
	private VertexBuffer<ContextType> texCoordBuffer;
	private VertexBuffer<ContextType> normalBuffer;
	private VertexBuffer<ContextType> tangentBuffer;
	private Vector3 center;
	
	UniformBuffer<ContextType> lightPositionBuffer;
	UniformBuffer<ContextType> lightIntensityBuffer;
	UniformBuffer<ContextType> shadowMatrixBuffer;
	
	Texture3D<ContextType> viewTextures;
	Texture3D<ContextType> depthTextures;
	Texture3D<ContextType> shadowTextures;
	
	public TextureFitExecutor(ContextType context, File vsetFile, File objFile, File imageDir, File maskDir, File rescaleDir, File outputDir, TextureFitParameters param) 
	{
		this.context = context;
		this.vsetFile = vsetFile;
		this.objFile = objFile;
		this.imageDir = imageDir;
		this.maskDir = maskDir;
		this.rescaleDir = rescaleDir;
		this.outputDir = outputDir;
		this.param = param;
	}
	
	private void compileShaders() throws IOException
	{
    	System.out.println("Loading and compiling shader programs...");
    	Date timestamp = new Date();
    	
		depthRenderingProgram = context.getShaderProgramBuilder()
    			.addShader(ShaderType.VERTEX, new File("shaders/common/depth.vert"))
    			.addShader(ShaderType.FRAGMENT, new File("shaders/common/depth.frag"))
    			.createProgram();
    	
    	projTexProgram = context.getShaderProgramBuilder()
    			.addShader(ShaderType.VERTEX, new File("shaders", "common/texspace.vert"))
    			.addShader(ShaderType.FRAGMENT, new File("shaders", "colorappearance/projtex_single.frag"))
    			.createProgram();
    	
    	lightFitProgram = context.getShaderProgramBuilder()
    			.addShader(ShaderType.VERTEX, new File("shaders", "common/texspace.vert"))
    			.addShader(ShaderType.FRAGMENT, new File("shaders", 
    					(param.isImagePreprojectionUseEnabled() ? "texturefit/lightfit_texspace.frag" : "texturefit/lightfit_imgspace.frag")))
    			.createProgram();
    	
    	diffuseFitProgram = context.getShaderProgramBuilder()
    			.addShader(ShaderType.VERTEX, new File("shaders", "common/texspace.vert"))
    			.addShader(ShaderType.FRAGMENT, new File("shaders", 
    					(param.isImagePreprojectionUseEnabled() ? "texturefit/diffusefit_texspace.frag" : "texturefit/diffusefit_imgspace.frag")))
    			.createProgram();
		
    	specularFit2Program = context.getShaderProgramBuilder()
    			.addShader(ShaderType.VERTEX, new File("shaders", "common/texspace.vert"))
    			.addShader(ShaderType.FRAGMENT, new File("shaders", 
    					(param.isImagePreprojectionUseEnabled() ? "texturefit/specularfit2_texspace.frag" : "texturefit/specularfit2_imgspace.frag")))
    			.createProgram();
		
    	adjustFitProgram = context.getShaderProgramBuilder()
    			.addShader(ShaderType.VERTEX, new File("shaders", "common/texspace.vert"))
    			.addShader(ShaderType.FRAGMENT, new File("shaders",
    					//"texturefit/adjustfit_debug.frag"))
    					(param.isImagePreprojectionUseEnabled() ? "texturefit/adjustfit_texspace.frag" : "texturefit/adjustfit_imgspace.frag")))
    			.createProgram();
		
    	errorCalcProgram = context.getShaderProgramBuilder()
    			.addShader(ShaderType.VERTEX, new File("shaders", "common/texspace.vert"))
    			.addShader(ShaderType.FRAGMENT, new File("shaders", 
    					//"texturefit/errorcalc_debug.frag"))
    					(param.isImagePreprojectionUseEnabled() ? "texturefit/errorcalc_texspace.frag" : "texturefit/errorcalc_imgspace.frag")))
    			.createProgram();
    	
    	specularResidProgram = context.getShaderProgramBuilder()
    			.addShader(ShaderType.VERTEX, new File("shaders", "common/texspace.vert"))
    			.addShader(ShaderType.FRAGMENT, new File("shaders", 
    					(param.isImagePreprojectionUseEnabled() ? "texturefit/specularresid_imgspace.frag" : "texturefit/specularresid_imgspace_multi.frag")))
    			.createProgram();
		
    	diffuseDebugProgram = context.getShaderProgramBuilder()
    			.addShader(ShaderType.VERTEX, new File("shaders", "common/texspace.vert"))
    			.addShader(ShaderType.FRAGMENT, new File("shaders", "colorappearance/projtex_multi.frag"))
    			.createProgram();
		
    	specularDebugProgram = context.getShaderProgramBuilder()
    			.addShader(ShaderType.VERTEX, new File("shaders", "common/texspace.vert"))
    			.addShader(ShaderType.FRAGMENT, new File("shaders", "texturefit/specularresid_imgspace.frag"))
    			.createProgram();
		
    	textureRectProgram = context.getShaderProgramBuilder()
    			.addShader(ShaderType.VERTEX, new File("shaders", "common/texture.vert"))
    			.addShader(ShaderType.FRAGMENT, new File("shaders", "common/texture.frag"))
    			.createProgram();
		
    	holeFillProgram = context.getShaderProgramBuilder()
    			.addShader(ShaderType.VERTEX, new File("shaders", "common/texture.vert"))
    			.addShader(ShaderType.FRAGMENT, new File("shaders", "texturefit/holefill.frag"))
    			.createProgram();
		
    	finalizeProgram = context.getShaderProgramBuilder()
    			.addShader(ShaderType.VERTEX, new File("shaders", "common/texture.vert"))
    			.addShader(ShaderType.FRAGMENT, new File("shaders", "texturefit/finalize.frag"))
    			.createProgram();
		
    	System.out.println("Shader compilation completed in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
	}
	
	private interface TextureSpaceCallback<ContextType extends Context<ContextType>>
	{
		void execute(Framebuffer<ContextType> framebuffer, int subdivRow, int subdivCol);
	}
	
	private FramebufferObject<ContextType> projectIntoTextureSpace(
			Program<ContextType> program, int viewIndex, int textureSize, int textureSubdiv, boolean useExistingTextureArray,
			TextureSpaceCallback<ContextType> callback) throws IOException
	{
		FramebufferObject<ContextType> mainFBO = 
			context.buildFramebufferObject(textureSize / textureSubdiv, textureSize / textureSubdiv)
				.addColorAttachments(ColorFormat.RGBA32F, 3)
				.createFramebufferObject();
    	Drawable<ContextType> drawable = context.createDrawable(program);
    	
    	drawable.addVertexBuffer("position", positionBuffer);
    	drawable.addVertexBuffer("texCoord", texCoordBuffer);
    	drawable.addVertexBuffer("normal", normalBuffer);
    	drawable.addVertexBuffer("tangent", tangentBuffer);
    	
    	drawable.program().setUniform("gamma", param.getGamma());
    	
    	if (viewSetResources.luminanceMap == null)
        {
    		drawable.program().setUniform("useLuminanceMap", false);
        }
        else
        {
        	drawable.program().setUniform("useLuminanceMap", true);
        	drawable.program().setTexture("luminanceMap", viewSetResources.luminanceMap);
        }
    	
		int width, height;
		Texture2D<ContextType> viewTexture = null;
    	FramebufferObject<ContextType> shadowFBO = null;
    	
		if (useExistingTextureArray && viewTextures != null)
		{
			if (!param.isCameraVisibilityTestEnabled() || depthTextures == null)
			{
				drawable.program().setUniform("occlusionEnabled", false);
				drawable.program().setUniform("shadowTestEnabled", false);
			}
			else
			{
				drawable.program().setTexture("depthImages", depthTextures);
				drawable.program().setUniform("occlusionEnabled", true);
				drawable.program().setUniform("occlusionBias", param.getCameraVisibilityTestBias());
				
				if (shadowTextures == null || shadowMatrixBuffer == null)
				{
					drawable.program().setUniform("shadowTestEnabled", false);
				}
				else
				{
					drawable.program().setTexture("shadowImages", depthTextures);
					drawable.program().setUniformBuffer("ShadowMatrices", shadowMatrixBuffer);
					drawable.program().setUniform("shadowTestEnabled", true);
				}
			}
			
			drawable.program().setUniformBuffer("CameraPoses", viewSetResources.cameraPoseBuffer);
			drawable.program().setUniformBuffer("CameraProjections", viewSetResources.cameraProjectionBuffer);
			drawable.program().setUniformBuffer("CameraProjectionIndices", viewSetResources.cameraProjectionIndexBuffer);
			drawable.program().setUniformBuffer("LightIndices", viewSetResources.lightIndexBuffer);
			drawable.program().setUniformBuffer("LightPositions", this.lightPositionBuffer);
			drawable.program().setUniformBuffer("LightIntensities", this.lightIntensityBuffer);

			drawable.program().setTexture("viewImages", viewTextures);
			drawable.program().setUniform("viewIndex", viewIndex);
			drawable.program().setUniform("viewCount", viewSet.getCameraPoseCount());
			drawable.program().setUniform("infiniteLightSources", false);
			
			width = viewTextures.getWidth();
			height = viewTextures.getHeight();
		}
		else
		{
			drawable.program().setUniform("occlusionEnabled", param.isCameraVisibilityTestEnabled());
	    	drawable.program().setUniform("occlusionBias", param.getCameraVisibilityTestBias());
	    	
	    	drawable.program().setUniform("cameraPose", viewSet.getCameraPose(viewIndex));
	    	drawable.program().setUniform("cameraProjection", 
	    			viewSet.getCameraProjection(viewSet.getCameraProjectionIndex(viewIndex))
	    				.getProjectionMatrix(viewSet.getRecommendedNearPlane(), viewSet.getRecommendedFarPlane()));
			
	    	boolean enableShadowTest = param.isCameraVisibilityTestEnabled();

	    	Vector3 lightPosition = viewSet.getLightPosition(viewSet.getLightIndex(viewIndex));
	    	drawable.program().setUniform("lightIntensity", viewSet.getLightIntensity(viewSet.getLightIndex(viewIndex)));
	    	drawable.program().setUniform("lightPosition", lightPosition);
			drawable.program().setUniform("infiniteLightSource", false);
	    	
	    	File imageFile = new File(imageDir, viewSet.getImageFileName(viewIndex));
			if (!imageFile.exists())
			{
				String[] filenameParts = viewSet.getImageFileName(viewIndex).split("\\.");
		    	filenameParts[filenameParts.length - 1] = "png";
		    	String pngFileName = String.join(".", filenameParts);
		    	imageFile = new File(imageDir, pngFileName);
			}
			
			if (maskDir == null)
	    	{
	    		viewTexture = context.build2DColorTextureFromFile(imageFile, true)
	    						.setLinearFilteringEnabled(true)
	    						.setMipmapsEnabled(true)
	    						.createTexture();
	    	}
	    	else
	    	{
	    		File maskFile = new File(maskDir, viewSet.getImageFileName(viewIndex));
				if (!maskFile.exists())
				{
					String[] filenameParts = viewSet.getImageFileName(viewIndex).split("\\.");
			    	filenameParts[filenameParts.length - 1] = "png";
			    	String pngFileName = String.join(".", filenameParts);
			    	maskFile = new File(maskDir, pngFileName);
				}
				
	    		viewTexture = context.build2DColorTextureFromFileWithMask(imageFile, maskFile, true)
	    						.setLinearFilteringEnabled(true)
	    						.setMipmapsEnabled(true)
	    						.createTexture();
	    	}
	    	
	    	drawable.program().setTexture("viewImage", viewTexture);
	    	
	    	width = viewTexture.getWidth();
	    	height = viewTexture.getHeight();
	    	
	    	if (enableShadowTest)
	    	{
	        	Matrix4 shadowModelView = Matrix4.lookAt(new Vector3(viewSet.getCameraPoseInverse(viewIndex).times(new Vector4(lightPosition, 1.0f))), center, new Vector3(0, 1, 0));
	        	
	    		Matrix4 shadowProjection = viewSet.getCameraProjection(viewSet.getCameraProjectionIndex(viewIndex))
	    				.getProjectionMatrix(
	    					viewSet.getRecommendedNearPlane(), 
	    					viewSet.getRecommendedFarPlane() * SHADOW_MAP_FAR_PLANE_CUSHION // double it for good measure
	    				);
	    		
	    		shadowFBO = context.buildFramebufferObject(width, height)
		    					.addDepthAttachment()
		    					.createFramebufferObject();
	    		
	    		Drawable<ContextType> shadowDrawable = context.createDrawable(depthRenderingProgram);
	    		shadowDrawable.addVertexBuffer("position", positionBuffer);
	        	
	        	depthRenderingProgram.setUniform("model_view", shadowModelView);
	    		depthRenderingProgram.setUniform("projection", shadowProjection);
	        	
	    		shadowFBO.clearDepthBuffer();
	    		shadowDrawable.draw(PrimitiveMode.TRIANGLES, shadowFBO);
	    		
	    		drawable.program().setUniform("shadowTestEnabled", true);
	    		drawable.program().setUniform("shadowMatrix", shadowProjection.times(shadowModelView));
	        	drawable.program().setTexture("shadowImage", shadowFBO.getDepthAttachmentTexture());
	    	}
	    	else
	    	{
	    		drawable.program().setUniform("shadowTestEnabled", false);
	    	}
		}
    	
    	FramebufferObject<ContextType> depthFBO = 
			context.buildFramebufferObject(width, height)
				.addDepthAttachment()
				.createFramebufferObject();
    	
    	Drawable<ContextType> depthDrawable = context.createDrawable(depthRenderingProgram);
    	depthDrawable.addVertexBuffer("position", positionBuffer);
    	
    	depthRenderingProgram.setUniform("model_view", viewSet.getCameraPose(viewIndex));
		depthRenderingProgram.setUniform("projection", 
			viewSet.getCameraProjection(viewSet.getCameraProjectionIndex(viewIndex))
				.getProjectionMatrix(
					viewSet.getRecommendedNearPlane(), 
					viewSet.getRecommendedFarPlane()
				)
		);
    	
		depthFBO.clearDepthBuffer();
    	depthDrawable.draw(PrimitiveMode.TRIANGLES, depthFBO);
    	
    	if (!useExistingTextureArray || viewTextures == null)
    	{
    		drawable.program().setTexture("depthImage", depthFBO.getDepthAttachmentTexture());
    	}
    	
    	for (int row = 0; row < textureSubdiv; row++)
    	{
	    	for (int col = 0; col < textureSubdiv; col++)
    		{
	    		drawable.program().setUniform("minTexCoord", 
	    				new Vector2((float)col / (float)textureSubdiv, (float)row / (float)textureSubdiv));
	    		
	    		drawable.program().setUniform("maxTexCoord", 
	    				new Vector2((float)(col+1) / (float)textureSubdiv, (float)(row+1) / (float)textureSubdiv));
	    		
	    		mainFBO.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, 0.0f);
	    		mainFBO.clearColorBuffer(1, 0.0f, 0.0f, 0.0f, 0.0f);
	    		mainFBO.clearColorBuffer(2, 0.0f, 0.0f, 0.0f, 0.0f);
	    		mainFBO.clearDepthBuffer();
	    		drawable.draw(PrimitiveMode.TRIANGLES, mainFBO);
	    		
	    		callback.execute(mainFBO, row, col);
	    	}
		}
    	
    	mainFBO.close();
    	
    	if (viewTexture != null)
    	{
    		viewTexture.close();
    	}
    	
    	if (shadowFBO != null)
		{
    		shadowFBO.close();
		}
    	
    	return depthFBO;
	}
	
	private static class SpecularParams
	{
		public final double reflectivity;
		public final double roughness;
		public final double remainder;
		
		public SpecularParams(double reflectivity, double roughness, double remainder)
		{
			this.reflectivity = reflectivity;
			this.roughness = roughness;
			this.remainder = remainder;
		}
	}
	
	private static double computeSumSqError(double roughnessSq, double reflectivity, double remainder, int directionalRes, double nDotHStart, 
			IntFunction<Vector3> colorSumLookup, IntFunction<Vector3> colorSquareSumLookup, 
			IntToDoubleFunction diffuseAlphaLookup, IntToDoubleFunction specularAlphaLookup, IntToDoubleFunction contributionSumLookup)
	{
		double sumSqError = 0.0;
		
		// Evaluate error
		for (int j = 0; j < directionalRes; j++)
		{
			double specularAlpha = specularAlphaLookup.applyAsDouble(j);
			if (specularAlpha > 0.0)
			{
				double coord = (double)j / (double)(directionalRes - 1);
				double nDotH = coord + (1 - coord) * nDotHStart;
				double contributionSum = contributionSumLookup.applyAsDouble(j);
				
				if (nDotH > 0.0 && contributionSum > 0.0)
				{
					double nDotHSquared = nDotH * nDotH;
					
					// Scaled by pi
//					double mfdEst = Math.exp((nDotHSquared - 1) / (nDotHSquared * roughnessSq))
//							/ (roughnessSq * nDotHSquared * nDotHSquared);
					
					// Optimize for GGX distribution
					double q1 = roughnessSq + (1.0 - nDotHSquared) / nDotHSquared;
					double mfdEst = roughnessSq / (nDotHSquared * nDotHSquared * q1 * q1);
					
//					double error = colorSumLookup.apply(j).y - specularAlpha * (remainder + reflectivity * mfdEst);
//					sumSqError += error * error;
					
					double fit = Math.pow((diffuseAlphaLookup.applyAsDouble(j) * remainder + specularAlpha * reflectivity * mfdEst) / contributionSum, FITTING_GAMMA_INV);
					double luminanceSum = colorSumLookup.apply(j).y;
					double sqError = colorSquareSumLookup.apply(j).y - 2 * luminanceSum * fit + contributionSum * fit * fit;
					sumSqError += sqError;
				}
			}
		}
		
		return sumSqError;
	}
	
	private SpecularParams computeSpecularParams(int directionalRes, double nDotHStart, 
			IntFunction<Vector3> colorSumLookup, IntFunction<Vector3> colorSquareSumLookup, 
			IntToDoubleFunction diffuseWeightSumLookup, IntToDoubleFunction specularWeightSumLookup, IntToDoubleFunction contributionSumLookup,
			File mfdFile) throws IOException
	{
		PrintStream mfdStream = new PrintStream(mfdFile);
		
		// Integral of Specular reflectivity
		// 		times microfacet probability
		//		times microfacet slope squared
		// 		times d [n dot h]
		// as [n dot h] goes from [n dot h]_min to 1
		double specularWeightedSlopeSum = 0.0;
		
		// Integral to find Specular reflectivity (low res)
		double specularReflectivitySum = 0.0;
		
		double contributionSum;
		double specularWeightSum;
		double nDotH;
		
		final double SQRT_HALF = Math.sqrt(0.5);
		
		// In case the first step(s) are missing
		int j = 0;
		do
		{
			contributionSum = contributionSumLookup.applyAsDouble(j);
			specularWeightSum = specularWeightSumLookup.applyAsDouble(j);
			double coord = (double)j / (double)(directionalRes - 1);
			nDotH = coord + (1 - coord) * nDotHStart;
			
			if (specularWeightSum == 0.0)
			{
				mfdStream.println(nDotH + ",0.0,0.0,0.0,0.0");
			}
			else if (nDotH < SQRT_HALF)
			{
				mfdStream.print(nDotH + ",");
				mfdStream.print(Math.max(0.0, colorSumLookup.apply(j).x) / specularWeightSum + ",");
				mfdStream.print(Math.max(0.0, colorSumLookup.apply(j).y) / specularWeightSum + ",");
				mfdStream.print(Math.max(0.0, colorSumLookup.apply(j).z) / specularWeightSum + ",");
				mfdStream.println(contributionSum);
			}
			
			j++;
		}
		while (j < directionalRes-1 && (specularWeightSum == 0.0 || nDotH < SQRT_HALF));
		
		specularWeightSum = specularWeightSumLookup.applyAsDouble(j);
		double lastWeight = colorSumLookup.apply(j).y / specularWeightSum;
		
		double coord = (double)j / (double)(directionalRes - 1);
		double lastNDotH = coord + (1 - coord) * nDotHStart;
		
		mfdStream.print(lastNDotH + ",");

		mfdStream.print(Math.max(0.0, colorSumLookup.apply(j).x) / specularWeightSum + ",");
		mfdStream.print(Math.max(0.0, colorSumLookup.apply(j).y) / specularWeightSum + ",");
		mfdStream.print(Math.max(0.0, colorSumLookup.apply(j).z) / specularWeightSum + ",");
		mfdStream.println(contributionSum);
		
//		if (lastNDotH > 0.0)
//		{
//			specularWeightedSlopeSum += lastWeight * (1 / lastNDotH - lastNDotH) * (lastNDotH - nDotHStart);
//			specularReflectivitySum += lastWeight * lastNDotH * (lastNDotH - nDotHStart);
//		}
//		else
		{
			lastWeight = 0.0;
		}
		
		int intervalCount = 0;
		
		for (j++; j < directionalRes; j++)
		{
			specularWeightSum = specularWeightSumLookup.applyAsDouble(j);
			
			coord = (double)j / (double)(directionalRes - 1);
			nDotH = coord + (1 - coord) * nDotHStart;
			
			if (specularWeightSum <= 0.0)
			{
				mfdStream.println(nDotH + ",0.0,0.0,0.0,0.0");
			}
			else
			{
				double weight = Math.max(0.0, colorSumLookup.apply(j).y) / specularWeightSum;

				mfdStream.print(nDotH + ",");

				contributionSum = contributionSumLookup.applyAsDouble(j);
				mfdStream.print(Math.max(0.0, colorSumLookup.apply(j).x) / specularWeightSum + ",");
				mfdStream.print(Math.max(0.0, colorSumLookup.apply(j).y) / specularWeightSum + ",");
				mfdStream.print(Math.max(0.0, colorSumLookup.apply(j).z) / specularWeightSum + ",");
				mfdStream.println(contributionSum);
				
				// Trapezoidal rule for integration
				if (lastNDotH > 0.0)
				{
					specularWeightedSlopeSum += lastWeight * (1 / lastNDotH - lastNDotH) / 2 * (nDotH - lastNDotH);
				}
				
				if (nDotH > 0.0)
				{
					specularWeightedSlopeSum += weight * (1 / nDotH - nDotH) / 2 * (nDotH - lastNDotH);
				}
				else
				{
					weight = 0.0;
				}
				
				specularReflectivitySum += (weight * nDotH + lastWeight * lastNDotH) / 2 * (nDotH - lastNDotH);
				
				intervalCount++;
				
				lastWeight = weight;
				lastNDotH = nDotH;
			}
		}
		
		// In case the final step(s) were missing
		specularWeightedSlopeSum += lastWeight * (1 / lastNDotH - lastNDotH) * (1.0 - lastNDotH);
		specularReflectivitySum += lastWeight * lastNDotH * (1.0 - lastNDotH);
		
		mfdStream.close();
		
		if (intervalCount > 0 && specularReflectivitySum > 0.0 && specularWeightedSlopeSum > 0.0)
		{
			double roughnessSq = specularWeightedSlopeSum / specularReflectivitySum;
			
			if (roughnessSq > 0.5)
			{
				roughnessSq = 0.5;
			}
			
			// Assuming scaling by 1 / pi
			double reflectivity = 2 * specularReflectivitySum;
			
			if (reflectivity > 1.0)
			{
				reflectivity = 1.0;
			}
			
			//if (!param.isLevenbergMarquardtOptimizationEnabled())
			{
				return new SpecularParams(reflectivity, Math.sqrt(roughnessSq), 0.0);
			}
//			else
//			{
//				double remainder = 0.0;
//				
//				double sumSqError;
//				double nextSumSqError = computeSumSqError(roughnessSq, reflectivity, remainder, directionalRes, nDotHStart, 
//						colorSumLookup, colorSquareSumLookup, diffuseWeightSumLookup, specularWeightSumLookup, contributionSumLookup);
//				
//				int i = 0;
//				double deltaRoughnessSq;
//				double deltaReflectivity;
//				double deltaRemainder;
//				double shiftFraction = 1.0;
//				
//				do
//				{
//					// Solving for parameter vector: [ roughness^2, reflectivity, diffuse ]
//					DoubleMatrix3 jacobianSquared = new DoubleMatrix3(0.0f);
//					DoubleVector3 jacobianTimesResiduals = new DoubleVector3(0.0f);
//					
//					sumSqError = nextSumSqError;
//					
//					for (j = 0; j < directionalRes; j++)
//					{
//						specularWeightSum = specularWeightSumLookup.applyAsDouble(j);
//						if (specularWeightSum > 0.0)
//						{
//							coord = (double)j / (double)(directionalRes - 1);
//							nDotH = coord + (1 - coord) * nDotHStart;
//							contributionSum = contributionSumLookup.applyAsDouble(j);
//							
//							if (nDotH > 0.0 && contributionSum > 0.0 )
//							{
//								double nDotHSquared = nDotH * nDotH;
//								
//								// Scaled by pi
//	//							double mfdEst = Math.exp((nDotHSquared - 1) / (nDotHSquared * roughnessSq))
//	//									/ (roughnessSq * nDotHSquared * nDotHSquared);
//	//							
//	//							double mfdDeriv = -mfdEst * (nDotHSquared * (roughnessSq + 1) - 1) 
//	//									/ (roughnessSq * roughnessSq * nDotHSquared);
//								
//								// Optimize for GGX distribution
//								double q1 = roughnessSq + (1.0 - nDotHSquared) / nDotHSquared;
//								double mfdEst = roughnessSq / (nDotHSquared * nDotHSquared * q1 * q1);
//								double q2 = 1.0 + (roughnessSq - 1.0) * nDotHSquared;
//								double mfdDeriv = (1.0 - (roughnessSq + 1.0) * nDotHSquared) / (q2 * q2 * q2);
//										
//	//							double residualWeighted = (colorSumLookup.apply(j).y - specularWeightSum * (remainder + reflectivity * mfdEst));
//	//							DoubleVector3 derivsWeighted = new DoubleVector3(specularWeightSum * reflectivity * mfdDeriv, specularWeightSum * mfdEst, specularWeightSum);
//	
//								double diffuseWeightSum = diffuseWeightSumLookup.applyAsDouble(j);
//								double currentFit = (diffuseWeightSum * remainder + reflectivity * mfdEst * specularWeightSum) / contributionSum;
//								double residualWeighted = colorSumLookup.apply(j).y // already gamma corrected
//										- contributionSum * Math.pow(currentFit, FITTING_GAMMA_INV);
//								
//								double dw = FITTING_GAMMA_INV * Math.pow(currentFit, FITTING_GAMMA_INV - 1);
//								DoubleVector3 derivsWeighted = new DoubleVector3(
//										dw * specularWeightSum * reflectivity * mfdDeriv, 
//										dw * specularWeightSum * mfdEst, 
//										dw * diffuseWeightSum);
//								
//								DoubleVector3 derivsUnweighted = derivsWeighted.times(1.0 / contributionSum);
//								
//								jacobianSquared = jacobianSquared.plus(derivsUnweighted.outerProduct(derivsWeighted));
//								jacobianTimesResiduals = jacobianTimesResiduals.plus(derivsUnweighted.times(residualWeighted));
//							}
//						}
//					}
//					
//					DoubleMatrix3 jacobianSquaredInverse = jacobianSquared.inverse();
//					DoubleMatrix3 identityTest = jacobianSquaredInverse.times(jacobianSquared);
//					
//					DoubleVector3 paramDelta = jacobianSquared.inverse().times(jacobianTimesResiduals);
//					deltaRoughnessSq = shiftFraction * paramDelta.x;
//					deltaReflectivity = shiftFraction * paramDelta.y;
//					deltaRemainder = shiftFraction * paramDelta.z;
//					double nextRoughnessSq = roughnessSq + deltaRoughnessSq;
//					double nextReflectivity = reflectivity + deltaReflectivity;
//					double nextRemainder = remainder + deltaRemainder; // TODO is "remainder" even necessary?
//					
//					while(nextRoughnessSq < 0.0 || nextReflectivity < 0.0)
//					{
//						shiftFraction /= 2;
//						deltaRoughnessSq /= 2;
//						deltaReflectivity /= 2;
//						deltaRemainder /= 2;
//	
//						nextRoughnessSq = roughnessSq + deltaRoughnessSq;
//						nextReflectivity = reflectivity + deltaReflectivity;
//						nextRemainder = remainder + deltaRemainder;
//					}
//					
//					nextSumSqError = computeSumSqError(nextRoughnessSq, nextReflectivity, nextRemainder, directionalRes, nDotHStart, 
//							colorSumLookup, colorSquareSumLookup, diffuseWeightSumLookup, specularWeightSumLookup, contributionSumLookup);
//					
//					while(nextSumSqError > sumSqError)
//					{
//						shiftFraction /= 2;
//						deltaRoughnessSq /= 2;
//						deltaReflectivity /= 2;
//						deltaRemainder /= 2;
//	
//						nextRoughnessSq = roughnessSq + deltaRoughnessSq;
//						nextReflectivity = reflectivity + deltaReflectivity;
//						nextRemainder = remainder + deltaRemainder;
//						
//						nextSumSqError = computeSumSqError(nextRoughnessSq, nextReflectivity, nextRemainder, directionalRes, nDotHStart, 
//								colorSumLookup, colorSquareSumLookup, diffuseWeightSumLookup, specularWeightSumLookup, contributionSumLookup);
//					}
//					
//					roughnessSq = nextRoughnessSq;
//					reflectivity = nextReflectivity;
//					remainder = nextRemainder;
//				}
//				while(++i < 100 && (sumSqError - nextSumSqError) / sumSqError > 0.001);
//	
//				double roughness = Math.sqrt(roughnessSq);
//				return new SpecularParams(reflectivity, roughness, remainder);
//			}
		}
		else
		{
			return new SpecularParams(0.0, 1.0, 0.0);
		}
	}
	
	private SpecularParams globalSpecularFit(Texture<ContextType> diffuseEstimate, Texture<ContextType> normalObjSpEstimate) throws IOException
	{
		int directionalRes = 4096;
		
		double[][] colorSums = new double[directionalRes][3];
		double[][] colorSquareSums = new double[directionalRes][3];
		double[] diffuseWeightSums = new double[directionalRes];
		double[] specularWeightSums = new double[directionalRes];
		double[] contributionSums = new double[directionalRes];
		
		System.out.println("Sampling views...");
		
		if (diffuseEstimate != null && normalObjSpEstimate != null)
		{
			System.out.println("Using diffuse estimate to compute specular residuals.");
			specularResidProgram.setTexture("diffuseEstimate", diffuseEstimate);
			specularResidProgram.setTexture("normalEstimate", normalObjSpEstimate);
			specularResidProgram.setUniform("useDiffuseEstimate", true);
		}
		else
		{
			specularResidProgram.setUniform("useDiffuseEstimate", false);
		}
		
		for (int k = 0; k < viewSet.getCameraPoseCount(); k++)
		{
			final int K = k;
			
			FramebufferObject<ContextType> depthFBO = projectIntoTextureSpace(specularResidProgram, k, /*param.getTextureSize()*/1024, 1, !param.isImagePreprojectionUseEnabled(),
				(framebuffer, row, col) -> 
				{
					if (param.isDebugModeEnabled())
			    	{
						try
	    				{
	    					framebuffer.saveColorBufferToFile(0, "PNG", new File(new File(auxDir, "specularResidDebug"), String.format("colors%04d.png", K)));
	    					framebuffer.saveColorBufferToFile(1, "PNG", new File(new File(auxDir, "specularResidDebug"), String.format("geom%04d.png", K)));
	    				}
	    				catch (IOException e)
	    				{
	    					e.printStackTrace();
	    				}
			    	}
					
					int partitionSize = 1024;//param.getTextureSize();
					
					float[] colorData = framebuffer.readFloatingPointColorBufferRGBA(0);
					float[] geomData = framebuffer.readFloatingPointColorBufferRGBA(1);
						
					for (int y = 0; y < partitionSize; y++)
					{
						for (int x = 0; x < partitionSize; x++)
						{
							float colorX = (float)Math.pow(Math.max(0.0, colorData[((y*partitionSize) + x) * 4 + 0]), FITTING_GAMMA_INV);
							float colorY = (float)Math.pow(Math.max(0.0, colorData[((y*partitionSize) + x) * 4 + 1]), FITTING_GAMMA_INV);
							float colorZ = (float)Math.pow(Math.max(0.0, colorData[((y*partitionSize) + x) * 4 + 2]), FITTING_GAMMA_INV);
							float xSquared = colorX * colorX;
							float ySquared = colorY * colorY;
							float zSquared = colorZ * colorZ;
							float nDotL = colorData[((y*partitionSize) + x) * 4 + 3];
							float nDotH =  geomData[((y*partitionSize) + x) * 4 + 2];
							float geomRatio = geomData[((y*partitionSize) + x) * 4 + 3];
							
							if (nDotL > 0.0f && nDotH > 0.0f && geomRatio > 0.0f)
							{
								double bin = (directionalRes-1) * nDotH; //(directionalRes-1) * 2 * (hDotN - 0.5)
			
								int binFloor = (int)Math.floor(bin);
								int binCeil = (int)Math.ceil(bin);
								
								double s, t;
			
								if (binFloor >= 0 && binCeil < directionalRes)
								{
									if (binFloor == binCeil)
									{
										t = 0.0;
									}
									else
									{
										t = (bin - binFloor);
									}
									
									s = 1.0 - t;
									
									colorSums[binFloor][0] += s * colorX;
									colorSums[binCeil][0] += t * colorX;
									
									colorSums[binFloor][1] += s * colorY;
									colorSums[binCeil][1] += t * colorY;
									
									colorSums[binFloor][2] += s * colorZ;
									colorSums[binCeil][2] += t * colorZ;
									
									colorSquareSums[binFloor][0] += s * xSquared;
									colorSquareSums[binCeil][0] += t * xSquared;
									
									colorSquareSums[binFloor][1] += s * ySquared;
									colorSquareSums[binCeil][1] += t * ySquared;
									
									colorSquareSums[binFloor][2] += s * zSquared;
									colorSquareSums[binCeil][2] += t * zSquared;
									
									diffuseWeightSums[binFloor] += s * nDotL;
									diffuseWeightSums[binCeil] += t * nDotL;
									
									specularWeightSums[binFloor] += s * nDotL * geomRatio;
									specularWeightSums[binCeil] += t * nDotL * geomRatio;
									
									contributionSums[binFloor] += s;
									contributionSums[binFloor] += t;
								}
							}
						}
					}
				});
			

	    	depthFBO.close();
			
	    	System.out.println("Completed " + (k+1) + "/" + viewSet.getCameraPoseCount() + " views...");
		}
		
		return computeSpecularParams(directionalRes, 0.0, 
				(j) -> new Vector3((float)colorSums[j][0], (float)colorSums[j][1], (float)colorSums[j][2]), 
				(j) -> new Vector3((float)colorSquareSums[j][0], (float)colorSquareSums[j][1], (float)colorSquareSums[j][2]), 
				(j) -> diffuseWeightSums[j], 
				(j) -> specularWeightSums[j], 
				(j) -> contributionSums[j],
				new File(auxDir, "mfd.csv"));
	}
	
	private void loadMesh() throws IOException
	{
		VertexGeometry mesh = VertexGeometry.createFromOBJFile(objFile);
    	positionBuffer = context.createVertexBuffer().setData(mesh.getVertices());
    	texCoordBuffer = context.createVertexBuffer().setData(mesh.getTexCoords());
    	normalBuffer = context.createVertexBuffer().setData(mesh.getNormals());
    	tangentBuffer = context.createVertexBuffer().setData(mesh.getTangents());
    	center = mesh.getCentroid();
    	materialFileName = mesh.getMaterialFileName();
    	
    	if (materialFileName == null)
    	{
    		materialFileName = objFile.getName().split("\\.")[0] + ".mtl";
    	}
    	
    	if (mesh.getMaterial() == null)
    	{
    		materialName = materialFileName.split("\\.")[0];
    	}
    	else
    	{
	    	materialName = mesh.getMaterial().getName();
	    	if (materialName == null)
	    	{
	    		materialName = materialFileName.split("\\.")[0];
	    	}
    	}
    	
    	mesh = null;
    	System.gc(); // Garbage collect the mesh object (hopefully)
	}
	
	private Drawable<ContextType> getLightFitDrawable()
	{
		Drawable<ContextType> drawable = context.createDrawable(lightFitProgram);
    	
        drawable.addVertexBuffer("position", positionBuffer);
        drawable.addVertexBuffer("texCoord", texCoordBuffer);
        drawable.addVertexBuffer("normal", normalBuffer);
    	
        drawable.program().setUniform("viewCount", viewSet.getCameraPoseCount());
        drawable.program().setUniform("gamma", param.getGamma());
        if (viewSetResources.luminanceMap == null)
        {
        	drawable.program().setUniform("useLuminanceMap", false);
        }
        else
        {
        	drawable.program().setUniform("useLuminanceMap", true);
        	drawable.program().setTexture("luminanceMap", viewSetResources.luminanceMap);
        }
        drawable.program().setUniform("shadowTestEnabled", false);
        drawable.program().setUniform("occlusionEnabled", param.isCameraVisibilityTestEnabled());
        drawable.program().setUniform("occlusionBias", param.getCameraVisibilityTestBias());
        drawable.program().setUniform("infiniteLightSources", param.areLightSourcesInfinite());
    	
        drawable.program().setUniformBuffer("CameraPoses", viewSetResources.cameraPoseBuffer);
    	
    	if (!param.isImagePreprojectionUseEnabled())
    	{
    		drawable.program().setUniformBuffer("CameraProjections", viewSetResources.cameraProjectionBuffer);
    		drawable.program().setUniformBuffer("CameraProjectionIndices", viewSetResources.cameraProjectionIndexBuffer);
    	}
    	
    	drawable.program().setUniformBuffer("LightIndices", viewSetResources.lightIndexBuffer);
    	
    	drawable.program().setUniform("delta", param.getDiffuseDelta());
    	drawable.program().setUniform("iterations", 1/*param.getDiffuseIterations()*/); // TODO rework light fitting
    	
    	return drawable;
	}
	
	private /*static*/ abstract class LightFit<ContextType extends Context<ContextType>>
	{
		private Drawable<ContextType> drawable;
		final int framebufferSize;
		final int framebufferSubdiv;
		
		private Vector3 position;
		private Vector3 intensity;
		
		Vector3 getPosition()
		{
			return position;
		}
		
		Vector3 getIntensity()
		{
			return intensity;
		}
		
		protected abstract void fitTexture(Drawable<ContextType> drawable, Framebuffer<ContextType> framebuffer) throws IOException;
		
		LightFit(Drawable<ContextType> drawable, int framebufferSize, int framebufferSubdiv)
		{
	    	this.drawable = drawable;
	    	this.framebufferSize = framebufferSize;
	    	this.framebufferSubdiv = framebufferSubdiv;
		}
    	
    	void fit(int lightIndex) throws IOException
    	{
    		FramebufferObject<ContextType> framebuffer = 
				drawable.getContext().buildFramebufferObject(framebufferSize, framebufferSize)
					.addColorAttachments(ColorAttachmentSpec.createWithInternalFormat(ColorFormat.RGBA32F), 2)
					.createFramebufferObject();

    		framebuffer.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, 0.0f);
	    	framebuffer.clearColorBuffer(1, 0.0f, 0.0f, 0.0f, 0.0f);
        	
        	drawable.program().setUniform("lightIndex", lightIndex);
        	fitTexture(drawable, framebuffer);

    		framebuffer.saveColorBufferToFile(1, "PNG", new File(auxDir, "lightDebug.png"));
    	
	    	System.out.println("Aggregating light estimates...");
	    	
	    	float[] rawLightPositions = framebuffer.readFloatingPointColorBufferRGBA(0);
	        float[] rawLightIntensities = framebuffer.readFloatingPointColorBufferRGBA(1);
	        
	        framebuffer.close(); // No need for this anymore
	        
	        Vector4 lightPositionSum = new Vector4(0, 0, 0, 0);
	        Vector4 lightIntensitySum = new Vector4(0, 0, 0, 0);
	        
	        for (int i = 0; i < framebuffer.getSize().height; i++)
	        {
	        	for (int j = 0; j < framebuffer.getSize().width; j++)
	        	{
	        		int indexStart = (i * framebuffer.getSize().width + j) * 4;
	        		lightPositionSum = lightPositionSum.plus(
	        				new Vector4(rawLightPositions[indexStart+0], rawLightPositions[indexStart+1], rawLightPositions[indexStart+2], 1.0f)
	        					.times(rawLightPositions[indexStart+3]));
	        		lightIntensitySum = lightIntensitySum.plus(
	        				new Vector4(rawLightIntensities[indexStart+0], rawLightIntensities[indexStart+1], rawLightIntensities[indexStart+2], 1.0f)
	        					.times(rawLightIntensities[indexStart+3]));
	        	}
	        }
	        
	        position = new Vector3(lightPositionSum.dividedBy(lightPositionSum.w));
	        intensity = new Vector3(lightIntensitySum.dividedBy(lightIntensitySum.w));
    	}
	}
	
	private /*static*/ class TexSpaceLightFit<ContextType extends Context<ContextType>> extends LightFit<ContextType>
	{
		private File preprojDir;
		private int preprojCount;
		
		TexSpaceLightFit(Drawable<ContextType> drawable, File preprojDir, int preprojCount, int framebufferSize, int framebufferSubdiv)
		{
			super(drawable, framebufferSize, framebufferSubdiv);
			this.preprojDir = preprojDir;
			this.preprojCount = preprojCount;
		}
		
		@Override
		protected void fitTexture(Drawable<ContextType> drawable, Framebuffer<ContextType> framebuffer) throws IOException
		{
			int subdivSize = framebufferSize / framebufferSubdiv;
    		
    		for (int row = 0; row < framebufferSubdiv; row++)
	    	{
		    	for (int col = 0; col < framebufferSubdiv; col++)
	    		{
		    		Texture3D<ContextType> preprojectedViews = null;
			    	
			    	preprojectedViews = drawable.getContext().build2DColorTextureArray(subdivSize, subdivSize, preprojCount).createTexture();
				    	
					for (int i = 0; i < preprojCount; i++)
					{
						preprojectedViews.loadLayer(i, new File(new File(preprojDir, String.format("%04d", i)), String.format("r%04dc%04d.png", row, col)), true);
					}
		    		
		    		drawable.program().setTexture("viewImages", preprojectedViews);
			    	
			    	drawable.program().setUniform("minTexCoord", 
		    				new Vector2((float)col / (float)framebufferSubdiv, (float)row / (float)framebufferSubdiv));
		    		
			    	drawable.program().setUniform("maxTexCoord", 
		    				new Vector2((float)(col+1) / (float)framebufferSubdiv, (float)(row+1) / (float)framebufferSubdiv));
			    	
			    	drawable.draw(PrimitiveMode.TRIANGLES, framebuffer, col * subdivSize, row * subdivSize, subdivSize, subdivSize);
			        drawable.getContext().finish();
			        
			        if (framebufferSubdiv > 1)
		    		{
		    			System.out.println("Block " + (row*framebufferSubdiv + col + 1) + "/" + (framebufferSubdiv * framebufferSubdiv) + " completed.");
		    		}
	    		}
	    	}
		}
	}
	
	private /*static*/ class ImgSpaceLightFit<ContextType extends Context<ContextType>> extends LightFit<ContextType>
	{
		private Texture<ContextType> viewTextures;
		private Texture<ContextType> depthTextures;
		
		ImgSpaceLightFit(Drawable<ContextType> drawable, Texture<ContextType> viewTextures, Texture<ContextType> depthTextures, int framebufferSize, int framebufferSubdiv)
		{
			super(drawable, framebufferSize, framebufferSubdiv);
			
			this.viewTextures = viewTextures;
			this.depthTextures = depthTextures;
		}
		
		@Override
		protected void fitTexture(Drawable<ContextType> drawable, Framebuffer<ContextType> framebuffer)
		{
			int subdivSize = framebufferSize / framebufferSubdiv;
    		
    		for (int row = 0; row < framebufferSubdiv; row++)
	    	{
		    	for (int col = 0; col < framebufferSubdiv; col++)
	    		{
			    	drawable.program().setTexture("viewImages", viewTextures);
		    		drawable.program().setTexture("depthImages", depthTextures);
			    	
			    	drawable.program().setUniform("minTexCoord", 
		    				new Vector2((float)col / (float)framebufferSubdiv, (float)row / (float)framebufferSubdiv));
		    		
			    	drawable.program().setUniform("maxTexCoord", 
		    				new Vector2((float)(col+1) / (float)framebufferSubdiv, (float)(row+1) / (float)framebufferSubdiv));
			    	
			    	drawable.draw(PrimitiveMode.TRIANGLES, framebuffer, col * subdivSize, row * subdivSize, subdivSize, subdivSize);
			        drawable.getContext().finish();
			        
			        if (framebufferSubdiv > 1)
		    		{
		    			System.out.println("Block " + (row*framebufferSubdiv + col + 1) + "/" + (framebufferSubdiv * framebufferSubdiv) + " completed.");
		    		}
	    		}
	    	}
		}
	}
	
	private LightFit<ContextType> createTexSpaceLightFit(int framebufferSize, int framebufferSubdiv)
	{
		return new TexSpaceLightFit<ContextType>(getLightFitDrawable(), tmpDir, viewSet.getCameraPoseCount(), framebufferSize, framebufferSubdiv);
	}
	
	private LightFit<ContextType> createImgSpaceLightFit(Texture<ContextType> viewTextures, Texture<ContextType> depthTextures, int framebufferSize, int framebufferSubdiv)
	{
		return new ImgSpaceLightFit<ContextType>(getLightFitDrawable(), viewTextures, depthTextures, framebufferSize, framebufferSubdiv);
	}
	
	private void setupCommonShaderInputs(Drawable<ContextType> drawable)
	{
		drawable.addVertexBuffer("position", positionBuffer);
    	drawable.addVertexBuffer("texCoord", texCoordBuffer);
    	drawable.addVertexBuffer("normal", normalBuffer);
    	drawable.addVertexBuffer("tangent", tangentBuffer);

    	drawable.program().setUniform("viewCount", viewSet.getCameraPoseCount());
    	drawable.program().setUniformBuffer("CameraPoses", viewSetResources.cameraPoseBuffer);
    	
    	if (!param.isImagePreprojectionUseEnabled())
    	{
	    	drawable.program().setUniformBuffer("CameraProjections", viewSetResources.cameraProjectionBuffer);
	    	drawable.program().setUniformBuffer("CameraProjectionIndices", viewSetResources.cameraProjectionIndexBuffer);
    	}

    	drawable.program().setUniform("occlusionEnabled", param.isCameraVisibilityTestEnabled());
    	drawable.program().setUniform("occlusionBias", param.getCameraVisibilityTestBias());
    	drawable.program().setUniform("gamma", param.getGamma());
    	drawable.program().setUniform("fittingGamma", (float)FITTING_GAMMA);
    	drawable.program().setUniform("infiniteLightSources", param.areLightSourcesInfinite());

    	if (viewSetResources.luminanceMap == null)
        {
        	drawable.program().setUniform("useLuminanceMap", false);
        }
        else
        {
        	drawable.program().setUniform("useLuminanceMap", true);
        	drawable.program().setTexture("luminanceMap", viewSetResources.luminanceMap);
        }
    	
		drawable.program().setUniformBuffer("LightIndices", viewSetResources.lightIndexBuffer);
    	
    	if (lightPositionBuffer != null)
    	{
    		drawable.program().setUniformBuffer("LightPositions", lightPositionBuffer);
    	}
    	else
    	{
    		drawable.program().setUniformBuffer("LightPositions", viewSetResources.lightPositionBuffer);
    	}
    	
    	if (lightIntensityBuffer != null)
    	{
    		drawable.program().setUniformBuffer("LightIntensities", lightIntensityBuffer);
    	}
    	else
    	{
    		drawable.program().setUniformBuffer("LightIntensities", viewSetResources.lightIntensityBuffer);
    	}
    	
    	if (shadowMatrixBuffer != null)
    	{
    		drawable.program().setUniform("shadowTestEnabled", param.isCameraVisibilityTestEnabled());
    		drawable.program().setUniformBuffer("ShadowMatrices", shadowMatrixBuffer);
    	}
    	else
    	{
    		drawable.program().setUniform("shadowTestEnabled", false);
    	}
	}
	
	private static interface SubdivisionRenderingCallback
	{
		void execute(int row, int col) throws IOException;
	}
	
	private static class ParameterizedFitBase<ContextType extends Context<ContextType>>
	{
		final Drawable<ContextType> drawable;
		final int viewCount;
		final int subdiv;
		
		ParameterizedFitBase(Drawable<ContextType> drawable, int viewCount, int subdiv)
		{
			this.subdiv = subdiv;
			this.viewCount = viewCount;
	    	this.drawable = drawable;
		}
		
		private void fitSubdiv(Framebuffer<ContextType> framebuffer, int row, int col, 
				Texture<ContextType> viewImages, Texture<ContextType> depthImages, Texture<ContextType> shadowImages)
		{
			int subdivWidth = framebuffer.getSize().width / subdiv;
			int subdivHeight = framebuffer.getSize().height / subdiv;
			
			drawable.program().setTexture("viewImages", viewImages);
	    	drawable.program().setTexture("depthImages", depthImages);
	    	drawable.program().setTexture("shadowImages", shadowImages);
	    	
	    	drawable.program().setUniform("minTexCoord", 
    				new Vector2((float)col / (float)subdiv, (float)row / (float)subdiv));
    		
	    	drawable.program().setUniform("maxTexCoord", 
    				new Vector2((float)(col+1) / (float)subdiv, (float)(row+1) / (float)subdiv));
	    	
	        drawable.draw(PrimitiveMode.TRIANGLES, framebuffer, col * subdivWidth, row * subdivHeight, subdivWidth, subdivHeight);
	        drawable.getContext().finish();
		}
		
		void fitImageSpace(Framebuffer<ContextType> framebuffer, Texture<ContextType> viewImages, Texture<ContextType> depthImages, Texture<ContextType> shadowImages, 
				SubdivisionRenderingCallback callback) throws IOException
		{
			if (this.subdiv == 1)
        	{
        		this.fitSubdiv(framebuffer, 0, 0, viewImages, depthImages, shadowImages);
        	}
        	else
        	{
        		for (int row = 0; row < this.subdiv; row++)
    	    	{
    		    	for (int col = 0; col < this.subdiv; col++)
    	    		{
    	        		this.fitSubdiv(framebuffer, row, col, viewImages, depthImages, shadowImages);
    	        		callback.execute(row, col);
    	    		}
    	    	}
        	}
		}
		
		void fitTextureSpace(Framebuffer<ContextType> framebuffer, File preprocessDirectory, SubdivisionRenderingCallback callback) throws IOException
		{
			int subdivWidth = framebuffer.getSize().width / subdiv;
			int subdivHeight = framebuffer.getSize().height / subdiv;
			
			if (this.subdiv == 1)
        	{
        		Texture3D<ContextType> preprojectedViews = 
    				drawable.getContext().build2DColorTextureArray(subdivWidth, subdivHeight, viewCount).createTexture();
			    	
				for (int i = 0; i < this.viewCount; i++)
				{
					preprojectedViews.loadLayer(i, new File(new File(preprocessDirectory, String.format("%04d", i)), String.format("r%04dc%04d.png", 0, 0)), true);
				}
	    	
		    	this.fitSubdiv(framebuffer, 0, 0, preprojectedViews, null, null);
		        	
	        	preprojectedViews.close();
        	}
        	else
        	{
        		for (int row = 0; row < this.subdiv; row++)
    	    	{
    		    	for (int col = 0; col < this.subdiv; col++)
    	    		{
    	        		Texture3D<ContextType> preprojectedViews = 
	        				drawable.getContext().build2DColorTextureArray(subdivWidth, subdivHeight, viewCount).createTexture();
    				    	
						for (int i = 0; i < this.viewCount; i++)
						{
							preprojectedViews.loadLayer(i, new File(new File(preprocessDirectory, String.format("%04d", i)), String.format("r%04dc%04d.png", row, col)), true);
						}

    	        		this.fitSubdiv(framebuffer, row, col, preprojectedViews, null, null);
    	        		callback.execute(row, col);

			    		preprojectedViews.close();
    	    		}
    	    	}
        	}
		}
	}
	
	private static class DiffuseFit<ContextType extends Context<ContextType>>
	{
		private final ParameterizedFitBase<ContextType> base;
		private final Framebuffer<ContextType> framebuffer;
		
		DiffuseFit(Drawable<ContextType> drawable, Framebuffer<ContextType> framebuffer, int viewCount, int subdiv)
		{
			this.framebuffer = framebuffer;
			base = new ParameterizedFitBase<ContextType>(drawable, viewCount, subdiv);
		}
		
		void fitImageSpace(Texture<ContextType> viewImages, Texture<ContextType> depthImages, Texture<ContextType> shadowImages, SubdivisionRenderingCallback callback)
			throws IOException
		{
	    	base.fitImageSpace(framebuffer, viewImages, depthImages, shadowImages, callback);
		}
		
		void fitTextureSpace(File preprocessDirectory,  SubdivisionRenderingCallback callback) throws IOException
		{
	    	base.fitTextureSpace(framebuffer, preprocessDirectory, callback);
		}
	}
	
	private DiffuseFit<ContextType> createDiffuseFit(Framebuffer<ContextType> framebuffer, int viewCount, int subdiv)
	{
		Drawable<ContextType> drawable = context.createDrawable(diffuseFitProgram);
    	setupCommonShaderInputs(drawable);
    	drawable.program().setUniform("delta", param.getDiffuseDelta());
    	drawable.program().setUniform("iterations", param.getDiffuseIterations());
    	drawable.program().setUniform("fit1Weight", param.getDiffuseInputNormalWeight());
    	drawable.program().setUniform("fit3Weight", param.getDiffuseComputedNormalWeight());
		return new DiffuseFit<ContextType>(drawable, framebuffer, viewCount, subdiv);
	}
	
	private static class SpecularFit<ContextType extends Context<ContextType>>
	{
		private final ParameterizedFitBase<ContextType> base;
		private final Framebuffer<ContextType> framebuffer;
		
		SpecularFit(Drawable<ContextType> drawable, Framebuffer<ContextType> framebuffer, int viewCount, int subdiv)
		{
			this.framebuffer = framebuffer;
			base = new ParameterizedFitBase<ContextType>(drawable, viewCount, subdiv);
		}
		
		void fitImageSpace(Texture<ContextType> viewImages, Texture<ContextType> depthImages, Texture<ContextType> shadowImages, 
				Texture<ContextType> diffuseEstimate, Texture<ContextType> normalEstimate, Texture<ContextType> roughnessEstimate, SubdivisionRenderingCallback callback) throws IOException
		{
			base.drawable.program().setTexture("diffuseEstimate", diffuseEstimate);
			base.drawable.program().setTexture("normalEstimate", normalEstimate);
			base.drawable.program().setTexture("roughnessEstimate", roughnessEstimate);
	    	base.fitImageSpace(framebuffer, viewImages, depthImages, shadowImages, callback);
		}
		
		void fitTextureSpace(File preprocessDirectory, Texture<ContextType> diffuseEstimate, Texture<ContextType> normalEstimate, Texture<ContextType> roughnessEstimate,
				SubdivisionRenderingCallback callback)
			throws IOException
		{
			base.drawable.program().setTexture("diffuseEstimate", diffuseEstimate);
			base.drawable.program().setTexture("normalEstimate", normalEstimate);
			base.drawable.program().setTexture("roughnessEstimate", roughnessEstimate);
	    	base.fitTextureSpace(framebuffer, preprocessDirectory, callback);
		}
	}
	
	private SpecularFit<ContextType> createSpecularFit(Framebuffer<ContextType> framebuffer, int viewCount, int subdiv)
	{
		Drawable<ContextType> drawable = context.createDrawable(specularFit2Program);
    	setupCommonShaderInputs(drawable);
		return new SpecularFit<ContextType>(drawable, framebuffer, viewCount, subdiv);
	}
	
	private static class AdjustFit<ContextType extends Context<ContextType>>
	{
		private final ParameterizedFitBase<ContextType> base;
		
		AdjustFit(Drawable<ContextType> drawable, int viewCount, int subdiv)
		{
			base = new ParameterizedFitBase<ContextType>(drawable, viewCount, subdiv);
		}
		
		void fitImageSpace(Framebuffer<ContextType> framebuffer, Texture<ContextType> viewImages, Texture<ContextType> depthImages, Texture<ContextType> shadowImages, 
				Texture<ContextType> diffuseEstimate, Texture<ContextType> normalEstimate, Texture<ContextType> specularEstimate, Texture<ContextType> roughnessEstimate,
				Texture<ContextType> errorTexture, SubdivisionRenderingCallback callback) throws IOException
		{
			base.drawable.program().setTexture("diffuseEstimate", diffuseEstimate);
			base.drawable.program().setTexture("normalEstimate", normalEstimate);
			base.drawable.program().setTexture("specularEstimate", specularEstimate);
			base.drawable.program().setTexture("roughnessEstimate", roughnessEstimate);
			base.drawable.program().setTexture("errorTexture", errorTexture);
	    	base.fitImageSpace(framebuffer, viewImages, depthImages, shadowImages, callback);
		}
		
		void fitTextureSpace(Framebuffer<ContextType> framebuffer, File preprocessDirectory, 
				Texture<ContextType> diffuseEstimate, Texture<ContextType> normalEstimate, Texture<ContextType> specularEstimate, Texture<ContextType> roughnessEstimate,
				Texture<ContextType> errorTexture, SubdivisionRenderingCallback callback) throws IOException
		{
			base.drawable.program().setTexture("diffuseEstimate", diffuseEstimate);
			base.drawable.program().setTexture("normalEstimate", normalEstimate);
			base.drawable.program().setTexture("specularEstimate", specularEstimate);
			base.drawable.program().setTexture("roughnessEstimate", roughnessEstimate);
			base.drawable.program().setTexture("errorTexture", errorTexture);
	    	base.fitTextureSpace(framebuffer, preprocessDirectory, callback);
		}
	}
	
	private AdjustFit<ContextType> createAdjustFit(int viewCount, int subdiv)
	{
		Drawable<ContextType> drawable = context.createDrawable(adjustFitProgram);
    	setupCommonShaderInputs(drawable);
		return new AdjustFit<ContextType>(drawable, viewCount, subdiv);
	}
	
	private static class ErrorCalc<ContextType extends Context<ContextType>>
	{
		private final ParameterizedFitBase<ContextType> base;
		
		ErrorCalc(Drawable<ContextType> drawable, int viewCount, int subdiv)
		{
			base = new ParameterizedFitBase<ContextType>(drawable, viewCount, subdiv);
		}
		
		void fitImageSpace(Framebuffer<ContextType> framebuffer, Texture<ContextType> viewImages, Texture<ContextType> depthImages, Texture<ContextType> shadowImages, 
				Texture<ContextType> diffuseEstimate, Texture<ContextType> normalEstimate, Texture<ContextType> specularEstimate, Texture<ContextType> roughnessEstimate,
				Texture<ContextType> errorTexture, SubdivisionRenderingCallback callback) throws IOException
		{
			base.drawable.program().setTexture("diffuseEstimate", diffuseEstimate);
			base.drawable.program().setTexture("normalEstimate", normalEstimate);
			base.drawable.program().setTexture("specularEstimate", specularEstimate);
			base.drawable.program().setTexture("roughnessEstimate", roughnessEstimate);
			base.drawable.program().setTexture("errorTexture", errorTexture);
	    	base.fitImageSpace(framebuffer, viewImages, depthImages, shadowImages, callback);
		}
		
		void fitTextureSpace(Framebuffer<ContextType> framebuffer, File preprocessDirectory, 
				Texture<ContextType> diffuseEstimate, Texture<ContextType> normalEstimate, Texture<ContextType> specularEstimate, Texture<ContextType> roughnessEstimate,
				Texture<ContextType> errorTexture, SubdivisionRenderingCallback callback) throws IOException
		{
			base.drawable.program().setTexture("diffuseEstimate", diffuseEstimate);
			base.drawable.program().setTexture("normalEstimate", normalEstimate);
			base.drawable.program().setTexture("specularEstimate", specularEstimate);
			base.drawable.program().setTexture("roughnessEstimate", roughnessEstimate);
			base.drawable.program().setTexture("errorTexture", errorTexture);
	    	base.fitTextureSpace(framebuffer, preprocessDirectory, callback);
		}
	}
	
	private ErrorCalc<ContextType> createErrorCalc(int viewCount, int subdiv)
	{
		Drawable<ContextType> drawable = context.createDrawable(errorCalcProgram);
    	setupCommonShaderInputs(drawable);
		return new ErrorCalc<ContextType>(drawable, viewCount, subdiv);
	}
	
//	private void removeNormalMapBias(float[] normalData, int normalMapSize, VertexMesh mesh)
//	{
//		VertexBuffer<ContextType> selectorBuffer = context.createVertexBuffer();
//		FloatVertexList selectors = new FloatVertexList(1, mesh.getTexCoords().count);
//		
//		FloatVertexList biasedNormals = new FloatVertexList(3, mesh.getTexCoords().count);
//		
//		Renderable<ContextType> vertexWeightRenderable = context.createRenderable(vertexColorProgram);
//		vertexWeightRenderable.addVertexBuffer("texCoord", texCoordBuffer);
//		vertexWeightRenderable.addVertexBuffer("colors", selectorBuffer);
//		
//		FramebufferObject<ContextType> vertexWeightFramebuffer = 
//			context.getFramebufferObjectBuilder(normalMapSize, normalMapSize)
//				.addColorAttachment(ColorFormat.R8)
//				.createFramebufferObject();
//		
//		for (int i = 0; i < mesh.getUniqueTexCoords().count; i++)
//		{
//			int[] texCoordInstances = mesh.getUniqueTexCoords().get(i);
//			
//			for (int j = 0; j < texCoordInstances.length; j++)
//			{
//				// Select the current vertex
//				selectors.set(j, 0, 1.0f);
//			}
//			
//			// Define a vertex buffer with zeros at every vertex except the current one
//			selectorBuffer.setData(selectors);
//			
//			vertexWeightFramebuffer.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, 0.0f);
//			vertexWeightRenderable.draw(PrimitiveMode.TRIANGLES, vertexWeightFramebuffer);
//			float[] vertexWeightData = vertexWeightFramebuffer.readFloatingPointColorBufferRGBA(0);
//			
//			Vector3 weightedNormalSum = new Vector3(0.0f, 0.0f, 0.0f);
//			for (int j = 0; j < vertexWeightData.length; j++)
//			{
//				weightedNormalSum = weightedNormalSum
//					.plus(new Vector3(normalData[4*j + 0] - 0.5f, normalData[4*j + 1] - 0.5f, normalData[4*j + 2] - 0.5f)
//						.times(vertexWeightData[j]));
//			}
//			
//			Vector3 biasedNormal = weightedNormalSum.normalized();
//			
//			for (int j = 0; j < texCoordInstances.length; j++)
//			{
//				// Set the biased normal in all instances
//				biasedNormals.set(j, 0, biasedNormal.x);
//				biasedNormals.set(j, 1, biasedNormal.y);
//				biasedNormals.set(j, 2, biasedNormal.z);
//				
//				// Deselect the current vertex
//				selectors.set(j, 0, 0.0f);
//			}
//		}
//		
//		vertexWeightFramebuffer.delete();
//		selectorBuffer.delete();
//		
//		VertexBuffer<ContextType> biasedNormalBuffer = context.createVertexBuffer().setData(biasedNormals);
//		
//		Renderable<ContextType> biasedNormalRenderable = context.createRenderable(vertexColorProgram);
//		biasedNormalRenderable.addVertexBuffer("texCoord", texCoordBuffer);
//		biasedNormalRenderable.addVertexBuffer("colors", biasedNormalBuffer);
//		
//		FramebufferObject<ContextType> biasedNormalFramebuffer = 
//			context.getFramebufferObjectBuilder(normalMapSize, normalMapSize)
//				.addColorAttachment(ColorFormat.RGB32F)
//				.createFramebufferObject();
//		
//		biasedNormalFramebuffer.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, 0.0f);
//		biasedNormalRenderable.draw(PrimitiveMode.TRIANGLES, biasedNormalFramebuffer);
//		float[] biasedNormalData = vertexWeightFramebuffer.readFloatingPointColorBufferRGBA(0);
//		
//		for (int i = 0; i < normalData.length; i+=4)
//		{
//			Vector3 detailNormal = new Vector3(normalData[i + 0] - 0.5f, normalData[i + 1] - 0.5f, normalData[i + 2] - 0.5f).normalized();
//			Vector3 biasedNormal = new Vector3(biasedNormalData[i + 0], biasedNormalData[i + 1], biasedNormalData[i + 2]).normalized();
//			
//			Vector3 unbiasedNormal = new Vector3(detailNormal.x - biasedNormal.x, detailNormal.y - biasedNormal.y, detailNormal.z - biasedNormal.z + 1.0f).normalized();
//			
//			// Write into the input array
//			normalData[i + 0] = unbiasedNormal.x * 0.5f + 0.5f;
//			normalData[i + 1] = unbiasedNormal.y * 0.5f + 0.5f;
//			normalData[i + 2] = unbiasedNormal.z * 0.5f + 0.5f;
//		}
//
//		biasedNormalFramebuffer.delete();
//		biasedNormalBuffer.delete();
//	}
	
	private double getLinearDepth(double nonLinearDepth, double nearPlane, double farPlane)
	{
		return 2 * nearPlane * farPlane / (farPlane + nearPlane - nonLinearDepth * (farPlane - nearPlane));
	}
	
	private double loadTextures() throws IOException
	{
		if (param.isImagePreprojectionUseEnabled() && param.isImagePreprojectionGenerationEnabled())
    	{
    		System.out.println("Pre-projecting images into texture space...");
	    	Date timestamp = new Date();
	    	
	    	tmpDir.mkdir();
	    	
    		double minDepth = viewSet.getRecommendedFarPlane();
	    	
	    	for (int i = 0; i < viewSet.getCameraPoseCount(); i++)
	    	{
	    		File viewDir = new File(tmpDir, String.format("%04d", i));
	        	viewDir.mkdir();
	        	
	    		FramebufferObject<ContextType> depthFBO = projectIntoTextureSpace(projTexProgram, i, param.getTextureSize(), param.getTextureSubdivision(), false,
	    			(framebuffer, row, col) -> 
    				{
	    				try
	    				{
	    					framebuffer.saveColorBufferToFile(0, "PNG", new File(viewDir, String.format("r%04dc%04d.png", row, col)));
	    					if (param.isDebugModeEnabled())
	    					{
	    						framebuffer.saveColorBufferToFile(1, "PNG", new File(viewDir, String.format("geomInfo_r%04dc%04d.png", row, col)));
	    					}
	    				}
	    				catch (IOException e)
	    				{
	    					e.printStackTrace();
	    				}
					});
	    		
	    		if (i == viewSet.getPrimaryViewIndex())
    			{
		    		short[] depthBufferData = depthFBO.readDepthBuffer();
		        	for (int j = 0; j < depthBufferData.length; j++)
		        	{
		        		int nonlinearDepth = 0xFFFF & (int)depthBufferData[j];
		        		minDepth = Math.min(minDepth, getLinearDepth((double)nonlinearDepth / 0xFFFF, viewSet.getRecommendedNearPlane(), viewSet.getRecommendedFarPlane()));
		        	}
    			}

	        	depthFBO.close();
		    	
		    	System.out.println("Completed " + (i+1) + "/" + viewSet.getCameraPoseCount());
	    	}
	    	
	    	System.out.println("Pre-projections completed in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
	    	
	    	return minDepth;
    	}
    	else if (!param.isImagePreprojectionUseEnabled())
    	{
    		if (param.isImageRescalingEnabled())
    		{
    			System.out.println("Rescaling images...");
    			Date timestamp = new Date();
    			
				// Create an FBO for downsampling
		    	FramebufferObject<ContextType> downsamplingFBO = 
	    			context.buildFramebufferObject(param.getImageWidth(), param.getImageHeight())
	    				.addColorAttachment()
	    				.createFramebufferObject();
		    	
		    	Drawable<ContextType> downsampleRenderable = context.createDrawable(textureRectProgram);
		    	VertexBuffer<ContextType> rectBuffer = context.createRectangle();
		    	downsampleRenderable.addVertexBuffer("position", rectBuffer);
		    	
		    	// Downsample and store each image
		    	for (int i = 0; i < viewSet.getCameraPoseCount(); i++)
		    	{
		    		File imageFile = new File(imageDir, viewSet.getImageFileName(i));
					if (!imageFile.exists())
					{
						String[] filenameParts = viewSet.getImageFileName(i).split("\\.");
				    	filenameParts[filenameParts.length - 1] = "png";
				    	String pngFileName = String.join(".", filenameParts);
				    	imageFile = new File(imageDir, pngFileName);
					}
		    		
		    		Texture2D<ContextType> fullSizeImage;
		    		if (maskDir == null)
	    			{
		    			fullSizeImage = context.build2DColorTextureFromFile(imageFile, true)
		    								.setLinearFilteringEnabled(true)
		    								.setMipmapsEnabled(true)
		    								.createTexture();
	    			}
		    		else
		    		{
		    			File maskFile = new File(maskDir, viewSet.getImageFileName(i));
						if (!maskFile.exists())
						{
							String[] filenameParts = viewSet.getImageFileName(i).split("\\.");
					    	filenameParts[filenameParts.length - 1] = "png";
					    	String pngFileName = String.join(".", filenameParts);
					    	maskFile = new File(maskDir, pngFileName);
						}
						
		    			fullSizeImage = context.build2DColorTextureFromFileWithMask(imageFile, maskFile, true)
											.setLinearFilteringEnabled(true)
											.setMipmapsEnabled(true)
											.createTexture();
		    		}
		    		
		    		downsamplingFBO.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, 0.0f);
		        	
		    		textureRectProgram.setTexture("tex", fullSizeImage);
		        	
		        	downsampleRenderable.draw(PrimitiveMode.TRIANGLE_FAN, downsamplingFBO);
		        	context.finish();
		        	
		        	if (rescaleDir != null)
		        	{
				    	String[] filenameParts = viewSet.getImageFileName(i).split("\\.");
				    	filenameParts[filenameParts.length - 1] = "png";
				    	String pngFileName = String.join(".", filenameParts);
		        		downsamplingFBO.saveColorBufferToFile(0, "PNG", new File(rescaleDir, pngFileName));
		        	}
		        	
		        	fullSizeImage.close();
		        	
					System.out.println((i+1) + "/" + viewSet.getCameraPoseCount() + " images rescaled.");
		    	}
	
		    	rectBuffer.close();
		    	downsamplingFBO.close();
		    	
	    		System.out.println("Rescaling completed in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
	    		
	    		// Use rescale directory in the future
	    		imageDir = rescaleDir;
	    		rescaleDir = null;
	    		maskDir = null;
    		}
    		
    		System.out.println("Loading images...");
	    	Date timestamp = new Date();
	    	
	    	// Read a single image to get the dimensions for the texture array
	    	File imageFile = new File(imageDir, viewSet.getImageFileName(0));
			if (!imageFile.exists())
			{
				String[] filenameParts = viewSet.getImageFileName(0).split("\\.");
		    	filenameParts[filenameParts.length - 1] = "png";
		    	String pngFileName = String.join(".", filenameParts);
		    	imageFile = new File(imageDir, pngFileName);
			}
			BufferedImage img = ImageIO.read(new FileInputStream(imageFile));
			viewTextures = context.build2DColorTextureArray(img.getWidth(), img.getHeight(), viewSet.getCameraPoseCount())
							.setInternalFormat(CompressionFormat.RGB_PUNCHTHROUGH_ALPHA1_4BPP)
							.setLinearFilteringEnabled(true)
							.setMipmapsEnabled(true)
							.createTexture();
			
			for (int i = 0; i < viewSet.getCameraPoseCount(); i++)
			{
				imageFile = new File(imageDir, viewSet.getImageFileName(i));
				if (!imageFile.exists())
				{
					String[] filenameParts = viewSet.getImageFileName(i).split("\\.");
			    	filenameParts[filenameParts.length - 1] = "png";
			    	String pngFileName = String.join(".", filenameParts);
			    	imageFile = new File(imageDir, pngFileName);
				}
				
				if (maskDir == null)
				{
					viewTextures.loadLayer(i, imageFile, true);
				}
				else
				{
					File maskFile = new File(maskDir, viewSet.getImageFileName(i));
					if (!maskFile.exists())
					{
						String[] filenameParts = viewSet.getImageFileName(i).split("\\.");
				    	filenameParts[filenameParts.length - 1] = "png";
				    	String pngFileName = String.join(".", filenameParts);
				    	maskFile = new File(maskDir, pngFileName);
					}
					
					viewTextures.loadLayer(i, imageFile, maskFile, true);
				}
				
				System.out.println((i+1) + "/" + viewSet.getCameraPoseCount() + " images loaded.");
			}
	    	
    		System.out.println("Image loading completed in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
    		
    		System.out.println("Creating depth maps...");
	    	timestamp = new Date();
	    	
	    	// Build depth textures for each view
	    	int width = viewTextures.getWidth() / 2;
	    	int height = viewTextures.getHeight() / 2;
	    	depthTextures = context.build2DDepthTextureArray(width, height, viewSet.getCameraPoseCount()).createTexture();
	    	
	    	// Don't automatically generate any texture attachments for this framebuffer object
	    	FramebufferObject<ContextType> depthRenderingFBO = context.buildFramebufferObject(width, height).createFramebufferObject();
	    	
	    	Drawable<ContextType> depthRenderable = context.createDrawable(depthRenderingProgram);
	    	depthRenderable.addVertexBuffer("position", positionBuffer);

    		double minDepth = viewSet.getRecommendedFarPlane();
	    	
	    	// Render each depth texture
	    	for (int i = 0; i < viewSet.getCameraPoseCount(); i++)
	    	{
	    		depthRenderingFBO.setDepthAttachment(depthTextures.getLayerAsFramebufferAttachment(i));
	        	depthRenderingFBO.clearDepthBuffer();
	        	
	        	depthRenderingProgram.setUniform("model_view", viewSet.getCameraPose(i));
	    		depthRenderingProgram.setUniform("projection", 
					viewSet.getCameraProjection(viewSet.getCameraProjectionIndex(i))
	    				.getProjectionMatrix(
							viewSet.getRecommendedNearPlane(), 
							viewSet.getRecommendedFarPlane()
						)
				);
	        	
	        	depthRenderable.draw(PrimitiveMode.TRIANGLES, depthRenderingFBO);
	        	
	        	if (i == viewSet.getPrimaryViewIndex())
	        	{
		        	short[] depthBufferData = depthRenderingFBO.readDepthBuffer();
		        	for (int j = 0; j < depthBufferData.length; j++)
		        	{
		        		int nonlinearDepth = 0xFFFF & (int)depthBufferData[j];
		        		minDepth = Math.min(minDepth, getLinearDepth((double)nonlinearDepth / 0xFFFF, viewSet.getRecommendedNearPlane(), viewSet.getRecommendedFarPlane()));
		        	}
	        	}
	        	
				//System.out.println((i+1) + "/" + viewSet.getCameraPoseCount() + " depth maps created.");
	    	}

	    	depthRenderingFBO.close();
	    	
    		System.out.println("Depth maps created in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
	    	
	    	return minDepth;
    	}
    	else
    	{
    		return -1.0;
    	}
	}
	
	public void fitLightSource(double avgDistance) throws IOException
	{
		if (!param.areLightSourcesInfinite())
		{
			if (param.isLightOffsetEstimationEnabled())
	    	{
		    	System.out.println("Beginning light fit...");
		    	
	    		Vector3 lightIntensity = new Vector3((float)(avgDistance * avgDistance));
		        System.out.println("Using light intensity: " + lightIntensity.x + " " + lightIntensity.y + " " + lightIntensity.z);
		        
		    	Date timestamp = new Date();
				
		    	NativeVectorBuffer lightPositionList = NativeVectorBufferFactory.getInstance().createEmpty(NativeDataType.FLOAT, 4, viewSet.getLightCount());
		        NativeVectorBuffer lightIntensityList = NativeVectorBufferFactory.getInstance().createEmpty(NativeDataType.FLOAT, 3, viewSet.getLightCount());
		        
	    		LightFit<ContextType> lightFit;
		    	
		    	if (param.isImagePreprojectionUseEnabled())
		    	{
		    		lightFit = createTexSpaceLightFit(param.getTextureSize(), param.getTextureSubdivision());
		    	}
		    	else
		    	{
		    		lightFit = createImgSpaceLightFit(viewTextures, depthTextures, param.getTextureSize(), param.getTextureSubdivision());
		    	}
		        
		    	for (int i = 0; i < viewSet.getLightCount(); i++)
		    	{
		    		System.out.println("Fitting light " + i + "...");
		
		    		lightFit.fit(i);
		    		
		    		Vector3 lightPosition = lightFit.getPosition();
		    		//lightIntensity = lightFit.getIntensity();
			        
			        System.out.println("Light position: " + lightPosition.x + " " + lightPosition.y + " " + lightPosition.z);
			        System.out.println("(Light intensity from fit: " + lightFit.getIntensity().x + " " + lightFit.getIntensity().y + " " + lightFit.getIntensity().z + ")");
		    		
			    	lightPositionList.set(i, 0, lightPosition.x);
			    	lightPositionList.set(i, 1, lightPosition.y);
			    	lightPositionList.set(i, 2, lightPosition.z);
			    	lightPositionList.set(i, 3, 1.0f);
			    	
			        lightIntensityList.set(i, 0, lightIntensity.x);
			        lightIntensityList.set(i, 1, lightIntensity.y);
			        lightIntensityList.set(i, 2, lightIntensity.z);
			        
			        viewSet.setLightPosition(i, lightPosition);
			        viewSet.setLightIntensity(i, lightIntensity);
		    	}
		        
		        lightPositionBuffer = context.createUniformBuffer().setData(lightPositionList);
		        lightIntensityBuffer = context.createUniformBuffer().setData(lightIntensityList);
		        
		        System.out.println("Light fit completed in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
	    	}
	    	else if (param.isLightIntensityEstimationEnabled())
	    	{
		        NativeVectorBuffer lightIntensityList = NativeVectorBufferFactory.getInstance().createEmpty(NativeDataType.FLOAT, 3, viewSet.getLightCount());
	    		
	    		Vector3 lightIntensity = new Vector3((float)(avgDistance * avgDistance));
		        System.out.println("Using light intensity: " + lightIntensity.x + " " + lightIntensity.y + " " + lightIntensity.z);
		        
		        for (int i = 0; i < viewSet.getLightCount(); i++)
		    	{
			        lightIntensityList.set(i, 0, lightIntensity.x);
			        lightIntensityList.set(i, 1, lightIntensity.y);
			        lightIntensityList.set(i, 2, lightIntensity.z);

			        viewSet.setLightIntensity(i, lightIntensity);
		    	}
		        
		        lightPositionBuffer = viewSetResources.lightPositionBuffer;
		        lightIntensityBuffer = context.createUniformBuffer().setData(lightIntensityList);
	    	}
	    	else
	    	{
	    		System.out.println("Skipping light fit.");
	    		
	    		lightPositionBuffer = viewSetResources.lightPositionBuffer;
		        lightIntensityBuffer =  viewSetResources.lightIntensityBuffer;
	    	}
	        
//	        if (!param.isImagePreprojectionUseEnabled())
//	        {
//		        System.out.println("Creating shadow maps...");
//		    	Date timestamp = new Date();
//		    	
//		    	// Build shadow maps for each view
//		    	int width = param.getImageWidth(); //viewTextures.getWidth();
//		    	int height = param.getImageHeight(); //viewTextures.getHeight();
//		    	shadowTextures = context.get2DDepthTextureArrayBuilder(width, height, viewSet.getCameraPoseCount()).createTexture();
//		    	
//		    	// Don't automatically generate any texture attachments for this framebuffer object
//		    	FramebufferObject<ContextType> shadowRenderingFBO = context.getFramebufferObjectBuilder(width, height).createFramebufferObject();
//		    	
//		    	Renderable<ContextType> shadowRenderable = context.createRenderable(depthRenderingProgram);
//		    	shadowRenderable.addVertexBuffer("position", positionBuffer);
//		    	
//		    	// Flatten the camera pose matrices into 16-component vectors and store them in the vertex list data structure.
//		    	FloatVertexList flattenedShadowMatrices = new FloatVertexList(16, viewSet.getCameraPoseCount());
//		    	
//		    	// Render each shadow map
//		    	for (int i = 0; i < viewSet.getCameraPoseCount(); i++)
//		    	{
//		    		shadowRenderingFBO.setDepthAttachment(shadowTextures.getLayerAsFramebufferAttachment(i));
//		    		shadowRenderingFBO.clearDepthBuffer();
//		    		
//		    		Matrix4 modelView = Matrix4.lookAt(new Vector3(viewSet.getCameraPoseInverse(i).times(new Vector4(lightPosition, 1.0f))), center, new Vector3(0, 1, 0));
//		        	depthRenderingProgram.setUniform("model_view", modelView);
//		        	
//		    		Matrix4 projection = viewSet.getCameraProjection(viewSet.getCameraProjectionIndex(i))
//							.getProjectionMatrix(
//								viewSet.getRecommendedNearPlane(), 
//								viewSet.getRecommendedFarPlane() * SHADOW_MAP_FAR_PLANE_CUSHION // double it for good measure
//							);
//		    		depthRenderingProgram.setUniform("projection", projection);
//		        	
//		    		shadowRenderable.draw(PrimitiveMode.TRIANGLES, shadowRenderingFBO);
//		    		
//		    		Matrix4 fullTransform = projection.times(modelView);
//		    		
//		    		int d = 0;
//					for (int col = 0; col < 4; col++) // column
//					{
//						for (int row = 0; row < 4; row++) // row
//						{
//							flattenedShadowMatrices.set(i, d, fullTransform.get(row, col));
//							d++;
//						}
//					}
//		    	}
//				
//				// Create the uniform buffer
//				shadowMatrixBuffer = context.createUniformBuffer().setData(flattenedShadowMatrices);
//		
//		    	shadowRenderingFBO.delete();
//		    	
//				System.out.println("Shadow maps created in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
//	        }
		}
	}
	
	private void writeMTLFile(SpecularParams specularParams) throws FileNotFoundException
	{
    	PrintStream materialStream = new PrintStream(new File(outputDir, materialFileName));
    	materialStream.println("newmtl " + materialName);

    	if (param.isDiffuseTextureEnabled())
    	{
    		materialStream.println("Kd 1.0 1.0 1.0");
	    	materialStream.println("map_Kd " + materialName + "_Kd.png");
    		materialStream.println("Ka 1.0 1.0 1.0");
	    	materialStream.println("map_Ka " + materialName + "_Kd.png");
    	}
    	else
    	{
    		materialStream.println("Kd 0.0 0.0 0.0");
	    	
	    	if (param.isSpecularTextureEnabled())
	    	{
	    		materialStream.println("Ka 1.0 1.0 1.0");
		    	materialStream.println("map_Ka " + materialName + "_Ks.png");
	    	}
	    	else
	    	{
	    		materialStream.println("Ka " + specularParams.reflectivity + " " + specularParams.reflectivity + " " + specularParams.reflectivity);
	    	}
    	}
    	
    	if (param.isSpecularTextureEnabled())
    	{
	    	materialStream.println("Ks 1.0 1.0 1.0");
	    	materialStream.println("map_Ks " + materialName + "_Ks.png");
	    	materialStream.println("Ns " + (2.0 / (specularParams.roughness * specularParams.roughness) - 2.0)); // Fallback for non-PBR
	    	
	    	//if (param.isRoughnessTextureEnabled())
	    	{
		    	materialStream.println("Pr 1.0");
		    	materialStream.println("map_Pr " + materialName + "_Pr.png");
	    	}
//	    	else
//	    	{
//		    	materialStream.println("Pr " + specularParams.roughness);
//	    	}
    	}
    	else
    	{
    		materialStream.println("Ks " + specularParams.reflectivity + " " + specularParams.reflectivity + " " + specularParams.reflectivity);
	    	materialStream.println("Ns " + (2.0 / (specularParams.roughness * specularParams.roughness) - 2.0)); // Fallback for non-PBR
	    	materialStream.println("Pr " + specularParams.roughness);
    	}
    	
    	if (param.isNormalTextureEnabled())
    	{
    		materialStream.println("norm " + materialName + "_norm.png");
    	}
    	
    	materialStream.println("d 1.0");
    	materialStream.println("Tr 0.0");
    	materialStream.println("illum 5");
    	
    	materialStream.flush();
    	materialStream.close();
	}

	public void execute() throws IOException
	{	
//		final int DEBUG_PIXEL_X = 322;
//		final int DEBUG_PIXEL_Y = param.getTextureSize() - 365;

    	System.out.println("Max vertex uniform components across all blocks:" + context.getState().getMaxCombinedVertexUniformComponents());
    	System.out.println("Max fragment uniform components across all blocks:" + context.getState().getMaxCombinedFragmentUniformComponents());
    	System.out.println("Max size of a uniform block in bytes:" + context.getState().getMaxUniformBlockSize());
    	System.out.println("Max texture array layers:" + context.getState().getMaxArrayTextureLayers());
		
		System.out.println("Loading view set...");
    	Date timestamp = new Date();
		
    	String[] vsetFileNameParts = vsetFile.getName().split("\\.");
    	String fileExt = vsetFileNameParts[vsetFileNameParts.length-1];
    	if (fileExt.equalsIgnoreCase("vset"))
    	{
    		System.out.println("Loading from VSET file.");
    		
    		viewSet = ViewSet.loadFromVSETFile(vsetFile);
    	}
    	else if (fileExt.equalsIgnoreCase("xml"))
    	{
    		System.out.println("Loading from Agisoft Photoscan XML file.");
    		viewSet = ViewSet.loadFromAgisoftXMLFile(vsetFile);
    	}
    	else
    	{
    		System.out.println("Unrecognized file type, aborting.");
    		return;
    	}
    	
    	viewSet.setTonemapping(param.getGamma(), param.getLinearLuminanceValues(), param.getEncodedLuminanceValues());
    	
    	// Only generate view set uniform buffers
		viewSetResources = IBRResources.getBuilderForContext(context)
				.useExistingViewSet(viewSet)
    			.setLoadOptions(new IBRLoadOptions().setColorImagesRequested(false))
    			.create();
    	
    	auxDir = new File(outputDir, "_aux");
    	auxDir.mkdirs();
    	
    	outputDir.mkdir();
    	if (param.isDebugModeEnabled())
    	{
    		new File(auxDir, "specularResidDebug").mkdir();
    	}
    	
    	System.out.println("Loading view set completed in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
    	
    	if(viewSetResources.luminanceMap == null)
    	{
    		System.out.println("WARNING: no luminance mapping found.  Reflectance values are not physically grounded.");
    	}
		
		context.getState().enableDepthTest();
    	//context.enableBackFaceCulling();
    	
    	try
    	{
	    	compileShaders();
	    	
	    	System.out.println("Loading mesh...");
	    	timestamp = new Date();
	    	
	    	loadMesh();
	    	
	    	System.out.println("Loading mesh completed in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
	    	
	    	tmpDir = new File(outputDir, "tmp");
	    	
	    	System.out.println("Primary view: " + param.getPrimaryViewName());
	    	viewSet.setPrimaryView(param.getPrimaryViewName());
	    	System.out.println("Primary view index: " + viewSet.getPrimaryViewIndex());
	    	
	    	// Load textures, generate visibility depth textures, fit light source and generate shadow depth textures
    		double avgDistance = loadTextures();
    		fitLightSource(avgDistance);
	    	
    		viewSet.setGeometryFileName(objFile.getName());
	    	Path relativePathToRescaledImages = outputDir.toPath().relativize(imageDir.toPath());
	    	viewSet.setRelativeImagePathName(relativePathToRescaledImages.toString());
	    	
	    	FileOutputStream outputStream = new FileOutputStream(new File(outputDir, vsetFile.getName().split("\\.")[0] + ".vset"));
	        viewSet.writeVSETFileToStream(outputStream);
	    	outputStream.flush();
	    	outputStream.close();
	    	
	    	File objFileCopy = new File(outputDir, objFile.getName());
	    	Files.copy(objFile.toPath(), objFileCopy.toPath(), StandardCopyOption.REPLACE_EXISTING);
	    	
			// Phong regression
	    	FramebufferObject<ContextType> diffuseFitFramebuffer = null;
	    	
	    	int subdivSize = param.getTextureSize() / param.getTextureSubdivision();
        
	    	if (!param.isDiffuseTextureEnabled())
	    	{
	    		System.out.println("Skipping diffuse fit.");
	    	}
	    	else
	    	{
	    		System.out.println("Beginning diffuse fit (" + (param.getTextureSubdivision() * param.getTextureSubdivision()) + " blocks)...");
		    	timestamp = new Date();
	    		
	    		diffuseFitFramebuffer = 
        			context.buildFramebufferObject(param.getTextureSize(), param.getTextureSize())
    					.addColorAttachments(4)
    					.createFramebufferObject();
	        	
	    		diffuseFitFramebuffer.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, 0.0f);
		    	diffuseFitFramebuffer.clearColorBuffer(1, 0.0f, 0.0f, 0.0f, 0.0f);
		    	diffuseFitFramebuffer.clearColorBuffer(2, 0.0f, 0.0f, 0.0f, 0.0f);
		    	diffuseFitFramebuffer.clearColorBuffer(3, 0.0f, 0.0f, 0.0f, 0.0f);
	    		
	    		DiffuseFit<ContextType> diffuseFit = createDiffuseFit(diffuseFitFramebuffer, viewSet.getCameraPoseCount(), param.getTextureSubdivision());
	    		
	    		File diffuseTempDirectory = new File(tmpDir, "diffuse");
		    	File normalTempDirectory = new File(tmpDir, "normal");
		    	File normalTSTempDirectory = new File(tmpDir, "normalTS");
	    		
	    		diffuseTempDirectory.mkdir();
		    	normalTempDirectory.mkdir();
		    	normalTSTempDirectory.mkdir();
		    	
		    	if (param.isImagePreprojectionUseEnabled())
		    	{
					final FramebufferObject<ContextType> currentFramebuffer = diffuseFitFramebuffer;
		    		diffuseFit.fitTextureSpace(tmpDir,
	    				(row, col) -> 
	    				{
	    					currentFramebuffer.saveColorBufferToFile(0, col * subdivSize, row * subdivSize, subdivSize, subdivSize, 
    	    		        		"PNG", new File(diffuseTempDirectory, String.format("r%04dc%04d.png", row, col)));
    	    		        
	    					currentFramebuffer.saveColorBufferToFile(1, col * subdivSize, row * subdivSize, subdivSize, subdivSize, 
    	    		        		"PNG", new File(normalTempDirectory, String.format("r%04dc%04d.png", row, col)));
    	    		        
	    					currentFramebuffer.saveColorBufferToFile(3, col * subdivSize, row * subdivSize, subdivSize, subdivSize, 
    	    		        		"PNG", new File(normalTSTempDirectory, String.format("r%04dc%04d.png", row, col)));
    	    		        
    	    		        System.out.println("Block " + (row*param.getTextureSubdivision() + col + 1) + "/" + 
    	    		        		(param.getTextureSubdivision() * param.getTextureSubdivision()) + " completed.");
	    				});
		    	}
		    	else
		    	{
		    		diffuseFit.fitImageSpace(viewTextures, depthTextures, shadowTextures, 
	    				(row, col) ->
    					{
    						System.out.println("Block " + (row*param.getTextureSubdivision() + col + 1) + "/" + 
    	    		        		(param.getTextureSubdivision() * param.getTextureSubdivision()) + " completed.");
    					});
		    	}

		        System.out.println("Diffuse fit completed in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
		    	
		    	System.out.println("Filling empty regions...");
		    	timestamp = new Date();
	    	}
	    	
	    	FramebufferObject<ContextType> backFramebuffer = 
				context.buildFramebufferObject(param.getTextureSize(), param.getTextureSize())
					.addColorAttachments(4)
					.createFramebufferObject();
	    	
	    	Drawable<ContextType> holeFillRenderable = context.createDrawable(holeFillProgram);
	    	VertexBuffer<ContextType> rectBuffer = context.createRectangle();
	    	holeFillRenderable.addVertexBuffer("position", rectBuffer);
	    	
	    	holeFillProgram.setUniform("minFillAlpha", 0.5f);
			
	    	if (param.isDiffuseTextureEnabled())
	    	{
				System.out.println("Diffuse fill...");
		    	
		    	// Diffuse
		    	FramebufferObject<ContextType> frontFramebuffer = diffuseFitFramebuffer;
		    	for (int i = 0; i < param.getTextureSize() / 2; i++)
		    	{
		    		backFramebuffer.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, 1.0f);
		    		backFramebuffer.clearColorBuffer(1, 0.0f, 0.0f, 0.0f, 0.0f);
		    		backFramebuffer.clearColorBuffer(2, 0.0f, 0.0f, 0.0f, 0.0f);
		    		backFramebuffer.clearColorBuffer(3, 0.0f, 0.0f, 0.0f, 0.0f);
		    		
		    		holeFillProgram.setTexture("input0", frontFramebuffer.getColorAttachmentTexture(0));
		    		holeFillProgram.setTexture("input1", frontFramebuffer.getColorAttachmentTexture(1));
		    		holeFillProgram.setTexture("input2", frontFramebuffer.getColorAttachmentTexture(2));
		    		holeFillProgram.setTexture("input3", frontFramebuffer.getColorAttachmentTexture(3));
		    		
		    		holeFillRenderable.draw(PrimitiveMode.TRIANGLE_FAN, backFramebuffer);
		    		context.finish();
		    		
		    		FramebufferObject<ContextType> tmp = frontFramebuffer;
		    		frontFramebuffer = backFramebuffer;
		    		backFramebuffer = tmp;
		    	}
		    	diffuseFitFramebuffer = frontFramebuffer;
		    	
				System.out.println("Empty regions filled in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
		    	System.out.println("Saving textures...");
		    	timestamp = new Date();
		    	
		    	diffuseFitFramebuffer.saveColorBufferToFile(0, "PNG", new File(auxDir, "diffuse-old.png"));
		    	diffuseFitFramebuffer.saveColorBufferToFile(1, "PNG", new File(auxDir, "normal.png"));
		    	//diffuseFitFramebuffer.saveColorBufferToFile(2, "PNG", new File(textureDirectory, "ambient.png"));
		    	diffuseFitFramebuffer.saveColorBufferToFile(3, "PNG", new File(auxDir, "normalts.png"));
		    	
		    	if (!param.isSpecularTextureEnabled())
		    	{
		    		frontFramebuffer.saveColorBufferToFile(0, "PNG", new File(outputDir, materialName + "_Kd.png"));
					
					if (param.isNormalTextureEnabled())
					{
						frontFramebuffer.saveColorBufferToFile(3, "PNG", new File(outputDir, materialName + "_norm.png"));
					}
		    	}
		
		    	System.out.println("Textures saved in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
	    	}
	    	
	    	System.out.println("Fitting specular residual...");
	    	timestamp = new Date();
	    	
			// Estimate the global, average specular parameters.
	    	SpecularParams specularParams = globalSpecularFit(null, null);
				
    		System.out.println("Reflectivity: " + specularParams.reflectivity);
    		System.out.println("Roughness: " + specularParams.roughness);
    		System.out.println("Remainder: " + specularParams.remainder);
			double[] roughnessValues = {specularParams.roughness};
    		//double[] roughnessValues = resample(viewSet, diffuseTexture, normalTexture);
			
			PrintStream specularInfoFile = new PrintStream(new File(auxDir, "specularInfo.txt"));
			specularInfoFile.println("reflectivity " + specularParams.reflectivity);
			specularInfoFile.println("roughness " + specularParams.roughness);
			specularInfoFile.println("remainder " + specularParams.remainder);
			specularInfoFile.close();
				
			if (param.isSpecularTextureEnabled())
	    	{
				System.out.println("Creating specular reflectivity texture...");
				
				NativeVectorBuffer roughnessList = NativeVectorBufferFactory.getInstance().createEmpty(NativeDataType.FLOAT, 1, roughnessValues.length);
				
				for (int i = 0; i < roughnessValues.length; i++)
				{
					roughnessList.set(i, 0, Math.max(0.125f, (float)roughnessValues[i]));
				}
				
				Texture2D<ContextType> roughnessTexture = 
						context.build2DColorTextureFromBuffer(ROUGHNESS_TEXTURE_SIZE, ROUGHNESS_TEXTURE_SIZE, roughnessList)
							.setInternalFormat(ColorFormat.R32F)
							.setLinearFilteringEnabled(true)
							.setMipmapsEnabled(false)
							.createTexture();


		    	FramebufferObject<ContextType> frontFramebuffer = 
	    			context.buildFramebufferObject(param.getTextureSize(), param.getTextureSize())
						.addColorAttachments(4)
						.createFramebufferObject();

				frontFramebuffer.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, 0.0f);
				frontFramebuffer.clearColorBuffer(1, 0.5f, 0.5f, 1.0f, 1.0f); // normal map
				frontFramebuffer.clearColorBuffer(2, 0.0f, 0.0f, 0.0f, 0.0f);
				frontFramebuffer.clearColorBuffer(3, 0.0f, 0.0f, 0.0f, 0.0f);

				backFramebuffer.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, 0.0f);
				backFramebuffer.clearColorBuffer(1, 0.0f, 0.0f, 0.0f, 0.0f);
				backFramebuffer.clearColorBuffer(2, 0.0f, 0.0f, 0.0f, 0.0f);
				backFramebuffer.clearColorBuffer(3, 0.0f, 0.0f, 0.0f, 0.0f);
		    	
				SpecularFit<ContextType> specularFit = createSpecularFit(backFramebuffer, viewSet.getCameraPoseCount(), param.getTextureSubdivision());
				
	    		File diffuseTempDirectory = new File(tmpDir, "diffuse");
	    		diffuseTempDirectory.mkdir();
				
				File specularTempDirectory = new File(tmpDir, "specular");
		    	specularTempDirectory.mkdir();
		    	
		    	if (param.isImagePreprojectionUseEnabled())
		    	{
		    		final FramebufferObject<ContextType> currentFramebuffer = backFramebuffer;
		    		specularFit.fitTextureSpace(tmpDir, 
	    				!param.isDiffuseTextureEnabled() ? frontFramebuffer.getColorAttachmentTexture(0) : diffuseFitFramebuffer.getColorAttachmentTexture(0),
						!param.isDiffuseTextureEnabled() ? frontFramebuffer.getColorAttachmentTexture(1) : diffuseFitFramebuffer.getColorAttachmentTexture(3), 
	    				roughnessTexture,
	    				(row, col) ->
	    				{
	    					currentFramebuffer.saveColorBufferToFile(0, col * subdivSize, row * subdivSize, subdivSize, subdivSize, 
    	    		        		"PNG", new File(specularTempDirectory, String.format("alt_r%04dc%04d.png", row, col)));

	    					currentFramebuffer.saveColorBufferToFile(1, col * subdivSize, row * subdivSize, subdivSize, subdivSize, 
    	    		        		"PNG", new File(diffuseTempDirectory, String.format("alt_r%04dc%04d.png", row, col)));
    	    	    		
    	    	    		System.out.println("Block " + (row*param.getTextureSubdivision() + col + 1) + "/" + 
    	    	    				(param.getTextureSubdivision() * param.getTextureSubdivision()) + " completed.");
	    				});
		    	}
		    	else
		    	{
		    		specularFit.fitImageSpace(viewTextures, depthTextures, shadowTextures, 
	    				!param.isDiffuseTextureEnabled() ? frontFramebuffer.getColorAttachmentTexture(0) : diffuseFitFramebuffer.getColorAttachmentTexture(0),
						!param.isDiffuseTextureEnabled() ? frontFramebuffer.getColorAttachmentTexture(1) : diffuseFitFramebuffer.getColorAttachmentTexture(3), 
	    				roughnessTexture, 
	    				(row, col) -> 
    					{
    						System.out.println("Block " + (row*param.getTextureSubdivision() + col + 1) + "/" + 
    	    	    				(param.getTextureSubdivision() * param.getTextureSubdivision()) + " completed.");
    					});
		    	}
		    	
		    	FramebufferObject<ContextType> tmp = backFramebuffer;
		    	backFramebuffer = frontFramebuffer;
		    	frontFramebuffer = tmp;

		    	frontFramebuffer.saveColorBufferToFile(0, "PNG", new File(auxDir, "diffuse-raw.png"));
		    	frontFramebuffer.saveColorBufferToFile(1, "PNG", new File(auxDir, "normal-raw.png"));
		    	frontFramebuffer.saveColorBufferToFile(2, "PNG", new File(auxDir, "specular-raw.png"));
		    	frontFramebuffer.saveColorBufferToFile(3, "PNG", new File(auxDir, "roughness-raw.png"));
		    	
		    	if (param.isLevenbergMarquardtOptimizationEnabled())
		    	{
					// Non-linear adjustment
					AdjustFit<ContextType> adjustFit = createAdjustFit(viewSet.getCameraPoseCount(), param.getTextureSubdivision());
					ErrorCalc<ContextType> errorCalc = createErrorCalc(viewSet.getCameraPoseCount(), param.getTextureSubdivision());
					Drawable<ContextType> finalizeRenderable = context.createDrawable(finalizeProgram);
					finalizeRenderable.addVertexBuffer("position", rectBuffer);
					
					FramebufferObject<ContextType> frontErrorFramebuffer = 
						context.buildFramebufferObject(param.getTextureSize(), param.getTextureSize())
							.addColorAttachments(ColorFormat.RG32F, 1)
							.addColorAttachments(ColorFormat.R8, 1)
							.createFramebufferObject();

					FramebufferObject<ContextType> backErrorFramebuffer = 
						context.buildFramebufferObject(param.getTextureSize(), param.getTextureSize())
							.addColorAttachments(ColorFormat.RG32F, 1)
							.addColorAttachments(ColorFormat.R8, 1)
							.createFramebufferObject();
					
					frontErrorFramebuffer.clearColorBuffer(0, 128.0f, Float.MAX_VALUE, 0.0f, 0.0f);
		    		backErrorFramebuffer.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, 0.0f);
		    		
		    		if (param.isImagePreprojectionUseEnabled())
			    	{
			    		errorCalc.fitTextureSpace(
		    				backErrorFramebuffer,
		    				tmpDir,
		    				frontFramebuffer.getColorAttachmentTexture(0),
		    				frontFramebuffer.getColorAttachmentTexture(1),
		    				frontFramebuffer.getColorAttachmentTexture(2),
		    				frontFramebuffer.getColorAttachmentTexture(3),
		    				frontErrorFramebuffer.getColorAttachmentTexture(0),
		    				(row, col) ->
		    				{
//		    	    	    		System.out.println("Block " + (row*param.getTextureSubdivision() + col + 1) + "/" + 
//		    	    	    				(param.getTextureSubdivision() * param.getTextureSubdivision()) + " completed.");
		    				});
			    	}
			    	else
			    	{
			    		errorCalc.fitImageSpace(
		    				backErrorFramebuffer,
		    				viewTextures, depthTextures, shadowTextures, 
		    				frontFramebuffer.getColorAttachmentTexture(0),
		    				frontFramebuffer.getColorAttachmentTexture(1),
		    				frontFramebuffer.getColorAttachmentTexture(2),
		    				frontFramebuffer.getColorAttachmentTexture(3),
		    				frontErrorFramebuffer.getColorAttachmentTexture(0),
		    				(row, col) -> 
	    					{
//		    						System.out.println("Block " + (row*param.getTextureSubdivision() + col + 1) + "/" + 
//		    	    	    				(param.getTextureSubdivision() * param.getTextureSubdivision()) + " completed.");
	    					});
			    	}
		    		
		    		context.finish();
		    		
//		    		backErrorFramebuffer.saveColorBufferToFile(0, "PNG", new File(textureDirectory, "error-mask-init.png"));
		    		
		    		tmp = frontErrorFramebuffer;
		    		frontErrorFramebuffer = backErrorFramebuffer;
		    		backErrorFramebuffer = tmp;
		    		
		    		double lastSumSqError = 0.0;
		    		float[] errorData = frontErrorFramebuffer.readFloatingPointColorBufferRGBA(0);
		    		for (int j = 0; j * 4 + 3 < errorData.length; j++)
		    		{
		    			lastSumSqError += errorData[j * 4 + 1]; // Green channel holds squared error
		    		}
		    		
		    		
		    		System.out.println("Sum squared error: " + lastSumSqError);
		    		
		    		System.out.println("Adjusting fit...");
		    		
		    		boolean saveDebugTextures = false;
		    		boolean useGlobalDampingFactor = false;
		    		float globalDampingFactor = 128.0f;
					
					for (int i = 0; globalDampingFactor <= 1048576; i++)
					{
			    		backFramebuffer.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, 0.0f);
			    		backFramebuffer.clearColorBuffer(1, 0.0f, 0.0f, 0.0f, 0.0f);
			    		backFramebuffer.clearColorBuffer(2, 0.0f, 0.0f, 0.0f, 0.0f);
			    		backFramebuffer.clearColorBuffer(3, 0.0f, 0.0f, 0.0f, 0.0f);
			    		
			    		if(useGlobalDampingFactor)
			    		{
			    			// hack to override damping factor and never discard the result - TODO make this more elegant
			    			frontErrorFramebuffer.clearColorBuffer(0, globalDampingFactor, Float.MAX_VALUE, 0.0f, 0.0f);
			    		}
			    		
			    		if (param.isImagePreprojectionUseEnabled())
				    	{
				    		adjustFit.fitTextureSpace(
			    				backFramebuffer,
			    				tmpDir,
			    				frontFramebuffer.getColorAttachmentTexture(0),
			    				frontFramebuffer.getColorAttachmentTexture(1),
			    				frontFramebuffer.getColorAttachmentTexture(2),
			    				frontFramebuffer.getColorAttachmentTexture(3),
			    				frontErrorFramebuffer.getColorAttachmentTexture(0),
			    				(row, col) ->
			    				{
//			    					currentFramebuffer.saveColorBufferToFile(0, col * subdivSize, row * subdivSize, subdivSize, subdivSize, 
//		    	    		        		"PNG", new File(diffuseTempDirectory, String.format("alt_r%04dc%04d.png", row, col)));
//
//			    					currentFramebuffer.saveColorBufferToFile(2, col * subdivSize, row * subdivSize, subdivSize, subdivSize, 
//		    	    		        		"PNG", new File(specularTempDirectory, String.format("alt_r%04dc%04d.png", row, col)));
		    	    	    		
//			    	    	    		System.out.println("Block " + (row*param.getTextureSubdivision() + col + 1) + "/" + 
//			    	    	    				(param.getTextureSubdivision() * param.getTextureSubdivision()) + " completed.");
			    				});
				    	}
				    	else
				    	{
				    		adjustFit.fitImageSpace(
			    				backFramebuffer,
			    				viewTextures, depthTextures, shadowTextures, 
			    				frontFramebuffer.getColorAttachmentTexture(0),
			    				frontFramebuffer.getColorAttachmentTexture(1),
			    				frontFramebuffer.getColorAttachmentTexture(2),
			    				frontFramebuffer.getColorAttachmentTexture(3),
			    				frontErrorFramebuffer.getColorAttachmentTexture(0),
			    				(row, col) -> 
		    					{
//			    						System.out.println("Block " + (row*param.getTextureSubdivision() + col + 1) + "/" + 
//			    	    	    				(param.getTextureSubdivision() * param.getTextureSubdivision()) + " completed.");
		    					});
				    	}
			    		
			    		context.finish();
			    		
			    		if (saveDebugTextures)
				    	{
					    	backFramebuffer.saveColorBufferToFile(0, "PNG", new File(auxDir, "diffuse-test1.png"));
				    		backFramebuffer.saveColorBufferToFile(1, "PNG", new File(auxDir, "normal-test1.png"));
				    		backFramebuffer.saveColorBufferToFile(2, "PNG", new File(auxDir, "specular-test1.png"));
				    		backFramebuffer.saveColorBufferToFile(3, "PNG", new File(auxDir, "roughness-test1.png"));
				    	}
			    		
			    		backErrorFramebuffer.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, 0.0f);
			    		
			    		if (param.isImagePreprojectionUseEnabled())
				    	{
				    		errorCalc.fitTextureSpace(
			    				backErrorFramebuffer,
			    				tmpDir,
			    				backFramebuffer.getColorAttachmentTexture(0),
			    				backFramebuffer.getColorAttachmentTexture(1),
			    				backFramebuffer.getColorAttachmentTexture(2),
			    				backFramebuffer.getColorAttachmentTexture(3),
			    				frontErrorFramebuffer.getColorAttachmentTexture(0),
			    				(row, col) ->
			    				{
//			    	    	    		System.out.println("Block " + (row*param.getTextureSubdivision() + col + 1) + "/" + 
//			    	    	    				(param.getTextureSubdivision() * param.getTextureSubdivision()) + " completed.");
			    				});
				    	}
				    	else
				    	{
				    		errorCalc.fitImageSpace(
			    				backErrorFramebuffer,
			    				viewTextures, depthTextures, shadowTextures, 
			    				backFramebuffer.getColorAttachmentTexture(0),
			    				backFramebuffer.getColorAttachmentTexture(1),
			    				backFramebuffer.getColorAttachmentTexture(2),
			    				backFramebuffer.getColorAttachmentTexture(3),
			    				frontErrorFramebuffer.getColorAttachmentTexture(0),
			    				(row, col) -> 
		    					{
//			    						System.out.println("Block " + (row*param.getTextureSubdivision() + col + 1) + "/" + 
//			    	    	    				(param.getTextureSubdivision() * param.getTextureSubdivision()) + " completed.");
		    					});
				    	}
			    		
			    		context.finish();
			    		
			    		if (saveDebugTextures)
				    	{
					    	backErrorFramebuffer.saveColorBufferToFile(0, "PNG", new File(auxDir, "error-mask-test.png"));
				    	}
			    		
			    		tmp = frontErrorFramebuffer;
			    		frontErrorFramebuffer = backErrorFramebuffer;
			    		backErrorFramebuffer = tmp;
			    		
			    		double sumSqError = 0.0;
			    		errorData = frontErrorFramebuffer.readFloatingPointColorBufferRGBA(0);
			    		for (int j = 0; j * 4 + 3 < errorData.length; j++)
			    		{
			    			sumSqError += errorData[j * 4 + 1]; // Green channel holds squared error
			    		}
			    		
			    		System.out.println("Sum squared error: " + sumSqError);
			    		
			    		if (sumSqError < lastSumSqError)
			    		{
			    			lastSumSqError = sumSqError;
			    			
			    			System.out.println("Saving iteration.");
			    			
			    			if (useGlobalDampingFactor)
			    			{
			    				globalDampingFactor /= 2;
				    			
				    			System.out.println("Next damping factor: " + globalDampingFactor);
				    			
				    			// Set the mask framebuffer to all 1 (hack - TODO make this more elegant)
				    			frontErrorFramebuffer.clearColorBuffer(1, 1.0f, 1.0f, 1.0f, 1.0f);
			    			}
			    			else
			    			{
			    				// If the damping factor isn't being used, set to the minimum, which will function as a countdown if an iteration is unproductive.
			    				globalDampingFactor = 0.0078125f;
			    			}
			    		
				    		finalizeProgram.setTexture("input0", backFramebuffer.getColorAttachmentTexture(0));
				    		finalizeProgram.setTexture("input1", backFramebuffer.getColorAttachmentTexture(1));
				    		finalizeProgram.setTexture("input2", backFramebuffer.getColorAttachmentTexture(2));
				    		finalizeProgram.setTexture("input3", backFramebuffer.getColorAttachmentTexture(3));
				    		finalizeProgram.setTexture("alphaMask", frontErrorFramebuffer.getColorAttachmentTexture(1));
				    		
				    		finalizeRenderable.draw(PrimitiveMode.TRIANGLE_FAN, frontFramebuffer);
				    		context.finish();
			    		}
			    		else
			    		{
			    			// If useGlobalDampingFactor == false, then this effectively serves as a countdown in the case of an unproductive iteration.
			    			// If enough unproductive iterations occur, then this variable will keep doubling until it exceeds the maximum value.
			    			globalDampingFactor *= 2;
			    			
			    			System.out.println("Discarding iteration.");
			    			System.out.println("Next damping factor: " + globalDampingFactor);
			    		}

			    		if (saveDebugTextures)
				    	{
				    		frontFramebuffer.saveColorBufferToFile(0, "PNG", new File(auxDir, "diffuse-test2.png"));
				    		frontFramebuffer.saveColorBufferToFile(1, "PNG", new File(auxDir, "normal-test2.png"));
				    		frontFramebuffer.saveColorBufferToFile(2, "PNG", new File(auxDir, "specular-test2.png"));
				    		frontFramebuffer.saveColorBufferToFile(3, "PNG", new File(auxDir, "roughness-test2.png"));
				    	}
			    		
			    		System.out.println("Iteration " + (i+1) + " complete.");
		    			System.out.println();
					}
					
		    		frontErrorFramebuffer.saveColorBufferToFile(0, "PNG", new File(auxDir, "error-mask-final.png"));
			    	
		    		// TODO refactor all references to close() in this class to use try-with-resources.
			    	frontErrorFramebuffer.close();
			    	backErrorFramebuffer.close();
		    	}
			
				// Fill holes
				for (int i = 0; i < param.getTextureSize() / 2; i++)
		    	{
		    		backFramebuffer.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, 0.0f);
		    		backFramebuffer.clearColorBuffer(1, 0.0f, 0.0f, 0.0f, 0.0f);
		    		backFramebuffer.clearColorBuffer(2, 0.0f, 0.0f, 0.0f, 0.0f);
		    		backFramebuffer.clearColorBuffer(3, 0.0f, 0.0f, 0.0f, 0.0f);
		    		
		    		holeFillProgram.setTexture("input0", frontFramebuffer.getColorAttachmentTexture(0));
		    		holeFillProgram.setTexture("input1", frontFramebuffer.getColorAttachmentTexture(1));
		    		holeFillProgram.setTexture("input2", frontFramebuffer.getColorAttachmentTexture(2));
		    		holeFillProgram.setTexture("input3", frontFramebuffer.getColorAttachmentTexture(3));
		    		
		    		holeFillRenderable.draw(PrimitiveMode.TRIANGLE_FAN, backFramebuffer);
		    		context.finish();
		    		
		    		tmp = frontFramebuffer;
		    		frontFramebuffer = backFramebuffer;
		    		backFramebuffer = tmp;
		    	}
				
				// Save a copy of all of the channels, even the ones that shouldn't be needed, in the _aux folder.
				frontFramebuffer.saveColorBufferToFile(0, "PNG", new File(auxDir, "diffuse-final.png"));
	    		frontFramebuffer.saveColorBufferToFile(1, "PNG", new File(auxDir, "normal-final.png"));
	    		frontFramebuffer.saveColorBufferToFile(2, "PNG", new File(auxDir, "specular-final.png"));
	    		frontFramebuffer.saveColorBufferToFile(3, "PNG", new File(auxDir, "roughness-final.png"));

				if (param.isDiffuseTextureEnabled())
				{
					frontFramebuffer.saveColorBufferToFile(0, "PNG", new File(outputDir, materialName + "_Kd.png"));
					
					if (param.isNormalTextureEnabled())
					{
						frontFramebuffer.saveColorBufferToFile(1, "PNG", new File(outputDir, materialName + "_norm.png"));
					}
				}
				
				frontFramebuffer.saveColorBufferToFile(2, "PNG", new File(outputDir, materialName + "_Ks.png"));
				frontFramebuffer.saveColorBufferToFile(3, "PNG", new File(outputDir, materialName + "_Pr.png"));
    	        
    	    	frontFramebuffer.close();
			}

	    	backFramebuffer.close();
	    	
	    	writeMTLFile(specularParams);
			
			if (viewTextures != null)
	        {
	        	viewTextures.close();
	        }
	        
	        if (depthTextures != null)
	        {
	        	depthTextures.close();
	        }
	        
	        if (shadowTextures != null)
	        {
	        	shadowTextures.close();
	        }
	        
	        lightPositionBuffer.close();
	        lightIntensityBuffer.close();
	        
	        if (shadowMatrixBuffer != null)
	        {
	        	shadowMatrixBuffer.close();
	        }
	        
	        if (rectBuffer != null)
	        {
	        	rectBuffer.close();
	        }
	    	
	        if (param.isDiffuseTextureEnabled())
	        {
	        	diffuseFitFramebuffer.close();
	        }
	
	    	//System.out.println("Resampling completed in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
    	}
    	finally
    	{
			if (projTexProgram != null) projTexProgram.close();
			if (diffuseFitProgram != null) diffuseFitProgram.close();
			if (specularFitProgram != null) specularFitProgram.close();
			if (diffuseDebugProgram != null) diffuseDebugProgram.close();
			if (specularDebugProgram != null) specularDebugProgram.close();
			if (depthRenderingProgram != null) depthRenderingProgram.close();
			
			if (viewSet != null) viewSetResources.close();
			if (positionBuffer != null) positionBuffer.close();
			if (normalBuffer != null) normalBuffer.close();
			if (texCoordBuffer != null) texCoordBuffer.close();
			if (tangentBuffer != null) tangentBuffer.close();
    	}
	}
}