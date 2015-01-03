package tetzlaff.gl.helpers;

public class IntVector3 
{
	public final int x;
	public final int y;
	public final int z;

	public IntVector3(int x, int y, int z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	public IntVector3 plus(IntVector3 other)
	{
		return new IntVector3(
			this.x + other.x,
			this.y + other.y,
			this.z + other.z
		);
	}
	
	public IntVector3 minus(IntVector3 other)
	{
		return new IntVector3(
			this.x - other.x,
			this.y - other.y,
			this.z - other.z
		);
	}
	
	public IntVector3 negated()
	{
		return new IntVector3(-this.x, -this.y, -this.z);
	}
	
	public IntVector3 times(int s)
	{
		return new IntVector3(s*this.x, s*this.y, s*this.z);
	}
	
	public IntVector3 dividedBy(int s)
	{
		return new IntVector3(this.x/s, this.y/s, this.z/s);
	}
	
	public int dot(IntVector3 other)
	{
		return this.x * other.x + this.y * other.y + this.z * other.z;
	}
	
	public IntVector3 cross(IntVector3 other)
	{
		return new IntVector3(
			this.y * other.z - this.z * other.y,
			this.z * other.x - this.x * other.z,
			this.x * other.y - this.y * other.x
		);
	}
	
	public double length()
	{
		return Math.sqrt(this.dot(this));
	}
	
	public double distance(IntVector3 other)
	{
		return this.minus(other).length();
	}
}
