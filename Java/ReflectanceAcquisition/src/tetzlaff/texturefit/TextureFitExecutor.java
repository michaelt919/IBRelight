package tetzlaff.texturefit;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;

import javax.imageio.ImageIO;

import org.ujmp.core.Matrix;
import org.ujmp.core.SparseMatrix;

import tetzlaff.gl.ColorFormat;
import tetzlaff.gl.Context;
import tetzlaff.gl.Framebuffer;
import tetzlaff.gl.FramebufferObject;
import tetzlaff.gl.PrimitiveMode;
import tetzlaff.gl.Program;
import tetzlaff.gl.Renderable;
import tetzlaff.gl.ShaderType;
import tetzlaff.gl.Texture2D;
import tetzlaff.gl.Texture3D;
import tetzlaff.gl.UniformBuffer;
import tetzlaff.gl.VertexBuffer;
import tetzlaff.gl.builders.framebuffer.ColorAttachmentSpec;
import tetzlaff.gl.helpers.FloatVertexList;
import tetzlaff.gl.helpers.Matrix3;
import tetzlaff.gl.helpers.Matrix4;
import tetzlaff.gl.helpers.Vector2;
import tetzlaff.gl.helpers.Vector3;
import tetzlaff.gl.helpers.Vector4;
import tetzlaff.gl.helpers.VertexMesh;
import tetzlaff.ulf.ViewSet;

public class TextureFitExecutor<ContextType extends Context<ContextType>>
{
	// Debug parameters
	private static final boolean DEBUG = false;

	private ContextType context;
	private File vsetFile;
	private File objFile;
	private File imageDir;
	private File maskDir;
	private File rescaleDir;
	private File outputDir;
	private Vector3 lightOffset;
	private Vector3 lightIntensity;
	private TextureFitParameters param;
	
	private Program<ContextType> depthRenderingProgram;
	private Program<ContextType> projTexProgram;
	private Program<ContextType> lightFitProgram;
	private Program<ContextType> diffuseFitProgram;
	private Program<ContextType> specularFitProgram;
	private Program<ContextType> diffuseDebugProgram;
	private Program<ContextType> specularDebugProgram;
	private Program<ContextType> textureRectProgram;
	private Program<ContextType> holeFillProgram;
	
	private VertexBuffer<ContextType> positionBuffer;
	private VertexBuffer<ContextType> texCoordBuffer;
	private VertexBuffer<ContextType> normalBuffer;
	
	public TextureFitExecutor(ContextType context, File vsetFile, File objFile, File imageDir, File maskDir, File rescaleDir, File outputDir,
			Vector3 lightOffset, Vector3 lightIntensity, TextureFitParameters param) 
	{
		this.context = context;
		this.vsetFile = vsetFile;
		this.objFile = objFile;
		this.imageDir = imageDir;
		this.maskDir = maskDir;
		this.rescaleDir = rescaleDir;
		this.outputDir = outputDir;
		this.lightOffset = lightOffset;
		this.lightIntensity = lightIntensity;
		this.param = param;
	}
	
	private interface TextureSpaceCallback<ContextType extends Context<ContextType>>
	{
		void execute(Framebuffer<ContextType> framebuffer, int subdivRow, int subdivCol);
	}
	
	private void projectIntoTextureSpace(ViewSet<ContextType> viewSet, int viewIndex, int textureSize, int textureSubdiv, TextureSpaceCallback<ContextType> callback) throws IOException
	{
		FramebufferObject<ContextType> projTexFBO = 
			context.getFramebufferObjectBuilder(textureSize / textureSubdiv, textureSize / textureSubdiv)
				.addColorAttachments(ColorFormat.RGBA32F, 2)
				.createFramebufferObject();
    	Renderable<ContextType> projTexRenderable = context.createRenderable(projTexProgram);
    	
    	projTexRenderable.addVertexBuffer("position", positionBuffer);
    	projTexRenderable.addVertexBuffer("texCoord", texCoordBuffer);
    	projTexRenderable.addVertexBuffer("normal", normalBuffer);
    	
    	projTexRenderable.program().setUniform("occlusionEnabled", param.isCameraVisibilityTestEnabled());
    	projTexRenderable.program().setUniform("occlusionBias", param.getCameraVisibilityTestBias());
    	
    	File imageFile = new File(imageDir, viewSet.getImageFileName(viewIndex));
		if (!imageFile.exists())
		{
			String[] filenameParts = viewSet.getImageFileName(viewIndex).split("\\.");
	    	filenameParts[filenameParts.length - 1] = "png";
	    	String pngFileName = String.join(".", filenameParts);
	    	imageFile = new File(imageDir, pngFileName);
		}
    	
    	Texture2D<ContextType> viewTexture;
    	if (maskDir == null)
    	{
    		viewTexture = context.get2DColorTextureBuilder(imageFile, true)
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
			
    		viewTexture = context.get2DColorTextureBuilder(imageFile, maskFile, true)
    						.setLinearFilteringEnabled(true)
    						.setMipmapsEnabled(true)
    						.createTexture();
    	}
    	
    	FramebufferObject<ContextType> depthFBO = 
			context.getFramebufferObjectBuilder(viewTexture.getWidth(), viewTexture.getHeight())
				.addDepthAttachment()
				.createFramebufferObject();
    	
    	Renderable<ContextType> depthRenderable = context.createRenderable(depthRenderingProgram);
    	depthRenderable.addVertexBuffer("position", positionBuffer);
    	
    	depthRenderingProgram.setUniform("model_view", viewSet.getCameraPose(viewIndex));
		depthRenderingProgram.setUniform("projection", 
			viewSet.getCameraProjection(viewSet.getCameraProjectionIndex(viewIndex))
				.getProjectionMatrix(
					viewSet.getRecommendedNearPlane(), 
					viewSet.getRecommendedFarPlane()
				)
		);
    	
		depthFBO.clearDepthBuffer();
    	depthRenderable.draw(PrimitiveMode.TRIANGLES, depthFBO);
    	
    	projTexRenderable.program().setUniform("cameraPose", viewSet.getCameraPose(viewIndex));
    	projTexRenderable.program().setUniform("cameraProjection", 
    			viewSet.getCameraProjection(viewSet.getCameraProjectionIndex(viewIndex))
    				.getProjectionMatrix(viewSet.getRecommendedNearPlane(), viewSet.getRecommendedFarPlane()));
    	
    	projTexRenderable.program().setTexture("viewImage", viewTexture);
    	projTexRenderable.program().setTexture("depthImage", depthFBO.getDepthAttachmentTexture());
	
    	for (int row = 0; row < textureSubdiv; row++)
    	{
	    	for (int col = 0; col < textureSubdiv; col++)
    		{
	    		projTexRenderable.program().setUniform("minTexCoord", 
	    				new Vector2((float)col / (float)textureSubdiv, (float)row / (float)textureSubdiv));
	    		
	    		projTexRenderable.program().setUniform("maxTexCoord", 
	    				new Vector2((float)(col+1) / (float)textureSubdiv, (float)(row+1) / (float)textureSubdiv));
	    		
	    		projTexFBO.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, 0.0f);
	    		projTexFBO.clearColorBuffer(1, 0.0f, 0.0f, 0.0f, 0.0f);
	    		projTexFBO.clearDepthBuffer();
	    		projTexRenderable.draw(PrimitiveMode.TRIANGLES, projTexFBO);
	    		
	    		callback.execute(projTexFBO, row, col);
	    	}
		}
    	
