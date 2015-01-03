package tetzlaff.gl.opengl;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;
import static tetzlaff.gl.opengl.helpers.StaticHelpers.*;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public abstract class OpenGLBuffer implements OpenGLResource
{
	private int bufferId;
	private int usage;

	protected OpenGLBuffer(int usage) 
	{
		this.bufferId = glGenBuffers();
		openGLErrorCheck();
		this.usage = usage;
	}

	protected OpenGLBuffer(ByteBuffer data, int usage) 
	{
		this(usage);
		this.setData(data);
	}

	protected OpenGLBuffer(ShortBuffer data, int usage) 
	{
		this(usage);
		this.setData(data);
	}

	protected OpenGLBuffer(IntBuffer data, int usage) 
	{
		this(usage);
		this.setData(data);
	}

	protected OpenGLBuffer(FloatBuffer data, int usage) 
	{
		this(usage);
		this.setData(data);
	}

	protected OpenGLBuffer(DoubleBuffer data, int usage) 
	{
		this(usage);
		this.setData(data);
	}
	
	protected int getBufferId()
	{
		return this.bufferId;
	}
	
	protected abstract int getBufferTarget();
	
	protected void setData(ByteBuffer data)
	{
		glBindBuffer(this.getBufferTarget(), this.bufferId);
		openGLErrorCheck();
		glBufferData(this.getBufferTarget(), data, this.usage);
		openGLErrorCheck();
	}
	
	protected void setData(ShortBuffer data)
	{
		glBindBuffer(this.getBufferTarget(), this.bufferId);
		openGLErrorCheck();
		glBufferData(this.getBufferTarget(), data, this.usage);
		openGLErrorCheck();
	}
	
	protected void setData(IntBuffer data)
	{
		glBindBuffer(this.getBufferTarget(), this.bufferId);
		openGLErrorCheck();
		glBufferData(this.getBufferTarget(), data, this.usage);
		openGLErrorCheck();
	}
	
	protected void setData(FloatBuffer data)
	{
		glBindBuffer(this.getBufferTarget(), this.bufferId);
		openGLErrorCheck();
		glBufferData(this.getBufferTarget(), data, this.usage);
		openGLErrorCheck();
	}
	
	protected void setData(DoubleBuffer data)
	{
		glBindBuffer(this.getBufferTarget(), this.bufferId);
		openGLErrorCheck();
		glBufferData(this.getBufferTarget(), data, this.usage);
		openGLErrorCheck();
	}
	
	protected void bind()
	{
		glBindBuffer(this.getBufferTarget(), this.bufferId);
		openGLErrorCheck();
	}
	
	void bindToIndex(int index)
	{
		glBindBufferBase(this.getBufferTarget(), index, this.bufferId);
		openGLErrorCheck();
	}
	
	protected static void unbindFromIndex(int bufferTarget, int index)
	{
		glBindBufferBase(bufferTarget, index, 0);
		openGLErrorCheck();
	}

	@Override
	public void delete()
	{
		glDeleteBuffers(this.bufferId);
		openGLErrorCheck();
	}
}