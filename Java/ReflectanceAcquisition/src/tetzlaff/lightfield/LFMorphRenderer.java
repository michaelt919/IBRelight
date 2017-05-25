/*
 * LF Viewer - A tool to render Agisoft PhotoScan models as light fields.
 *
 * Copyright (c) 2016
 * The Regents of the University of Minnesota
 *     and
 * Cultural Heritage Imaging
 * All rights reserved
 *
 * This file is part of LF Viewer.
 *
 *     LF Viewer is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     LF Viewer is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with LF Viewer.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package tetzlaff.lightfield;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import tetzlaff.gl.Context;
import tetzlaff.gl.Program;
import tetzlaff.gl.helpers.Trackball;
import tetzlaff.gl.helpers.Vector4;

/**
 * A renderer for a sequence of related light fields.
 * This renderer acts as a aggregate LFDrawable for each stage of the morph, with draw requests directed towards the currently active stage of the morph.
 * @author Michael Tetzlaff
 *
 * @param <ContextType> The type of the context that will be used for rendering.
 */
public class LFMorphRenderer<ContextType extends Context<ContextType>> implements LFDrawable<ContextType>
{
	private ContextType context;
	private Program<ContextType> program;
    private File lfmFile;
    private LFLoadOptions loadOptions;
    private Trackball trackball;
    private String id;

    private LFLoadingMonitor callback;
	private List<LFRenderer<ContextType>> stages;
	private int currentStage;

	/**
	 * Creates a new light field morph renderer.
	 * @param contextThe GL context in which to perform the rendering.
     * @param program The program to use for rendering.
	 * @param lfmFile The file defining the stages of the light field morph to be loaded.
	 * @param loadOptions The options to use when loading the light field.
     * @param trackball The trackball controlling the movement of the virtual camera.
	 * @throws FileNotFoundException Thrown due to a File I/O error occurring.
	 */
	public LFMorphRenderer(ContextType context, Program<ContextType> program, File lfmFile, LFLoadOptions loadOptions, Trackball trackball) throws FileNotFoundException 
	{
		this.context = context;
		this.program = program;
		this.lfmFile = lfmFile;
		this.loadOptions = loadOptions;
		this.trackball = trackball;
		
		this.id = lfmFile.getParentFile().getName();
		
		this.stages = new ArrayList<LFRenderer<ContextType>>();
		this.currentStage = 0;
	}
	
	/**
	 * Gets the currently active stage of the light field morph.
	 * @return The currently active stage of the morph.
	 */
	public int getCurrentStage()
	{
		return this.currentStage;
	}
	
	/**
	 * Sets the currently active stage of the light field morph.
	 * @param newStage The currently active stage of the morph.
	 */
	public void setCurrentStage(int newStage)
	{
		this.currentStage = newStage;
	}
	
	/**
	 * Gets the number of stages in the morph.
	 * @return The number of stages in the morph.
	 */
	public int getStageCount()
	{
		return this.stages.size();
	}

	@Override
	public void initialize() 
	{
		try 
		{
			Scanner scanner = new Scanner(lfmFile);
			File directory = lfmFile.getParentFile();
			while (scanner.hasNextLine())
			{
				String vsetFileName = scanner.nextLine();
				stages.add(new LFRenderer<ContextType>(context, program, new File(directory, vsetFileName), loadOptions, trackball));
			}
			scanner.close();
			
			int stagesLoaded = 0;
			for(LFRenderer<ContextType> stage : stages)
			{
				System.out.println(stage.getVSETFile());
				callback.setProgress((double)stagesLoaded / (double)stages.size());
				stage.initialize();
				stagesLoaded++;
			}
		} 
		catch (FileNotFoundException e) 
		{
			e.printStackTrace();
		}
		
		callback.loadingComplete();
	}

	@Override
	public void update() 
	{
		for(LFRenderer<ContextType> stage : stages)
		{
			stage.update();
		}
	}

	@Override
	public boolean draw() 
	{
		return stages.get(currentStage).draw();
	}

