package openGL.wrappers.interfaces;

import java.io.IOException;

public interface Framebuffer
{
	int getWidth();

	int getHeight();

	void bindForDraw();
	void bindForDraw(int x, int y, int width, int height);

	int[] readPixelsARGB(int mode);
	int[] readPixelsARGB(int mode, int x, int y, int width, int height);

	void saveToFile(int readMode, String fileFormat, String filename) throws IOException;
}