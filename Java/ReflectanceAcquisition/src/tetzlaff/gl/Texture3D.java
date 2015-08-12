package tetzlaff.gl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import tetzlaff.helpers.ZipWrapper;

/**
 * An interface for a three-dimensional texture.
 * @author Michael Tetzlaff
 *
 * @param <ContextType> The type of the GL context that the texture is associated with.
 */
public interface Texture3D<ContextType extends Context<ContextType>> extends Texture<ContextType>
{
	int getWidth();
	int getHeight();
	int getDepth();
	
	void loadLayer(int layerIndex, InputStream fileStream, boolean flipVertical) throws IOException;
	void loadLayer(int layerIndex, ZipWrapper zipFile, boolean flipVertical) throws IOException;
	void loadLayer(int layerIndex, File file, boolean flipVertical) throws IOException;
	void loadLayer(int layerIndex, InputStream imageStream, InputStream maskStream, boolean flipVertical) throws IOException;
	void loadLayer(int layerIndex, ZipWrapper imageZip, ZipWrapper maskZip, boolean flipVertical) throws IOException;
	void loadLayer(int layerIndex, File imageFile, File maskFile, boolean flipVertical) throws IOException;
	void generateMipmaps();
	
	FramebufferAttachment<ContextType> getLayerAsFramebufferAttachment(int layerIndex);
}