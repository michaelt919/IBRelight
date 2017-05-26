package tetzlaff.gl.vecmath;

/**
 * A vector of two dimensions (for linear algebra calculations) backed by 
 * 32-bit integers.
 * All arithmetic other than length() or distance() will be integer arithmetic, including division.  
 * This is an immutable object.
 * 
 * @see Vector2
 * @author Michael Tetzlaff
 */
public class IntVector2 
{
	public final int x;
	public final int y;

	IntVector2(int x, int y)
	{
		this.x = x;
		this.y = y;
	}
	
	/**
	 * Construct a vector in two dimensions with the given values.
	 * @param value Value of both dimensions.
	 */
	public static IntVector2 fromScalar(int value)
	{
		return new IntVector2(value, value);
	}
	
	public static IntVector2 fromScalars(int x, int y)
	{
		return new IntVector2(x, y);
	}
	
	public static IntVector2 takeXY(IntVector3 v3)
	{
		return new IntVector2(v3.x, v3.y);
	}
	
	public static IntVector2 takeXY(IntVector4 v4)
	{
		return new IntVector2(v4.x, v4.y);
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