    	viewTexture.delete();
    	depthFBO.delete();
	}
	
	private void initResampleMatrix(Matrix matrix, int texWidth, int texHeight, int n)
	{
		float regularizationWeight = 1.0f; // TODO(?)
		
		// Initialize regularization constraints
		long indexBase = 0;
		for (int y = 0; y < texHeight - 1; y++)
		{
			for (int x = 0; x < texWidth - 1; x++)
			{
				long indexCurrent = indexBase;
				long indexRight = indexBase + n*n;
				long indexBelow = indexBase + n*n * texWidth;
				
				for (int p = 0; p < n*n; p++)
				{
					matrix.setAsFloat(matrix.getAsFloat(indexCurrent, indexCurrent) + 2 * regularizationWeight, indexCurrent, indexCurrent);
					matrix.setAsFloat(matrix.getAsFloat(indexRight, indexRight) + regularizationWeight, indexRight, indexRight);
					matrix.setAsFloat(matrix.getAsFloat(indexBelow, indexBelow) + regularizationWeight, indexBelow, indexBelow);
					matrix.setAsFloat(matrix.getAsFloat(indexCurrent, indexRight) - regularizationWeight, indexCurrent, indexRight);
					matrix.setAsFloat(matrix.getAsFloat(indexCurrent, indexBelow) - regularizationWeight, indexCurrent, indexBelow);
					matrix.setAsFloat(matrix.getAsFloat(indexRight, indexCurrent) - regularizationWeight, indexRight, indexCurrent);
					matrix.setAsFloat(matrix.getAsFloat(indexBelow, indexCurrent) - regularizationWeight, indexBelow, indexCurrent);
					
					indexCurrent++;
					indexRight++;
					indexBelow++;
				}
				
				indexBase += n*n;
			}
		}
	}
	
	private void accumResampleSystem(Matrix lhs, Matrix rhs, int texWidth, int texHeight, int n, float rMax, float[] colorDataRGBA, float[] halfAngleDataTBNA)
	{
		int indexBase = 0;
		
		for (int y = 0; y < texHeight; y++)
		{
			for (int x = 0; x < texWidth; x++)
			{
				float red = colorDataRGBA[((y*texWidth) + x) * 4];
				float green = colorDataRGBA[((y*texWidth) + x) * 4 + 1];
				float blue = colorDataRGBA[((y*texWidth) + x) * 4 + 2];
				float alpha = colorDataRGBA[((y*texWidth) + x) * 4 + 3];
				
				if (alpha > 0.0f)
				{
					// the tangent and bitangent components of the half-vector
					float u = halfAngleDataTBNA[((y*texWidth) + x) * 4];
					float v = halfAngleDataTBNA[((y*texWidth) + x) * 4 + 1]; 
		
					float r = (float)Math.sqrt(u*u+v*v);
					
					if (r <= rMax)
					{
						float scale = (n-1) / (rMax) * r / Math.max(Math.abs(u), Math.abs(v));
						
						// Mapped coordinates for the specified half-vector
						float iReal = scale * 0.5f * (1 + u);
						float jReal = scale * 0.5f * (1 + v);
		
						// Coordinate indices of the nearest sample points
						int iRight = (int)Math.ceil(iReal);
						int iLeft = (int)Math.floor(iReal);
		
						int jAbove = (int)Math.ceil(jReal);
						int jBelow = (int)Math.floor(jReal);
						
						// Indices of the sample points in the flattened vector
						long[] indices = {
							indexBase + iRight + jAbove * n,
							indexBase + iRight + jBelow * n,
							indexBase + iLeft + jAbove * n,
							indexBase + iLeft + jBelow * n
						};
						
						// Interpolation coefficients
						float s, t;
						
						if (iRight == iLeft)
						{
							s = 0.0f;
						}
						else
						{
							s = (iReal - iLeft) / (iRight - iLeft);
						}
						
						if (jAbove == jBelow)
						{
							t = 0.0f;
						}
						else
						{
							t = (jReal - jBelow) / (jAbove - jBelow);
						}
						
						// Blending weights
						float[] weights = { s * t, s * (1 - t), (1 - s) * t, (1 - s) * (1 - t) };
						
						// Accumulate in the least squares system
						for (int q1 = 0; q1 < 4; q1++)
						{
							for (int q2 = 0; q2 < 4; q2++)
							{
								lhs.setAsFloat(lhs.getAsFloat(indices[q1], indices[q2]) + weights[q1] * weights[q2], indices[q1], indices[q2]);
							}
							
							rhs.setAsFloat(rhs.getAsFloat(indices[q1], 0) + weights[q1] * red, indices[q1], 0);
							rhs.setAsFloat(rhs.getAsFloat(indices[q1], 1) + weights[q1] * green, indices[q1], 1);
							rhs.setAsFloat(rhs.getAsFloat(indices[q1], 2) + weights[q1] * blue, indices[q1], 2);
						}
					}
				}

				indexBase += n*n;
			}
		}
	}
	
	private void resample(ViewSet<ContextType> viewSet) throws IOException
	{
		System.out.println("Resampling...");
		
		int n = 8; // TODO
		
		int texSize = 256;

		// REALLY big matrices (but sparse)
		int dim = n*n*texSize*texSize;
		Matrix mTm = SparseMatrix.Factory.zeros(dim, dim);
		Matrix mTx = SparseMatrix.Factory.zeros(dim, 3);
		
		// The greatest projection the half vector can have on the tangent plane before specularity is assumed to be negligible.
		float rMax = 0.5f * (float)Math.sqrt(3); //(float)Math.min(0.5*Math.sqrt(3), Math.sqrt(Math.sqrt(2) * maxRmsSlope));
		
		System.out.println("Initializing regularization constraints...");
		
		initResampleMatrix(mTm, texSize, texSize, n);
		
		System.out.println("Adding view samples...");
		
		for (int k = 0; k < viewSet.getCameraPoseCount(); k++)
		{
			projectIntoTextureSpace(viewSet, k, texSize, 1, 
				(framebuffer, row, col) -> 
				{
					if (DEBUG)
			    	{
						try
	    				{
	    					framebuffer.saveColorBufferToFile(0, "PNG", new File(new File(outputDir, "debug"), String.format("colors%04d.png", k)));
	    					framebuffer.saveColorBufferToFile(1, "PNG", new File(new File(outputDir, "debug"), String.format("halfangle%04d.png", k)));
	    				}
	    				catch (IOException e)
	    				{
	    					e.printStackTrace();
	    				}
			    	}
				});
					//accumResampleSystem(mTm, mTx, texSize, texSize, n, rMax, 
						//framebuffer.readFloatingPointColorBufferRGBA(0), 
						//framebuffer.readFloatingPointColorBufferRGBA(1)));
			
	    	System.out.println("Completed " + (k+1) + "/" + viewSet.getCameraPoseCount() + " views...");
		}
		
		System.out.println("Solving system...");
    		
		// Solve using Cholesky decomposition
		Matrix solution = Matrix.chol.solve(mTm, mTx);
		
		
	}

	public void execute() throws IOException
	{
		final int DEBUG_PIXEL_X = 322;
		final int DEBUG_PIXEL_Y = param.getTextureSize() - 365;

    	System.out.println("Max vertex uniform components across all blocks:" + context.getMaxCombinedVertexUniformComponents());
    	System.out.println("Max fragment uniform components across all blocks:" + context.getMaxCombinedFragmentUniformComponents());
    	System.out.println("Max size of a uniform block in bytes:" + context.getMaxUniformBlockSize());
    	System.out.println("Max texture array layers:" + context.getMaxArrayTextureLayers());
		
		System.out.println("Loading view set...");
    	Date timestamp = new Date();
		
    	ViewSet<ContextType> viewSet = null;
    	String[] vsetFileNameParts = vsetFile.getName().split("\\.");
    	String fileExt = vsetFileNameParts[vsetFileNameParts.length-1];
    	if (fileExt.equalsIgnoreCase("vset"))
    	{
    		System.out.println("Loading from VSET file.");
    		viewSet = ViewSet.loadFromVSETFile(vsetFile, context);
    	}
    	else if (fileExt.equalsIgnoreCase("xml"))
    	{
    		System.out.println("Loading from Agisoft Photoscan XML file.");
    		viewSet = ViewSet.loadFromAgisoftXMLFile(vsetFile, null, lightOffset, lightIntensity, context, null);
    	}
    	else
    	{
    		System.out.println("Unrecognized file type, aborting.");
    		return;
    	}
    	
    	outputDir.mkdir();
    	if (DEBUG)
    	{
    		new File(outputDir, "debug").mkdir();
    	}
    	
    	System.out.println("Loading view set completed in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
    	
    	System.out.println("Loading and compiling shader programs...");
    	timestamp = new Date();
		
		context.enableDepthTest();
    	context.enableBackFaceCulling();
    	
    	depthRenderingProgram = context.getShaderProgramBuilder()
    			.addShader(ShaderType.VERTEX, new File("shaders/common/depth.vert"))
    			.addShader(ShaderType.FRAGMENT, new File("shaders/common/depth.frag"))
    			.createProgram();
    	
    	projTexProgram = context.getShaderProgramBuilder()
    			.addShader(ShaderType.VERTEX, new File("shaders", "common/texspace.vert"))
    			.addShader(ShaderType.FRAGMENT, new File("shaders", "reflectance/projtex_single.frag"))
    			.createProgram();
    	
    	lightFitProgram = context.getShaderProgramBuilder()
    			.addShader(ShaderType.VERTEX, new File("shaders", "common/texspace.vert"))
    			.addShader(ShaderType.FRAGMENT, new File("shaders", param.isImagePreprojectionUseEnabled() ? 
    					"texturefit/lightfit_texspace.frag" : "texturefit/lightfit_imgspace.frag"))
    			.createProgram();
    	
    	diffuseFitProgram = context.getShaderProgramBuilder()
    			.addShader(ShaderType.VERTEX, new File("shaders", "common/texspace.vert"))
    			.addShader(ShaderType.FRAGMENT, new File("shaders", param.isImagePreprojectionUseEnabled() ? 
    					"texturefit/diffusefit_texspace.frag" : "texturefit/diffusefit_imgspace.frag"))
    			.createProgram();
		
    	specularFitProgram = context.getShaderProgramBuilder()
    			.addShader(ShaderType.VERTEX, new File("shaders", "common/texspace.vert"))
    			.addShader(ShaderType.FRAGMENT, new File("shaders", param.isImagePreprojectionUseEnabled() ? 
    					"texturefit/specularfit_texspace.frag" : "texturefit/specularfit_imgspace.frag"))
    			.createProgram();
		
    	diffuseDebugProgram = context.getShaderProgramBuilder()
    			.addShader(ShaderType.VERTEX, new File("shaders", "common/texspace.vert"))
    			.addShader(ShaderType.FRAGMENT, new File("shaders", "reflectance/projtex_multi.frag"))
    			.createProgram();
		
    	specularDebugProgram = context.getShaderProgramBuilder()
    			.addShader(ShaderType.VERTEX, new File("shaders", "common/texspace.vert"))
    			.addShader(ShaderType.FRAGMENT, new File("shaders", "texturefit/speculardebug_imgspace.frag"))
    			.createProgram();
		
    	textureRectProgram = context.getShaderProgramBuilder()
    			.addShader(ShaderType.VERTEX, new File("shaders", "common/texture.vert"))
    			.addShader(ShaderType.FRAGMENT, new File("shaders", "common/texture.frag"))
    			.createProgram();
		
    	holeFillProgram = context.getShaderProgramBuilder()
    			.addShader(ShaderType.VERTEX, new File("shaders", "common/texture.vert"))
    			.addShader(ShaderType.FRAGMENT, new File("shaders", "texturefit/holefill.frag"))
    			.createProgram();
		
    	System.out.println("Shader compilation completed in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
    	
    	System.out.println("Loading mesh...");
    	timestamp = new Date();
    	
    	VertexMesh mesh = new VertexMesh("OBJ", objFile);
    	positionBuffer = context.createVertexBuffer().setData(mesh.getVertices());
    	texCoordBuffer = context.createVertexBuffer().setData(mesh.getTexCoords());
    	normalBuffer = context.createVertexBuffer().setData(mesh.getNormals());
    	
    	System.out.println("Loading mesh completed in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");

    	System.out.println("Resampling...");
    	timestamp = new Date();
    	
		// Resample the reflectance data
		resample(viewSet);

    	System.out.println("Resampling completed in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
    	
    	File tmpDir = new File(outputDir, "tmp");
    	
    	Texture3D<ContextType> viewTextures = null;
    	Texture3D<ContextType> depthTextures = null;
    	Texture3D<ContextType> shadowTextures = null;
    	
    	if (param.isImagePreprojectionUseEnabled() && param.isImagePreprojectionGenerationEnabled())
    	{
    		System.out.println("Pre-projecting images into texture space...");
	    	timestamp = new Date();
	    	
	    	tmpDir.mkdir();
	    	
	    	for (int i = 0; i < viewSet.getCameraPoseCount(); i++)
	    	{
	    		File viewDir = new File(tmpDir, String.format("%04d", i));
	        	viewDir.mkdir();
	        	
	    		projectIntoTextureSpace(viewSet, i, param.getTextureSize(), param.getTextureSubdivision(), 
	    			(framebuffer, row, col) -> 
    				{
	    				try
	    				{
	    					framebuffer.saveColorBufferToFile(0, "PNG", new File(viewDir, String.format("r%04dc%04d.png", row, col)));
	    				}
	    				catch (IOException e)
	    				{
	    					e.printStackTrace();
	    				}
					});
		    	
		    	System.out.println("Completed " + (i+1) + "/" + viewSet.getCameraPoseCount());
	    	}
	    	
	    	System.out.println("Pre-projections completed in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
    	}
    	else
    	{
    		if (param.isImageRescalingEnabled())
    		{
    			System.out.println("Loading and rescaling images...");
    			timestamp = new Date();
    			
    			viewTextures = context.get2DColorTextureArrayBuilder(param.getImageWidth(), param.getImageHeight(), viewSet.getCameraPoseCount())
    							.setLinearFilteringEnabled(true)
    							//.setMipmapsEnabled(true)
    							.createTexture();
    			
				// Create an FBO for downsampling
		    	FramebufferObject<ContextType> downsamplingFBO = 
	    			context.getFramebufferObjectBuilder(param.getImageWidth(), param.getImageHeight())
	    				.addEmptyColorAttachment()
	    				.createFramebufferObject();
		    	
		    	Renderable<ContextType> downsampleRenderable = context.createRenderable(textureRectProgram);
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
		    			fullSizeImage = context.get2DColorTextureBuilder(imageFile, true)
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
						
		    			fullSizeImage = context.get2DColorTextureBuilder(imageFile, maskFile, true)
											.setLinearFilteringEnabled(true)
											.setMipmapsEnabled(true)
											.createTexture();
		    		}
		    		
		    		downsamplingFBO.setColorAttachment(0, viewTextures.getLayerAsFramebufferAttachment(i));
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
		        	
		        	fullSizeImage.delete();
		        	
					System.out.println((i+1) + "/" + viewSet.getCameraPoseCount() + " images loaded and rescaled.");
		    	}
	
		    	rectBuffer.delete();
		    	downsamplingFBO.delete();
		    	
		    	// TODO why don't mipmaps work?
		    	//viewTextures.generateMipmaps();
		    	//context.finish();
		    	
	    		System.out.println("Image loading and rescaling completed in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
	    		
	    		System.err.println("Warning: Image rescaling is buggy; it may be necessary to reload the rescaled images and execute again."); // TODO fix this
    		}
    		else
    		{
	    		System.out.println("Loading images...");
		    	timestamp = new Date();
		    	
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
				viewTextures = context.get2DColorTextureArrayBuilder(img.getWidth(), img.getHeight(), viewSet.getCameraPoseCount())
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
    		}
    		
    		System.out.println("Creating depth maps...");
	    	timestamp = new Date();
	    	
	    	// Build depth textures for each view
	    	int width = viewTextures.getWidth();
	    	int height = viewTextures.getHeight();
	    	depthTextures = context.get2DDepthTextureArrayBuilder(width, height, viewSet.getCameraPoseCount()).createTexture();
	    	
	    	// Don't automatically generate any texture attachments for this framebuffer object
	    	FramebufferObject<ContextType> depthRenderingFBO = context.getFramebufferObjectBuilder(width, height).createFramebufferObject();
	    	
	    	Renderable<ContextType> depthRenderable = context.createRenderable(depthRenderingProgram);
	    	depthRenderable.addVertexBuffer("position", positionBuffer);
	    	
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
				//System.out.println((i+1) + "/" + viewSet.getCameraPoseCount() + " depth maps created.");
	    	}

	    	depthRenderingFBO.delete();
	    	
    		System.out.println("Depth maps created in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
    	}
    	
    	if (param.getTextureSubdivision() > 1)
    	{
	    	System.out.println("Beginning model fitting (" + (param.getTextureSubdivision() * param.getTextureSubdivision()) + " blocks)...");
    	}
    	else
    	{
	    	System.out.println("Setting up model fitting...");
    	}
    	timestamp = new Date();
    	
    	FramebufferObject<ContextType> lightFitFramebuffer = 
			context.getFramebufferObjectBuilder(param.getTextureSize(), param.getTextureSize())
				.addColorAttachments(new ColorAttachmentSpec(ColorFormat.RGBA32F), 2)
				.createFramebufferObject();
    	
    	FramebufferObject<ContextType> diffuseFitFramebuffer = 
			context.getFramebufferObjectBuilder(param.getTextureSize(), param.getTextureSize())
				.addColorAttachments(4)
				.createFramebufferObject();
    	
    	FramebufferObject<ContextType> specularFitFramebuffer = 
			context.getFramebufferObjectBuilder(param.getTextureSize(), param.getTextureSize())
				.addColorAttachments(4)
				.createFramebufferObject();
    	
    	diffuseFitFramebuffer.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, 0.0f);
    	diffuseFitFramebuffer.clearColorBuffer(1, 0.0f, 0.0f, 0.0f, 0.0f);
    	diffuseFitFramebuffer.clearColorBuffer(2, 0.0f, 0.0f, 0.0f, 0.0f);
    	diffuseFitFramebuffer.clearColorBuffer(3, 0.0f, 0.0f, 0.0f, 0.0f);
    	
    	specularFitFramebuffer.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, 0.0f);
        specularFitFramebuffer.clearColorBuffer(1, 0.0f, 0.0f, 0.0f, 0.0f);
        specularFitFramebuffer.clearColorBuffer(2, 0.0f, 0.0f, 0.0f, 0.0f);
        specularFitFramebuffer.clearColorBuffer(3, 0.0f, 0.0f, 0.0f, 0.0f);
        
        Renderable<ContextType> lightFitRenderable = context.createRenderable(lightFitProgram);
    	
        lightFitRenderable.addVertexBuffer("position", positionBuffer);
        lightFitRenderable.addVertexBuffer("texCoord", texCoordBuffer);
        lightFitRenderable.addVertexBuffer("normal", normalBuffer);
    	
        lightFitRenderable.program().setUniform("viewCount", viewSet.getCameraPoseCount());
        lightFitRenderable.program().setUniform("gamma", param.getGamma());
        lightFitRenderable.program().setUniform("shadowTestEnabled", false);
        lightFitRenderable.program().setUniform("occlusionEnabled", param.isCameraVisibilityTestEnabled());
        lightFitRenderable.program().setUniform("occlusionBias", param.getCameraVisibilityTestBias());
        lightFitRenderable.program().setUniform("infiniteLightSources", param.areLightSourcesInfinite());
    	
        lightFitRenderable.program().setUniformBuffer("CameraPoses", viewSet.getCameraPoseBuffer());
    	
    	if (!param.isImagePreprojectionUseEnabled())
    	{
    		lightFitRenderable.program().setUniformBuffer("CameraProjections", viewSet.getCameraProjectionBuffer());
    		lightFitRenderable.program().setUniformBuffer("CameraProjectionIndices", viewSet.getCameraProjectionIndexBuffer());
    	}
    	
    	lightFitRenderable.program().setUniform("delta", param.getDiffuseDelta());
    	lightFitRenderable.program().setUniform("iterations", param.getDiffuseIterations());

    	Renderable<ContextType> diffuseFitRenderable = context.createRenderable(diffuseFitProgram);
    	
    	diffuseFitRenderable.addVertexBuffer("position", positionBuffer);
    	diffuseFitRenderable.addVertexBuffer("texCoord", texCoordBuffer);
    	diffuseFitRenderable.addVertexBuffer("normal", normalBuffer);
    	
    	diffuseFitRenderable.program().setUniform("viewCount", viewSet.getCameraPoseCount());
    	diffuseFitRenderable.program().setUniform("gamma", param.getGamma());
    	diffuseFitRenderable.program().setUniform("shadowTestEnabled", param.isCameraVisibilityTestEnabled());
    	diffuseFitRenderable.program().setUniform("occlusionEnabled", param.isCameraVisibilityTestEnabled());
    	diffuseFitRenderable.program().setUniform("occlusionBias", param.getCameraVisibilityTestBias());
    	diffuseFitRenderable.program().setUniform("infiniteLightSources", param.areLightSourcesInfinite());
    	
    	diffuseFitRenderable.program().setUniformBuffer("CameraPoses", viewSet.getCameraPoseBuffer());
    	
    	if (!param.isImagePreprojectionUseEnabled())
    	{
	    	diffuseFitRenderable.program().setUniformBuffer("CameraProjections", viewSet.getCameraProjectionBuffer());
	    	diffuseFitRenderable.program().setUniformBuffer("CameraProjectionIndices", viewSet.getCameraProjectionIndexBuffer());
    	}
    	
    	diffuseFitRenderable.program().setUniform("delta", param.getDiffuseDelta());
    	diffuseFitRenderable.program().setUniform("iterations", param.getDiffuseIterations());
    	diffuseFitRenderable.program().setUniform("fit1Weight", param.getDiffuseInputNormalWeight());
    	diffuseFitRenderable.program().setUniform("fit3Weight", param.getDiffuseComputedNormalWeight());
    	
    	if (viewSet.getLightPositionBuffer() != null && viewSet.getLightIndexBuffer() != null)
		{
    		diffuseFitRenderable.program().setUniformBuffer("LightPositions", viewSet.getLightPositionBuffer());
    		diffuseFitRenderable.program().setUniformBuffer("LightIntensities", viewSet.getLightIntensityBuffer());
    		diffuseFitRenderable.program().setUniformBuffer("LightIndices", viewSet.getLightIndexBuffer());
		}
    	
    	Renderable<ContextType> specularFitRenderable = context.createRenderable(specularFitProgram);
    	
    	specularFitRenderable.addVertexBuffer("position", positionBuffer);
    	specularFitRenderable.addVertexBuffer("texCoord", texCoordBuffer);
    	specularFitRenderable.addVertexBuffer("normal", normalBuffer);

    	specularFitRenderable.program().setUniform("viewCount", viewSet.getCameraPoseCount());
    	specularFitRenderable.program().setUniformBuffer("CameraPoses", viewSet.getCameraPoseBuffer());
    	
    	if (!param.isImagePreprojectionUseEnabled())
    	{
	    	specularFitRenderable.program().setUniformBuffer("CameraProjections", viewSet.getCameraProjectionBuffer());
	    	specularFitRenderable.program().setUniformBuffer("CameraProjectionIndices", viewSet.getCameraProjectionIndexBuffer());
    	}

    	specularFitRenderable.program().setUniform("shadowTestEnabled", param.isCameraVisibilityTestEnabled());
    	specularFitRenderable.program().setUniform("occlusionEnabled", param.isCameraVisibilityTestEnabled());
    	specularFitRenderable.program().setUniform("occlusionBias", param.getCameraVisibilityTestBias());
    	specularFitRenderable.program().setUniform("gamma", param.getGamma());
    	specularFitRenderable.program().setUniform("infiniteLightSources", param.areLightSourcesInfinite());
    	
    	specularFitRenderable.program().setUniform("computeRoughness", param.isSpecularRoughnessComputationEnabled());
    	specularFitRenderable.program().setUniform("computeNormal", param.isSpecularNormalComputationEnabled());
    	specularFitRenderable.program().setUniform("trueBlinnPhong", param.isTrueBlinnPhongSpecularEnabled());

    	specularFitRenderable.program().setUniform("diffuseRemovalAmount", param.getSpecularSubtractDiffuseAmount());
    	specularFitRenderable.program().setUniform("specularInfluenceScale", param.getSpecularInfluenceScale());
    	specularFitRenderable.program().setUniform("determinantThreshold", param.getSpecularDeterminantThreshold());
    	specularFitRenderable.program().setUniform("fit1Weight", param.getSpecularInputNormalDefaultRoughnessWeight());
    	specularFitRenderable.program().setUniform("fit2Weight", param.getSpecularInputNormalComputedRoughnessWeight());
    	specularFitRenderable.program().setUniform("fit4Weight", param.getSpecularComputedNormalWeight());
    	specularFitRenderable.program().setUniform("defaultSpecularColor", new Vector3(0.0f, 0.0f, 0.0f));
    	specularFitRenderable.program().setUniform("defaultSpecularRoughness", param.getDefaultSpecularRoughness());
    	specularFitRenderable.program().setUniform("specularRoughnessScale", param.getSpecularRoughnessCap());

    	File diffuseTempDirectory = new File(tmpDir, "diffuse");
    	File normalTempDirectory = new File(tmpDir, "normal");
    	File specularTempDirectory = new File(tmpDir, "specular");
    	File roughnessTempDirectory = new File(tmpDir, "roughness");
    	File snormalTempDirectory = new File(tmpDir, "snormal");
    	
    	diffuseTempDirectory.mkdir();
    	normalTempDirectory.mkdir();
    	specularTempDirectory.mkdir();
    	roughnessTempDirectory.mkdir();
    	snormalTempDirectory.mkdir();
    	
    	int subdivSize = param.getTextureSize() / param.getTextureSubdivision();
    	
    	if (param.getTextureSubdivision() == 1)
		{
	    	System.out.println("Setup finished in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
	    	timestamp = new Date();
		}
    	
    	System.out.println("Fitting light...");
    	
    	for (int row = 0; row < param.getTextureSubdivision(); row++)
    	{
	    	for (int col = 0; col < param.getTextureSubdivision(); col++)
    		{
	    		Texture3D<ContextType> preprojectedViews = null;
		    	
		    	if (param.isImagePreprojectionUseEnabled())
		    	{
		    		preprojectedViews = context.get2DColorTextureArrayBuilder(subdivSize, subdivSize, viewSet.getCameraPoseCount()).createTexture();
			    	
					for (int i = 0; i < viewSet.getCameraPoseCount(); i++)
					{
						preprojectedViews.loadLayer(i, new File(new File(tmpDir, String.format("%04d", i)), String.format("r%04dc%04d.png", row, col)), true);
					}
		    		
		    		lightFitRenderable.program().setTexture("viewImages", preprojectedViews);
		    	}
		    	else
		    	{
		    		lightFitRenderable.program().setTexture("viewImages", viewTextures);
		    		lightFitRenderable.program().setTexture("depthImages", depthTextures);
		    	}
		    	
		    	lightFitRenderable.program().setUniform("minTexCoord", 
	    				new Vector2((float)col / (float)param.getTextureSubdivision(), (float)row / (float)param.getTextureSubdivision()));
	    		
		    	lightFitRenderable.program().setUniform("maxTexCoord", 
	    				new Vector2((float)(col+1) / (float)param.getTextureSubdivision(), (float)(row+1) / (float)param.getTextureSubdivision()));
		    	
		    	lightFitRenderable.draw(PrimitiveMode.TRIANGLES, lightFitFramebuffer, col * subdivSize, row * subdivSize, subdivSize, subdivSize);
		        context.finish();
		        
		        if (param.getTextureSubdivision() > 1)
	    		{
	    			System.out.println("Block " + (row*param.getTextureSubdivision() + col + 1) + "/" + (param.getTextureSubdivision() * param.getTextureSubdivision()) + " completed.");
	    		}
    		}
    	}
    	
    	System.out.println("Aggregating light estimates...");
    	
    	float[] rawLightPositions = lightFitFramebuffer.readFloatingPointColorBufferRGBA(0);
        float[] rawLightIntensities = lightFitFramebuffer.readFloatingPointColorBufferRGBA(1);
        
        lightFitFramebuffer.delete(); // No need for this anymore
        
        Vector4 lightPositionSum = new Vector4(0, 0, 0, 0);
        Vector4 lightIntensitySum = new Vector4(0, 0, 0, 0);
        
        for (int i = 0; i < param.getTextureSize(); i++)
        {
        	for (int j = 0; j < param.getTextureSize(); j++)
        	{
        		int indexStart = (i * param.getTextureSize() + j) * 4;
        		lightPositionSum = lightPositionSum.plus(
        				new Vector4(rawLightPositions[indexStart+0], rawLightPositions[indexStart+1], rawLightPositions[indexStart+2], 1.0f)
        					.times(rawLightPositions[indexStart+3]));
        		lightIntensitySum = lightIntensitySum.plus(
        				new Vector4(rawLightIntensities[indexStart+0], rawLightIntensities[indexStart+1], rawLightIntensities[indexStart+2], 1.0f)
        					.times(rawLightIntensities[indexStart+3]));
        	}
        }
        
        Vector4 avgLightPosition = lightPositionSum.dividedBy(lightPositionSum.w);
        Vector4 avgLightIntensity = lightIntensitySum.dividedBy(lightIntensitySum.w);
        
        System.out.println("Light position: " + avgLightPosition.x + " " + avgLightPosition.y + " " + avgLightPosition.z);
        System.out.println("Light intensity: " + avgLightIntensity.x + " " + avgLightIntensity.y + " " + avgLightIntensity.z);
        
        viewSet.setLightPosition(0, new Vector3(avgLightPosition));
        viewSet.setLightIntensity(0, new Vector3(avgLightIntensity));
        viewSet.writeVSETFileToStream(new FileOutputStream(new File(outputDir, "default.vset")));
        
        System.out.println("Creating shadow maps...");
    	timestamp = new Date();
    	
    	// Build shadow maps for each view
    	int width = viewTextures.getWidth();
    	int height = viewTextures.getHeight();
    	shadowTextures = context.get2DDepthTextureArrayBuilder(width, height, viewSet.getCameraPoseCount()).createTexture();
    	
    	// Don't automatically generate any texture attachments for this framebuffer object
    	FramebufferObject<ContextType> shadowRenderingFBO = context.getFramebufferObjectBuilder(width, height).createFramebufferObject();
    	
    	Renderable<ContextType> shadowRenderable = context.createRenderable(depthRenderingProgram);
    	shadowRenderable.addVertexBuffer("position", positionBuffer);
    	
    	final int shadowMapFarPlaneCushion = 2; // TODO decide where this should be defined
    	
    	// Flatten the camera pose matrices into 16-component vectors and store them in the vertex list data structure.
    	FloatVertexList flattenedShadowMatrices = new FloatVertexList(16, viewSet.getCameraPoseCount());
    	
    	// Render each shadow map
    	for (int i = 0; i < viewSet.getCameraPoseCount(); i++)
    	{
    		shadowRenderingFBO.setDepthAttachment(shadowTextures.getLayerAsFramebufferAttachment(i));
    		shadowRenderingFBO.clearDepthBuffer();
    		
    		Matrix4 modelView = Matrix4.lookAt(new Vector3(viewSet.getCameraPoseInverse(i).times(avgLightPosition)), mesh.getCentroid(), new Vector3(0, 1, 0));
        	depthRenderingProgram.setUniform("model_view", modelView);
        	
    		Matrix4 projection = viewSet.getCameraProjection(viewSet.getCameraProjectionIndex(i))
					.getProjectionMatrix(
						viewSet.getRecommendedNearPlane(), 
						viewSet.getRecommendedFarPlane() * shadowMapFarPlaneCushion // double it for good measure
					);
    		depthRenderingProgram.setUniform("projection", projection);
        	
    		shadowRenderable.draw(PrimitiveMode.TRIANGLES, shadowRenderingFBO);
    		
    		Matrix4 fullTransform = projection.times(modelView);
    		
    		int d = 0;
			for (int col = 0; col < 4; col++) // column
			{
				for (int row = 0; row < 4; row++) // row
				{
					flattenedShadowMatrices.set(i, d, fullTransform.get(row, col));
					d++;
				}
			}
    	}
		
		// Create the uniform buffer
		UniformBuffer<ContextType> shadowMatrixBuffer = context.createUniformBuffer().setData(flattenedShadowMatrices);

    	shadowRenderingFBO.delete();
    	
    	diffuseFitRenderable.program().setUniformBuffer("ShadowMatrices", shadowMatrixBuffer);
    	specularFitRenderable.program().setUniformBuffer("ShadowMatrices", shadowMatrixBuffer);
    	
		System.out.println("Shadow maps created in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
		
		// Phong regression
        
        FloatVertexList lightPositionList = new FloatVertexList(4, 1);
    	lightPositionList.set(0, 0, avgLightPosition.x);
    	lightPositionList.set(0, 1, avgLightPosition.y);
    	lightPositionList.set(0, 2, avgLightPosition.z);
    	lightPositionList.set(0, 3, 1.0f);
    	
        FloatVertexList lightIntensityList = new FloatVertexList(3, 1);
        lightIntensityList.set(0, 0, avgLightIntensity.x);
        lightIntensityList.set(0, 1, avgLightIntensity.y);
        lightIntensityList.set(0, 2, avgLightIntensity.z);
        
        UniformBuffer<ContextType> lightPositionBuffer = context.createUniformBuffer().setData(lightPositionList);
        UniformBuffer<ContextType> lightIntensityBuffer = context.createUniformBuffer().setData(lightIntensityList);

		diffuseFitRenderable.program().setUniformBuffer("LightPositions", lightPositionBuffer);
		diffuseFitRenderable.program().setUniformBuffer("LightIntensities", lightIntensityBuffer);

		specularFitRenderable.program().setUniformBuffer("LightPositions", lightPositionBuffer);
		specularFitRenderable.program().setUniformBuffer("LightIntensities", lightIntensityBuffer);
        
    	for (int row = 0; row < param.getTextureSubdivision(); row++)
    	{
	    	for (int col = 0; col < param.getTextureSubdivision(); col++)
    		{
		    	Texture3D<ContextType> preprojectedViews = null;
		        
		    	if (param.getTextureSubdivision() == 1)
	    		{
		    		System.out.println("Light fit completed in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
		        	
		        	System.out.println("Fitting diffuse...");
			    	timestamp = new Date();
	    		}
		    	
		    	if (param.isImagePreprojectionUseEnabled())
		    	{
	    			preprojectedViews = context.get2DColorTextureArrayBuilder(subdivSize, subdivSize, viewSet.getCameraPoseCount()).createTexture();
			    	
					for (int i = 0; i < viewSet.getCameraPoseCount(); i++)
					{
						preprojectedViews.loadLayer(i, new File(new File(tmpDir, String.format("%04d", i)), String.format("r%04dc%04d.png", row, col)), true);
					}
		    		
		    		diffuseFitRenderable.program().setTexture("viewImages", preprojectedViews);
		    	}
		    	else
		    	{
			    	diffuseFitRenderable.program().setTexture("viewImages", viewTextures);
			    	diffuseFitRenderable.program().setTexture("depthImages", depthTextures);
			    	diffuseFitRenderable.program().setTexture("shadowImages", shadowTextures);
		    	}
		    	
		    	diffuseFitRenderable.program().setUniform("minTexCoord", 
	    				new Vector2((float)col / (float)param.getTextureSubdivision(), (float)row / (float)param.getTextureSubdivision()));
	    		
		    	diffuseFitRenderable.program().setUniform("maxTexCoord", 
	    				new Vector2((float)(col+1) / (float)param.getTextureSubdivision(), (float)(row+1) / (float)param.getTextureSubdivision()));
		    	
		        diffuseFitRenderable.draw(PrimitiveMode.TRIANGLES, diffuseFitFramebuffer, col * subdivSize, row * subdivSize, subdivSize, subdivSize);
		        context.finish();
		        
		        if (param.isImagePreprojectionUseEnabled())
		        {
			        diffuseFitFramebuffer.saveColorBufferToFile(0, col * subdivSize, row * subdivSize, subdivSize, subdivSize, 
			        		"PNG", new File(diffuseTempDirectory, String.format("r%04dc%04d.png", row, col)));
			        
			        diffuseFitFramebuffer.saveColorBufferToFile(1, col * subdivSize, row * subdivSize, subdivSize, subdivSize, 
			        		"PNG", new File(normalTempDirectory, String.format("r%04dc%04d.png", row, col)));
		        }
	    		
		        if (param.getTextureSubdivision() == 1)
		        {
		        	System.out.println("Diffuse fit completed in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
		        	
		        	System.out.println("Fitting specular...");
		        	timestamp = new Date();
		        }
		    	
		    	if (param.isImagePreprojectionUseEnabled())
		    	{
		    		specularFitRenderable.program().setTexture("viewImages", preprojectedViews);
		    	}
		    	else
		    	{
			    	specularFitRenderable.program().setTexture("viewImages", viewTextures);
			    	specularFitRenderable.program().setTexture("depthImages", depthTextures);
			    	specularFitRenderable.program().setTexture("shadowImages", shadowTextures);
		    	}
		    	
		    	if (viewSet.getLightPositionBuffer() != null && viewSet.getLightIndexBuffer() != null)
	    		{
		    		specularFitRenderable.program().setUniformBuffer("LightPositions", viewSet.getLightPositionBuffer());
		    		specularFitRenderable.program().setUniformBuffer("LightIntensities", viewSet.getLightIntensityBuffer());
		    		specularFitRenderable.program().setUniformBuffer("LightIndices", viewSet.getLightIndexBuffer());
	    		}
		    	
		    	specularFitRenderable.program().setUniform("minTexCoord", 
	    				new Vector2((float)col / (float)param.getTextureSubdivision(), (float)row / (float)param.getTextureSubdivision()));
	    		
		    	specularFitRenderable.program().setUniform("maxTexCoord", 
	    				new Vector2((float)(col+1) / (float)param.getTextureSubdivision(), (float)(row+1) / (float)param.getTextureSubdivision()));

		    	specularFitRenderable.program().setTexture("diffuseEstimate", diffuseFitFramebuffer.getColorAttachmentTexture(0));
		    	specularFitRenderable.program().setTexture("normalEstimate", diffuseFitFramebuffer.getColorAttachmentTexture(1));
		        
	    		specularFitRenderable.draw(PrimitiveMode.TRIANGLES, specularFitFramebuffer, col * subdivSize, row * subdivSize, subdivSize, subdivSize);
	    		context.finish();

	    		if (param.isImagePreprojectionUseEnabled())
	    		{
		    		specularFitFramebuffer.saveColorBufferToFile(0, col * subdivSize, row * subdivSize, subdivSize, subdivSize, 
			        		"PNG", new File(specularTempDirectory, String.format("r%04dc%04d.png", row, col)));
			        
		    		specularFitFramebuffer.saveColorBufferToFile(1, col * subdivSize, row * subdivSize, subdivSize, subdivSize, 
			        		"PNG", new File(roughnessTempDirectory, String.format("r%04dc%04d.png", row, col)));
			        
		    		specularFitFramebuffer.saveColorBufferToFile(2, col * subdivSize, row * subdivSize, subdivSize, subdivSize, 
			        		"PNG", new File(snormalTempDirectory, String.format("r%04dc%04d.png", row, col)));
		    		
		    		preprojectedViews.delete();
		    	}
	    		
		    	if (param.getTextureSubdivision() > 1)
	    		{
	    			System.out.println("Block " + (row*param.getTextureSubdivision() + col + 1) + "/" + (param.getTextureSubdivision() * param.getTextureSubdivision()) + " completed.");
	    		}
		    	else
		    	{
			    	System.out.println("Specular fit completed in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
		        }
    		}
    	}
    	
    	if (param.getTextureSubdivision() > 1)
    	{
    		System.out.println("Model fitting completed in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
    	}
    	
    	System.out.println("Filling empty regions...");
    	timestamp = new Date();
    	
    	FramebufferObject<ContextType> holeFillBackFBO = 
			context.getFramebufferObjectBuilder(param.getTextureSize(), param.getTextureSize())
				.addColorAttachments(4)
				.createFramebufferObject();
    	
    	Renderable<ContextType> holeFillRenderable = context.createRenderable(holeFillProgram);
    	VertexBuffer<ContextType> rectBuffer = context.createRectangle();
    	holeFillRenderable.addVertexBuffer("position", rectBuffer);
    	
    	holeFillProgram.setUniform("minFillAlpha", 0.5f);
		
		System.out.println("Diffuse fill...");
    	
    	// Diffuse
    	FramebufferObject<ContextType> holeFillFrontFBO = diffuseFitFramebuffer;
    	for (int i = 0; i < param.getTextureSize() / 2; i++)
    	{
    		holeFillBackFBO.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, 1.0f);
    		holeFillBackFBO.clearColorBuffer(1, 0.0f, 0.0f, 0.0f, 0.0f);
    		holeFillBackFBO.clearColorBuffer(2, 0.0f, 0.0f, 0.0f, 0.0f);
    		holeFillBackFBO.clearColorBuffer(3, 0.0f, 0.0f, 0.0f, 0.0f);
    		
    		holeFillProgram.setTexture("input0", holeFillFrontFBO.getColorAttachmentTexture(0));
    		holeFillProgram.setTexture("input1", holeFillFrontFBO.getColorAttachmentTexture(1));
    		holeFillProgram.setTexture("input2", holeFillFrontFBO.getColorAttachmentTexture(2));
    		holeFillProgram.setTexture("input3", holeFillFrontFBO.getColorAttachmentTexture(3));
    		
    		holeFillRenderable.draw(PrimitiveMode.TRIANGLE_FAN, holeFillBackFBO);
    		context.finish();
    		
    		FramebufferObject<ContextType> tmp = holeFillFrontFBO;
    		holeFillFrontFBO = holeFillBackFBO;
    		holeFillBackFBO = tmp;
    	}
    	diffuseFitFramebuffer = holeFillFrontFBO;
		
		System.out.println("Specular fill...");
    	
    	// Specular
    	holeFillFrontFBO = specularFitFramebuffer;
    	for (int i = 0; i < param.getTextureSize() / 2; i++)
    	{
    		holeFillBackFBO.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, 0.0f);
    		holeFillBackFBO.clearColorBuffer(1, 0.0f, 0.0f, 0.0f, 0.0f);
    		holeFillBackFBO.clearColorBuffer(2, 0.0f, 0.0f, 0.0f, 0.0f);
    		holeFillBackFBO.clearColorBuffer(3, 0.0f, 0.0f, 0.0f, 0.0f);
    		
    		holeFillProgram.setTexture("input0", holeFillFrontFBO.getColorAttachmentTexture(0));
    		holeFillProgram.setTexture("input1", holeFillFrontFBO.getColorAttachmentTexture(1));
    		holeFillProgram.setTexture("input2", holeFillFrontFBO.getColorAttachmentTexture(2));
    		holeFillProgram.setTexture("input3", holeFillFrontFBO.getColorAttachmentTexture(3));
    		
    		holeFillRenderable.draw(PrimitiveMode.TRIANGLE_FAN, holeFillBackFBO);
    		context.finish();
    		
    		FramebufferObject<ContextType> tmp = holeFillFrontFBO;
    		holeFillFrontFBO = holeFillBackFBO;
    		holeFillBackFBO = tmp;
    	}
    	specularFitFramebuffer = holeFillFrontFBO;

		System.out.println("Empty regions filled in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
    	
    	if (!DEBUG || param.isImagePreprojectionUseEnabled())
    	{
	        viewSet.deleteOpenGLResources();
	        positionBuffer.delete();
	        normalBuffer.delete();
	        texCoordBuffer.delete();
	        
	        if (viewTextures != null)
	        {
	        	viewTextures.delete();
	        }
	        
	        if (depthTextures != null)
	        {
	        	depthTextures.delete();
	        }
	        
	        if (shadowTextures != null)
	        {
	        	shadowTextures.delete();
	        }
	        
	        lightPositionBuffer.delete();
	        lightIntensityBuffer.delete();
	        
	        if (shadowMatrixBuffer != null)
	        {
	        	shadowMatrixBuffer.delete();
	        }
    	}
    	
    	System.out.println("Saving textures...");
    	timestamp = new Date();
    	
    	File textureDirectory = new File(outputDir, "textures");
    	
    	textureDirectory.mkdirs();
        
    	diffuseFitFramebuffer.saveColorBufferToFile(0, "PNG", new File(textureDirectory, "diffuse.png"));
    	diffuseFitFramebuffer.saveColorBufferToFile(1, "PNG", new File(textureDirectory, "normal.png"));
    	//diffuseFitFramebuffer.saveColorBufferToFile(2, "PNG", new File(textureDirectory, "ambient.png"));
    	if (DEBUG)
    	{
    		diffuseFitFramebuffer.saveColorBufferToFile(3, "PNG", new File(textureDirectory, "ddebug.png"));
    	}

    	specularFitFramebuffer.saveColorBufferToFile(0, "PNG", new File(textureDirectory, "specular.png"));
    	specularFitFramebuffer.saveColorBufferToFile(1, "PNG", new File(textureDirectory, "roughness.png"));
    	if (param.isSpecularNormalComputationEnabled())
    	{
    		specularFitFramebuffer.saveColorBufferToFile(2, "PNG", new File(textureDirectory, "snormal.png"));
    	}
    	if (DEBUG)
    	{
	    	specularFitFramebuffer.saveColorBufferToFile(3, "PNG", new File(textureDirectory, "textures/sdebug.png"));
    	}
    	
    	diffuseFitFramebuffer.delete();
    	specularFitFramebuffer.delete();

    	System.out.println("Textures saved in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
    	
    	if (DEBUG && !param.isImagePreprojectionUseEnabled())
    	{
    		System.out.println("Generating diffuse debug info...");
	    	timestamp = new Date();

	    	new File(outputDir, "debug").mkdirs();
    		
	    	FramebufferObject<ContextType> diffuseDebugFBO = 
    			context.getFramebufferObjectBuilder(param.getTextureSize(), param.getTextureSize())
    				.addColorAttachments(ColorFormat.RGBA32F, 2)
    				.createFramebufferObject();
	    	
	    	Renderable<ContextType> diffuseDebugRenderable = context.createRenderable(diffuseDebugProgram);
	    	
	    	diffuseDebugRenderable.program().setUniform("minTexCoord", new Vector2(0.0f, 0.0f));
	    	diffuseDebugRenderable.program().setUniform("maxTexCoord", new Vector2(1.0f, 1.0f));
	    	
	    	diffuseDebugRenderable.addVertexBuffer("position", positionBuffer);
	    	diffuseDebugRenderable.addVertexBuffer("texCoord", texCoordBuffer);
	    	diffuseDebugRenderable.addVertexBuffer("normal", normalBuffer);

	    	diffuseDebugRenderable.program().setTexture("viewImages", viewTextures);
	    	diffuseDebugRenderable.program().setTexture("depthImages", depthTextures);
	    	diffuseDebugRenderable.program().setUniformBuffer("CameraPoses", viewSet.getCameraPoseBuffer());
	    	diffuseDebugRenderable.program().setUniformBuffer("CameraProjections", viewSet.getCameraProjectionBuffer());
	    	diffuseDebugRenderable.program().setUniformBuffer("CameraProjectionIndices", viewSet.getCameraProjectionIndexBuffer());
	    	diffuseDebugRenderable.program().setUniform("occlusionEnabled", param.isCameraVisibilityTestEnabled());
	    	diffuseDebugRenderable.program().setUniform("occlusionBias", param.getCameraVisibilityTestBias());
	    	
	    	//new File(outputDirectory, "debug/diffuse/projpos").mkdirs();
	    	
	    	PrintStream diffuseInfo = new PrintStream(new File(outputDir, "debug/diffuseInfo.txt"));
	    	
	    	for (int i = 0; i < viewSet.getCameraPoseCount(); i++)
	    	{
	    		diffuseDebugRenderable.program().setUniform("viewIndex", i);
	    		
	    		diffuseDebugFBO.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, 0.0f);
	    		diffuseDebugFBO.clearColorBuffer(1, 0.0f, 0.0f, 0.0f, 0.0f);
	    		diffuseDebugFBO.clearDepthBuffer();
	    		diffuseDebugRenderable.draw(PrimitiveMode.TRIANGLES, diffuseDebugFBO);
	    		
	    		//diffuseDebugFBO.saveColorBufferToFile(0, "PNG", new File(outputDirectory, String.format("debug/diffuse/%04d.png", i)));
	    		//diffuseDebugFBO.saveColorBufferToFile(1, "PNG", new File(outputDirectory, String.format("debug/diffuse/projpos/%04d.png", i)));
	    		
	    		Matrix4 cameraPose = viewSet.getCameraPose(i);
	    		Vector3 debugLightPos = new Matrix3(cameraPose).transpose().times(
    				viewSet.getLightPosition(viewSet.getLightPositionIndex(i))
    					.minus(new Vector3(cameraPose.getColumn(3))));
	    		int[] colorData = diffuseDebugFBO.readColorBufferARGB(0, DEBUG_PIXEL_X, DEBUG_PIXEL_Y, 1, 1);
	    		float[] positionData = diffuseDebugFBO.readFloatingPointColorBufferRGBA(1, DEBUG_PIXEL_X, DEBUG_PIXEL_Y, 1, 1);
	    		diffuseInfo.println(
    				debugLightPos.x + "\t" +
					debugLightPos.y + "\t" +
					debugLightPos.z + "\t" +
    				positionData[0] + "\t" +
    				positionData[1] + "\t" +
    				positionData[2] + "\t" +
					((colorData[0] & 0xFF000000) >>> 24) + "\t" + 
					((colorData[0] & 0x00FF0000) >>> 16) + "\t" + 
					((colorData[0] & 0x0000FF00) >>> 8) + "\t" +
					(colorData[0] & 0x000000FF));
	    	}	
	    	
	    	diffuseInfo.flush();
	    	diffuseInfo.close();	    	
	    	
	    	diffuseDebugFBO.delete();
	    	
			System.out.println("Diffuse debug info completed in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
    		
	    	System.out.println("Generating specular debug info...");
	    	timestamp = new Date();
    		
	    	FramebufferObject<ContextType> specularDebugFBO = 
    			context.getFramebufferObjectBuilder(param.getTextureSize(), param.getTextureSize())
    				.addColorAttachments(ColorFormat.RGBA32F, 2)
    				.createFramebufferObject();
	    	Renderable<ContextType> specularDebugRenderable = context.createRenderable(specularDebugProgram);

	    	specularDebugRenderable.program().setUniform("minTexCoord", new Vector2(0.0f, 0.0f));
	    	specularDebugRenderable.program().setUniform("maxTexCoord", new Vector2(1.0f, 1.0f));
	    	
	    	specularDebugRenderable.addVertexBuffer("position", positionBuffer);
	    	specularDebugRenderable.addVertexBuffer("texCoord", texCoordBuffer);
	    	specularDebugRenderable.addVertexBuffer("normal", normalBuffer);

	    	specularDebugRenderable.program().setTexture("viewImages", viewTextures);
	    	specularDebugRenderable.program().setTexture("depthImages", depthTextures);
	    	specularDebugRenderable.program().setUniform("occlusionEnabled", param.isCameraVisibilityTestEnabled());
	    	specularDebugRenderable.program().setUniform("occlusionBias", param.getCameraVisibilityTestBias());
	    	specularDebugRenderable.program().setTexture("diffuse", diffuseFitFramebuffer.getColorAttachmentTexture(0));
	    	specularDebugRenderable.program().setTexture("normalMap", diffuseFitFramebuffer.getColorAttachmentTexture(1));
	    	specularDebugRenderable.program().setUniformBuffer("CameraPoses", viewSet.getCameraPoseBuffer());
	    	specularDebugRenderable.program().setUniformBuffer("CameraProjections", viewSet.getCameraProjectionBuffer());
	    	specularDebugRenderable.program().setUniformBuffer("CameraProjectionIndices", viewSet.getCameraProjectionIndexBuffer());
	    	specularDebugRenderable.program().setUniform("gamma", param.getGamma());
	    	specularDebugRenderable.program().setUniform("diffuseRemovalFactor", 1.0f);
	    	specularDebugRenderable.program().setUniform("infiniteLightSources", param.areLightSourcesInfinite());
	    	
	    	if (viewSet.getLightPositionBuffer() != null && viewSet.getLightIndexBuffer() != null)
    		{
	    		specularDebugRenderable.program().setUniformBuffer("LightPositions", viewSet.getLightPositionBuffer());
	    		specularDebugRenderable.program().setUniformBuffer("LightIntensities", viewSet.getLightIntensityBuffer());
	    		specularDebugRenderable.program().setUniformBuffer("LightIndices", viewSet.getLightIndexBuffer());
    		}
	    	
	    	//new File(outputDirectory, "debug/specular/rDotV").mkdirs();
	    	
	    	PrintStream specularInfo = new PrintStream(new File(outputDir, "debug/specularInfo.txt"));
	    	
	    	for (int i = 0; i < viewSet.getCameraPoseCount(); i++)
	    	{
	    		specularDebugRenderable.program().setUniform("viewIndex", i);
	    		
	    		specularDebugFBO.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, 0.0f);
	    		specularDebugFBO.clearColorBuffer(1, 0.0f, 0.0f, 0.0f, 0.0f);
	    		specularDebugFBO.clearDepthBuffer();
	    		specularDebugRenderable.draw(PrimitiveMode.TRIANGLES, specularDebugFBO);
	    		
	    		//specularDebugFBO.saveColorBufferToFile(0, "PNG", new File(outputDirectory, String.format("debug/specular/%04d.png", i)));
	    		//specularDebugFBO.saveColorBufferToFile(1, "PNG", new File(outputDirectory, String.format("debug/specular/rDotV/%04d.png", i)));
	    		
	    		int[] colorData = specularDebugFBO.readColorBufferARGB(0, DEBUG_PIXEL_X, DEBUG_PIXEL_Y, 1, 1);
	    		int[] rDotVData = specularDebugFBO.readColorBufferARGB(1, DEBUG_PIXEL_X, DEBUG_PIXEL_Y, 1, 1);
	    		specularInfo.println(	(rDotVData[0] & 0x000000FF) + "\t" +
									((colorData[0] & 0xFF000000) >>> 24) + "\t" + 
	    							((colorData[0] & 0x00FF0000) >>> 16) + "\t" + 
	    							((colorData[0] & 0x0000FF00) >>> 8) + "\t" +
	    							(colorData[0] & 0x000000FF));
	    	}
	    	
	    	specularInfo.flush();
	    	specularInfo.close();
	    	
	    	specularDebugFBO.delete();
	    	
	        viewSet.deleteOpenGLResources();
	        positionBuffer.delete();
	        normalBuffer.delete();
	        texCoordBuffer.delete();
	        
	        if (viewTextures != null)
	        {
	        	viewTextures.delete();
	        }
	        
	        if (depthTextures != null)
	        {
	        	depthTextures.delete();
	        }
	        
	        if (shadowTextures != null)
	        {
	        	shadowTextures.delete();
	        }
	        
	        if (lightPositionBuffer != null)
	        {
	        	lightPositionBuffer.delete();
	        }
	        
	        if (lightIntensityBuffer != null)
	        {
	        	lightIntensityBuffer.delete();
	        }
	        
	        if (shadowMatrixBuffer != null)
	        {
	        	shadowMatrixBuffer.delete();
	        }
	    	
			System.out.println("Specular debug info completed in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
    	}

		projTexProgram.delete();
		diffuseFitProgram.delete();
		specularFitProgram.delete();
		diffuseDebugProgram.delete();
		specularDebugProgram.delete();
    	depthRenderingProgram.delete();
	}
}
