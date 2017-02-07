package tetzlaff.imagebasedmicrofacet;

import java.util.function.Supplier;

import tetzlaff.gl.helpers.CameraController;
import tetzlaff.gl.helpers.LightController;
import tetzlaff.gl.helpers.Matrix3;
import tetzlaff.gl.helpers.Matrix4;
import tetzlaff.gl.helpers.Trackball;
import tetzlaff.gl.helpers.Vector3;
import tetzlaff.gl.helpers.Vector4;
import tetzlaff.gl.helpers.VertexMesh;
import tetzlaff.ulf.ViewSet;
import tetzlaff.window.Window;

public class HardcodedLightController implements LightController, OverrideableLightController
{
	private static float OBJECT_SCALE = 11.1f; // 11.1cm radius
	
	private Trackball cameraTrackball;
	Supplier<ViewSet<?>> viewSetSupplier;
	Supplier<VertexMesh> proxySupplier;
	private Vector3[] lightColors;
	private Vector3[] lightPositions;

	private Matrix4 cameraPoseOverride;
	
	public HardcodedLightController(Supplier<ViewSet<?>> viewSetSupplier, Supplier<VertexMesh> proxySupplier)
	{		
		this.viewSetSupplier = viewSetSupplier;
		this.proxySupplier = proxySupplier;
		this.cameraTrackball = new Trackball(1.0f, 0, 1, true);
	}
	
	public CameraController asCameraController()
	{
		return () -> cameraTrackball.getViewMatrix()
			;//	.times(new Matrix4(new Matrix3(viewSetSupplier.get().getCameraPose(0))));
	}
	
	public void addAsWindowListener(Window window)
	{
		cameraTrackball.addAsWindowListener(window);
	}

	@Override
	public int getLightCount() 
	{
		return 3;
	}

	@Override
	public Vector3 getLightColor(int i) 
	{
		if (i == 0)
		{
			return new Vector3(1.0f);
		}
		else 
			if (i == 1)
		{
			Vector3 lightPosition = new Vector3(-80.0f, 44.0f, -74.0f).times(1.0f / 77.0f);
			return new Vector3((float)Math.pow(2.0, 1.2) / lightPosition.dot(lightPosition));
		}
		else 
			if (i == 2)
		{
			Vector3 lightPosition = new Vector3(81.0f, 36.0f, 12.0f).times(1.0f / 77.0f);
			return new Vector3((float)Math.pow(2.0, 0.5) / lightPosition.dot(lightPosition));
		}
		else
		{
			return new Vector3(0.0f);
		}
	}
	
	@Override
	public void setLightColor(int i, Vector3 lightColor)
	{
		// Ignore
	}

	@Override
	public Matrix4 getLightMatrix(int i) 
	{
		Matrix4 cameraPose;
		
		if (cameraPoseOverride == null)
		{
			cameraPose = cameraTrackball.getViewMatrix();
		}
		else
		{
			cameraPose = cameraPoseOverride;
			
//			//cameraPose = cameraPoseOverride.times(Matrix4.translate(proxySupplier.get().getCentroid()));
//			cameraPose = 
//					new Matrix4(new Matrix3(viewSetSupplier.get().getCameraPose(0).transpose()))
//					.times(cameraPoseOverride)
//					.times(Matrix4.translate(proxySupplier.get().getCentroid()))
//					.times(Matrix4.scale(1.0f / proxySupplier.get().getBoundingRadius()));
//					//.times(new Matrix4(new Matrix3(viewSetSupplier.get().getCameraPose(0).transpose())));
		}
		
		if (i == 0)
		{
			Vector4 lightOffset = new Vector4(viewSetSupplier.get().getLightPosition(0)
				.times(1.0f / new Vector3(viewSetSupplier.get().getCameraPose(0)
								.times(new Vector4(proxySupplier.get().getCentroid(), 1.0f))).length()), 
				1.0f);
			
			// Only take the rotation of the default camera pose.
			return Matrix4.lookAt(
					new Vector3(cameraPose.quickInverse(0.001f).times(lightOffset)).normalized(), 
					new Vector3(0, 0, 0), 
					new Vector3(0, 1, 0));
			
			//return cameraPose;
		}
		else
		{
			Vector3 lightPosition = new Vector3(0.0f);
			
			if (i == 1)
			{
				lightPosition = new Vector3(-80.0f, 44.0f, -74.0f).times(1.0f / 77.0f);
			}
			else if (i == 2)
			{
				lightPosition = new Vector3(81.0f, 36.0f, 12.0f).times(1.0f / 77.0f);
			}
			
			Matrix3 projectedCameraRotation = Matrix3.scale(1.0f, 0.0f, 1.0f).times(new Matrix3(cameraPose));
			Vector3 projectedRotatedXAxis = projectedCameraRotation.times(new Vector3(1.0f, 0.0f, 0.0f)).normalized();
			Vector3 projectedRotatedZAxis = projectedCameraRotation.times(new Vector3(0.0f, 0.0f, 1.0f)).normalized();
			projectedRotatedZAxis = projectedRotatedZAxis.minus(projectedRotatedXAxis.times(projectedRotatedXAxis.dot(projectedRotatedZAxis))).normalized();
			
			Matrix3 turntableRotation = new Matrix3(
					projectedRotatedXAxis,
					projectedRotatedXAxis.cross(projectedRotatedZAxis).normalized().negated(), 
					projectedRotatedZAxis);
			
			return Matrix4.lookAt(lightPosition, new Vector3(0.0f), new Vector3(0.0f, 1.0f, 0.0f))
					.times(new Matrix4(turntableRotation));
		}
	}

	@Override
	public void overrideCameraPose(Matrix4 cameraPoseOverride) 
	{
		this.cameraPoseOverride = cameraPoseOverride;
	}

	@Override
	public void removeCameraPoseOverride()
	{
		this.cameraPoseOverride = null;
	}

	@Override
	public Vector3 getAmbientLightColor() 
	{
		return new Vector3(0.0f);
	}

	@Override
	public void setAmbientLightColor(Vector3 ambientLightColor) 
	{
		// Ignore
	}

	@Override
	public boolean getEnvironmentMappingEnabled() 
	{
		return false;
	}

	@Override
	public void setEnvironmentMappingEnabled(boolean enabled) 
	{
		// Ignore
	}

	@Override
	public int getSelectedLightIndex() 
	{
		return 0;
	}
}