	@Override
	public void saveToFile(String fileFormat, File file)
	{
		stages.get(currentStage).saveToFile(fileFormat, file);
	}
	
	@Override
	public void cleanup() 
	{
		for(LFRenderer<ContextType> stage : stages)
		{
			stage.cleanup();
		}
	}

	@Override
	public void setOnLoadCallback(LFLoadingMonitor callback) 
	{
		this.callback = callback;
	}
	
	public LightField<ContextType> getLightField()
	{
		return stages.get(currentStage).getLightField();
	}

	@Override
	public float getGamma() 
	{
		return this.getLightField().settings.getGamma();
	}

	@Override
	public float getWeightExponent() 
	{
		return this.getLightField().settings.getWeightExponent();
	}

	@Override
	public boolean isOcclusionEnabled() 
	{
		return this.getLightField().settings.isOcclusionEnabled();
	}

	@Override
	public float getOcclusionBias() 
	{
		return this.getLightField().settings.getOcclusionBias();
	}

	@Override
	public void setGamma(float gamma)
	{
		for (LFRenderer<ContextType> stage : stages)
		{
			stage.getLightField().settings.setGamma(gamma);
		}
	}

	@Override
	public void setWeightExponent(float weightExponent) 
	{
		for (LFRenderer<ContextType> stage : stages)
		{
			stage.getLightField().settings.setWeightExponent(weightExponent);
		}
	}

	@Override
	public void setOcclusionEnabled(boolean occlusionEnabled) 
	{
		for (LFRenderer<ContextType> stage : stages)
		{
			stage.getLightField().settings.setOcclusionEnabled(occlusionEnabled);
		}
	}

	@Override
	public void setOcclusionBias(float occlusionBias) 
	{
		for (LFRenderer<ContextType> stage : stages)
		{
			stage.getLightField().settings.setOcclusionBias(occlusionBias);
		}
	}
	
	@Override
	public String toString()
	{
		return this.id;
	}

	@Override
	public void requestResample(int width, int height, File targetVSETFile, File exportPath) throws IOException 
	{
		this.stages.get(this.currentStage).requestResample(width, height, targetVSETFile, exportPath);
	}

	@Override
	public void setHalfResolution(boolean halfResEnabled)
	{	
		this.stages.get(this.currentStage).setHalfResolution(halfResEnabled);
	}

	@Override
	public void setVisualizeCameras(boolean camerasEnabled)
	{	
		this.stages.get(this.currentStage).setVisualizeCameras(camerasEnabled);
	}

	@Override
	public boolean getHalfResolution()
	{	
		return this.stages.get(this.currentStage).getHalfResolution();
	}

	@Override
	public void setMultisampling(boolean multisamplingEnabled)
	{
		context.makeContextCurrent();
		if(multisamplingEnabled)
		{
			context.enableMultisampling();
		}
		else
		{
			context.disableMultisampling();			
		}		
	}

	@Override
	public void setProgram(Program<ContextType> program) 
	{
		for(LFRenderer<ContextType> stage : stages)
		{
			stage.setProgram(program);
		}
	}

	@Override
	public void setBackgroundColor(Vector4 RGBA) {
		for (LFRenderer<ContextType> stage : stages)
		{
			stage.setBackgroundColor(RGBA);
		}
	}

	@Override
	public Vector4 getBackgroundColor() {
		return this.stages.get(this.currentStage).getBackgroundColor();
	}

	@Override
	public boolean isKNeighborsEnabled() {
		return this.stages.get(this.currentStage).isKNeighborsEnabled();
	}

	@Override
	public void setKNeighborsEnabled(boolean kNeighborsEnabled) {
		for (LFRenderer<ContextType> stage : stages)
		{
			stage.setKNeighborsEnabled(kNeighborsEnabled);
		}
	}

	@Override
	public int getKNeighborCount() {
		return this.stages.get(this.currentStage).getKNeighborCount();
	}

	@Override
	public void setKNeighborCount(int kNeighborCount) {
		for (LFRenderer<ContextType> stage : stages)
		{
			stage.setKNeighborCount(kNeighborCount);
		}
	}
}