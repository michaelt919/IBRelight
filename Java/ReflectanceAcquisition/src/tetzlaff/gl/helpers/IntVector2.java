package tetzlaff.gl.helpers;

public class IntVector2 
{
	public final int x;
	public final int y;

	public IntVector2(int x, int y)
	{
		this.x = x;
		this.y = y;
	}
	
	public IntVector2 plus(IntVector2 other)
	{
		return new IntVector2(
			this.x + other.x,
			this.y + other.y
		);
	}
	
	public IntVector2 minus(IntVector2 other)
	{
		return new IntVector2(
			this.x - other.x,
			this.y - other.y
		);
	}
	
	public IntVector2 negated()
	{
		return new IntVector2(-this.x, -this.y);
	}
	
	public IntVector2 times(int s)
	{
		return new IntVector2(s*this.x, s*this.y);
	}
	
	public IntVector2 dividedBy(int s)
	{
		return new IntVector2(this.x/s, this.y/s);
	}
	
	public int dot(IntVector2 other)
	{
		return this.x * other.x + this.y * other.y;
	}
	
	public double length()
	{
		return Math.sqrt(this.dot(this));
	}
	
	public double distance(IntVector2 other)
	{
		return this.minus(other).length();
	}
}
