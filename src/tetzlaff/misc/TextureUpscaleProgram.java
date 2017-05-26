package tetzlaff.misc;

import java.io.File;
import java.io.IOException;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import tetzlaff.gl.Drawable;
import tetzlaff.gl.FramebufferObject;
import tetzlaff.gl.PrimitiveMode;
import tetzlaff.gl.Program;
import tetzlaff.gl.ShaderType;
import tetzlaff.gl.Texture2D;
import tetzlaff.gl.VertexBuffer;
import tetzlaff.gl.glfw.GLFWWindow;
import tetzlaff.gl.glfw.GLFWWindowFactory;
import tetzlaff.gl.opengl.OpenGLContext;
import tetzlaff.gl.vecmath.Vector4;

public class TextureUpscaleProgram
{
	private static final float GAMMA = 2.2f;
	private static final int SCALE_FACTOR = 16;
	private static final float CLOUD_AMPLITUDE = 8.0f;
	private static final int CLOUD_SCALE = 16;
	private static final int CLOUD_DEPTH = 8;
	private static final int SAMPLE_RADIUS = 8;
	private static final float WEIGHT_EXPONENT = 1.0f;
	private static final float SHARPNESS = 0.0f;
	private static final int MAX_SAMPLES = 32;
	
	public static void main(String[] args)
    {
		try(GLFWWindow<OpenGLContext> window = GLFWWindowFactory.buildOpenGLWindow("Texture Upscale", 800, 800).create())
		{
	    	OpenGLContext context = window.getContext();
	        try
	        {
		    	Program<OpenGLContext> perlinNoiseProgram = context.getShaderProgramBuilder()
	    				.addShader(ShaderType.VERTEX, new File("shaders", "common/texture.vert"))
	    				.addShader(ShaderType.FRAGMENT, new File("shaders", "misc/perlintex.frag"))
	    				.createProgram();
		    	Texture2D<OpenGLContext> permTexture = context.buildPerlinNoiseTexture().createTexture();
		    	perlinNoiseProgram.setTexture("permTexture", permTexture);
	    		
	    		JFileChooser fileChooser = new JFileChooser(new File("").getAbsolutePath());
	    		fileChooser.removeChoosableFileFilter(fileChooser.getAcceptAllFileFilter());
	    		fileChooser.setFileFilter(new FileNameExtensionFilter("PNG images (.png)", "png"));
	    		
	    		if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
	    		{
	    	    	File imageFile = fileChooser.getSelectedFile();
	    	    	Texture2D<OpenGLContext> imageTexture = context.build2DColorTextureFromFile(imageFile, true)
		    										.setLinearFilteringEnabled(true)
		    										.setMipmapsEnabled(true)
		    										.createTexture();
	    	    	Texture2D<OpenGLContext> segmentTexture = context.build2DColorTextureFromFile(new File(new File(imageFile.getParent(), "segment"), imageFile.getName()), true).createTexture();
	    	    	perlinNoiseProgram.setTexture("imageTexture", imageTexture);
	    	    	perlinNoiseProgram.setTexture("segmentTexture", segmentTexture);
	    	    	int targetWidth = imageTexture.getWidth() * SCALE_FACTOR;
	    	    	int targetHeight = imageTexture.getHeight() * SCALE_FACTOR;
	    	    	perlinNoiseProgram.setUniform("gamma", GAMMA);
	    	    	perlinNoiseProgram.setUniform("imageWidth", imageTexture.getWidth());
	    	    	perlinNoiseProgram.setUniform("imageHeight", imageTexture.getHeight());
	    	    	perlinNoiseProgram.setUniform("targetWidth", targetWidth);
	    	    	perlinNoiseProgram.setUniform("targetHeight", targetHeight);
	    	    	perlinNoiseProgram.setUniform("cloudAmplitude", CLOUD_AMPLITUDE);
	    	    	perlinNoiseProgram.setUniform("cloudScale", CLOUD_SCALE);
	    	    	perlinNoiseProgram.setUniform("cloudDepth", CLOUD_DEPTH);
	    	    	perlinNoiseProgram.setUniform("sampleRadius", SAMPLE_RADIUS);
	    	    	perlinNoiseProgram.setUniform("weightExponent", WEIGHT_EXPONENT);
	    	    	perlinNoiseProgram.setUniform("sharpness", SHARPNESS);
	    	    	perlinNoiseProgram.setUniform("maxSamples", MAX_SAMPLES);
	    	    	perlinNoiseProgram.setUniform("blackPoint", new Vector4(0.0f, 0.0f, 0.0f, 0.0f));
	    	    	perlinNoiseProgram.setUniform("whitePoint", new Vector4(1.0f, 1.0f, 1.0f, 1.0f));
			    	FramebufferObject<OpenGLContext> fbo = context.buildFramebufferObject(targetWidth, targetHeight).addColorAttachment().createFramebufferObject();
			    	Drawable<OpenGLContext> drawable = context.createDrawable(perlinNoiseProgram);
			    	VertexBuffer<OpenGLContext> rectangle = context.createRectangle();
			    	drawable.addVertexBuffer("position", rectangle);
			    	fbo.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, 0.0f);
			    	drawable.draw(PrimitiveMode.TRIANGLE_FAN, fbo);
			    	new File(imageFile.getParentFile(), "output").mkdirs();
			        fbo.saveColorBufferToFile(0, "PNG", new File(new File(imageFile.getParentFile(), "output"), imageFile.getName()));
			    	fbo.close();
			    	imageTexture.close();
			    	rectangle.close();
	    		}
	    		
	    		perlinNoiseProgram.close();
	    		permTexture.close();
	        }
	        catch (IOException e)
	        {
	        	e.printStackTrace();
	        }
		}
        
        System.out.println("Process terminated without errors.");
        GLFWWindow.closeAllWindows();
        System.exit(0);
	}
}