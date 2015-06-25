package tetzlaff.reflacq;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.WindowListener;
import java.io.File;
import java.util.Arrays;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

public class TexGenUserInterface
{
	private JFrame frame;
	private JButton executeButton;
	
	private FilePicker vsetFilePicker;
	private FilePicker objFilePicker;
	private FilePicker imageDirectoryPicker;
	private FilePicker maskDirectoryPicker;
	private FilePicker outputDirectoryPicker;
	
	private JSpinner gammaSpinner;
	private JCheckBox cameraVisCheckBox;
	private JSpinner cameraVisBiasSpinner;
	
	private JSpinner textureSizeSpinner;
	private JSpinner textureSubdivSpinner;
	private JCheckBox imagePreprojUseCheckBox;
	private JCheckBox imagePreprojGenCheckBox;
	
	private JSpinner diffuseDeltaSpinner;
	private JSpinner diffuseIterationsSpinner;
	private JSpinner diffuseCompNormalSpinner;
	private JCheckBox diffuseCompNormalInfCheckBox;
	private JSpinner diffuseInputNormalSpinner;
	private JCheckBox diffuseInputNormalInfCheckBox;
	
	private JCheckBox specRoughCheckBox;
	private JCheckBox specNormalCheckBox;
	private JCheckBox blinnPhongCheckBox;
	
	private JSpinner specSubDiffuseSpinner;
	private JSpinner specInfluenceSpinner;
	private JSpinner specDetThreshSpinner;
	private JSpinner specCompNormalSpinner;
	private JSpinner specCompRoughSpinner;
	private JSpinner specDefaultRoughSpinner;
	private JSpinner defaultRoughnessSpinner;
	private JSpinner roughnessCapSpinner;
	
	private class FilePicker
	{
		File file = null;
	}
	
	private FilePicker addDirectoryPicker(JPanel panel, String name)
	{
		Box loadBox = new Box(BoxLayout.X_AXIS);
		JPanel loadWrapper = new JPanel();
		JButton loadButton = new JButton(name);
		loadWrapper.add(loadButton);
		loadWrapper.setBorder(new EmptyBorder(0, 10, 0, 10));
		loadBox.add(loadWrapper);
		JPanel labelWrapper = new JPanel();
		labelWrapper.setLayout(new BorderLayout());
		labelWrapper.setBorder(new EmptyBorder(0, 10, 0, 10));
		JLabel label = new JLabel("No file selected.");
		label.setHorizontalAlignment(SwingConstants.RIGHT);
		labelWrapper.add(label);
		loadBox.add(labelWrapper);
		panel.add(loadBox);
		
		FilePicker picker = new FilePicker();
		
		loadButton.addActionListener(e -> 
		{
			JFileChooser fileChooser = new JFileChooser(new File("").getAbsolutePath());
			fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION)
			{
				picker.file = fileChooser.getSelectedFile();
				String fileString = picker.file.toString();
				label.setText(fileString.length() < 32 ? fileString : "..." + fileString.substring(fileString.length() - 32));
			}
		});
		
		return picker;
	}
	
	private FilePicker addFilePicker(JPanel panel, String name, Iterable<FileFilter> filters)
	{
		Box loadBox = new Box(BoxLayout.X_AXIS);
		JPanel loadWrapper = new JPanel();
		JButton loadButton = new JButton(name);
		loadWrapper.add(loadButton);
		loadWrapper.setBorder(new EmptyBorder(0, 10, 0, 10));
		loadBox.add(loadWrapper);
		JPanel labelWrapper = new JPanel();
		labelWrapper.setLayout(new BorderLayout());
		labelWrapper.setBorder(new EmptyBorder(0, 10, 0, 10));
		JLabel label = new JLabel("No file selected.");
		label.setHorizontalAlignment(SwingConstants.RIGHT);
		labelWrapper.add(label);
		loadBox.add(labelWrapper);
		panel.add(loadBox);
		
		FilePicker picker = new FilePicker();
		
		loadButton.addActionListener(e -> 
		{
			
			JFileChooser fileChooser = new JFileChooser(new File("").getAbsolutePath());
			fileChooser.removeChoosableFileFilter(fileChooser.getAcceptAllFileFilter());
			for (FileFilter filter : filters)
			{
				fileChooser.addChoosableFileFilter(filter);
			}
			if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION)
			{
				picker.file = fileChooser.getSelectedFile();
				String fileString = picker.file.toString();
				label.setText(fileString.length() < 32 ? fileString : "..." + fileString.substring(fileString.length() - 32));
			}
		});
		
		return picker;
	}
	
	private JLabel addUILabel(JPanel panel, String name)
	{
		JPanel wrapper = new JPanel();
		wrapper.setLayout(new BorderLayout());
		JLabel label = new JLabel(name);
		label.setBorder(new EmptyBorder(25, 10, 5, 10));
		wrapper.add(label);
		panel.add(wrapper);
		return label;
	}
	
	private JSpinner addUIValueField(JPanel panel, String name, double defaultValue, double minValue, double maxValue, double stepSize)
	{
		Box box = new Box(BoxLayout.X_AXIS);
		JLabel label = new JLabel(name + ":");
		label.setPreferredSize(new Dimension(256, 16));
		box.add(label);
		SpinnerNumberModel model = new SpinnerNumberModel(defaultValue, minValue, maxValue, stepSize);
		JSpinner spinner = new JSpinner(model);
		box.add(spinner);
		box.setBorder(new EmptyBorder(5, 10, 5, 10));
		panel.add(box);
		return spinner;
	}
	
	private JCheckBox addUICheckBoxField(JPanel panel, String name, boolean checked)
	{
		JCheckBox checkBox = new JCheckBox(name, checked);
		checkBox.setBorder(new EmptyBorder(5, 10, 5, 10));
		panel.add(checkBox);
		return checkBox;
	}
	
	public TexGenUserInterface() 
	{
		this.frame = new JFrame("Texture Generation Program");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setLocation(10, 10);
		frame.setSize(256, 256);
		frame.setResizable(false);
		frame.getContentPane().setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.Y_AXIS));
		
		TexGenParameters defaults = new TexGenParameters();

		JPanel filePanel = new JPanel();
		filePanel.setLayout(new BoxLayout(filePanel, BoxLayout.Y_AXIS));
		filePanel.setBorder(new TitledBorder("Files and Directories"));
		vsetFilePicker = addFilePicker(filePanel, "Select Camera File...", Arrays.asList(
				new FileNameExtensionFilter("Agisoft Photoscan XML files (.xml)", "xml"),
				new FileNameExtensionFilter("View Set files (.vset)", "vset")));
		objFilePicker = addFilePicker(filePanel, "Select Model File...", Arrays.asList(new FileNameExtensionFilter("Wavefront OBJ files (.obj)", "obj")));
		imageDirectoryPicker = addDirectoryPicker(filePanel, "Select Images...");
		maskDirectoryPicker = addDirectoryPicker(filePanel, "Select Masks...");
		outputDirectoryPicker = addDirectoryPicker(filePanel, "Output Directory...");
		frame.add(filePanel);
		
		JPanel samplingPanel = new JPanel();
		samplingPanel.setLayout(new BoxLayout(samplingPanel, BoxLayout.Y_AXIS));
		samplingPanel.setBorder(new TitledBorder("Sampling Parameters"));
		gammaSpinner = addUIValueField(samplingPanel, "Gamma", defaults.getGamma(), 1.0f, 99.0f, 0.1f);
		cameraVisCheckBox = addUICheckBoxField(samplingPanel, "Enable Camera Visibility Test", defaults.isCameraVisibilityTestEnabled());
		cameraVisBiasSpinner = addUIValueField(samplingPanel, "Camera Visibility Test Bias", defaults.getCameraVisibilityTestBias(), 0.0f, 1.0f, 0.0001f);
		textureSizeSpinner = addUIValueField(samplingPanel, "Texture Size", defaults.getTextureSize(), 1.0f, 8192.0f, 1.0f);
		textureSubdivSpinner = addUIValueField(samplingPanel, "Texture Subdivision", defaults.getTextureSubdivision(), 1.0f, 8192.0f, 1.0f);
		imagePreprojGenCheckBox = addUICheckBoxField(samplingPanel, "Generate Pre-projected images", defaults.isImagePreprojectionGenerationEnabled());
		imagePreprojUseCheckBox = addUICheckBoxField(samplingPanel, "Use Pre-projected images", defaults.isImagePreprojectionUseEnabled());
		frame.add(samplingPanel);
		
		JPanel diffusePanel = new JPanel();
		diffusePanel.setLayout(new BoxLayout(diffusePanel, BoxLayout.Y_AXIS));
		diffusePanel.setBorder(new TitledBorder("Diffuse Fitting Parameters"));
		diffuseDeltaSpinner = addUIValueField(diffusePanel, "Delta", defaults.getDiffuseDelta(), 0.0f, 1.0f, 0.01f);
		diffuseIterationsSpinner = addUIValueField(diffusePanel, "Iterations", defaults.getDiffuseIterations(), 0.0f, 8.0f, 1.0f);
		diffuseCompNormalSpinner = addUIValueField(diffusePanel, "Computed Normal Weight", Math.min(9999.0f, defaults.getDiffuseComputedNormalWeight()), 0.0f, 9999.0f, 0.1f);
		diffuseCompNormalInfCheckBox = addUICheckBoxField(diffusePanel, "(infinite)", defaults.getDiffuseComputedNormalWeight() >= Float.MAX_VALUE);
		diffuseInputNormalSpinner = addUIValueField(diffusePanel, "Input Normal Weight", Math.min(9999.0f, defaults.getDiffuseInputNormalWeight()),  0.0f, 9999.0f, 0.1f);
		diffuseInputNormalInfCheckBox = addUICheckBoxField(diffusePanel, "(infinite)", defaults.getDiffuseInputNormalWeight() >= Float.MAX_VALUE);
		frame.add(diffusePanel);
		
		JPanel specularPanel = new JPanel();
		specularPanel.setLayout(new BoxLayout(specularPanel, BoxLayout.Y_AXIS));
		specularPanel.setBorder(new TitledBorder("Specular Fitting Parameters"));
		specRoughCheckBox = addUICheckBoxField(specularPanel,"Compute Roughness", defaults.isSpecularRoughnessComputationEnabled());
		specNormalCheckBox = addUICheckBoxField(specularPanel,"Compute Separate Normal", defaults.isSpecularNormalComputationEnabled());
		blinnPhongCheckBox = addUICheckBoxField(specularPanel,"Use \"True\" Blinn-Phong Model", defaults.isTrueBlinnPhongSpecularEnabled());
		specSubDiffuseSpinner = addUIValueField(specularPanel,"Diffuse Subtraction Amount", defaults.getSpecularSubtractDiffuseAmount(), 0.0f, 1.0f, 0.01f);
		specInfluenceSpinner = addUIValueField(specularPanel,"Influence Scale", defaults.getSpecularInfluenceScale(), 0.0f, 1.0f, 0.01f);
		specDetThreshSpinner = addUIValueField(specularPanel,"Determinant Threshold", defaults.getSpecularDeterminantThreshold(), 0.0f, 1.0f, 0.0001f);
		specCompNormalSpinner = addUIValueField(specularPanel,"Computed Normal Weight", defaults.getSpecularComputedNormalWeight(), 0.0f, 1.0f, 0.01f);
		specCompRoughSpinner = addUIValueField(specularPanel,"Input Normal, Computed Roughness Weight", defaults.getSpecularInputNormalComputedRoughnessWeight(), 0.0f, 1.0f, 0.01f);
		specDefaultRoughSpinner = addUIValueField(specularPanel,"Input Normal, Default Roughness Weight", defaults.getSpecularInputNormalDefaultRoughnessWeight(), 0.0f, 1.0f, 0.01f);
		defaultRoughnessSpinner = addUIValueField(specularPanel,"Default Specular Roughness", defaults.getDefaultSpecularRoughness(), 0.0f, 10.0f, 0.01f);
		roughnessCapSpinner = addUIValueField(specularPanel,"Specular Roughness Cap", defaults.getSpecularRoughnessCap(), 0.0f, 10.0f, 0.01f);
		frame.add(specularPanel);
		
		JSpinner.NumberEditor cameraVisBiasNumberEditor = new JSpinner.NumberEditor(cameraVisBiasSpinner, "0.0000");
		cameraVisBiasSpinner.setEditor(cameraVisBiasNumberEditor);
		
		JSpinner.NumberEditor specDetThreshNumberEditor = new JSpinner.NumberEditor(specDetThreshSpinner, "0.0000");
		specDetThreshSpinner.setEditor(specDetThreshNumberEditor);
		
		Box executeBox = new Box(BoxLayout.X_AXIS);
		JPanel executeWrapper = new JPanel();
		executeButton = new JButton("Execute...");
		executeWrapper.add(executeButton);
		executeWrapper.setBorder(new EmptyBorder(0, 10, 0, 10));
		executeBox.add(executeWrapper);
		frame.add(executeBox);
		
		frame.pack();
		
		JFrame loadingFrame = new JFrame("Loading...");
		loadingFrame.setUndecorated(true);
		JProgressBar loadingBar = new JProgressBar();
		loadingBar.setIndeterminate(true);
		loadingFrame.add(loadingBar);
		loadingFrame.pack();
		loadingFrame.setLocationRelativeTo(null);
	}
	
	public File getCameraFile()
	{
		return vsetFilePicker.file;
	}
	
	public File getModelFile()
	{
		return objFilePicker.file;
	}
	
	public File getImageDirectory()
	{
		return imageDirectoryPicker.file;
	}
	
	public File getMaskDirectory()
	{
		return maskDirectoryPicker.file;
	}
	
	public File getOutputDirectory()
	{
		return outputDirectoryPicker.file;
	}
	
	private float getValueAsFloat(JSpinner spinner)
	{
		return (float)((double)((Double)spinner.getValue()));
	}
	
	private int getValueAsInt(JSpinner spinner)
	{
		return (int)Math.round(((double)((Double)spinner.getValue())));
	}
	
	public TexGenParameters getParameters()
	{
		TexGenParameters param = new TexGenParameters();
		param.setGamma(getValueAsFloat(this.gammaSpinner));
		param.setCameraVisibilityTestEnabled(this.cameraVisCheckBox.isSelected());
		param.setCameraVisibilityTestBias(getValueAsFloat(this.cameraVisBiasSpinner));
		param.setTextureSize(getValueAsInt(this.textureSizeSpinner));
		param.setTextureSubdivision(getValueAsInt(this.textureSubdivSpinner));
		param.setImagePreprojectionUseEnabled(this.imagePreprojUseCheckBox.isSelected());
		param.setImagePreprojectionGenerationEnabled(this.imagePreprojGenCheckBox.isSelected());
		param.setDiffuseDelta(getValueAsFloat(this.diffuseDeltaSpinner));
		param.setDiffuseIterations(getValueAsInt(this.diffuseIterationsSpinner));
		param.setDiffuseComputedNormalWeight(this.diffuseCompNormalInfCheckBox.isSelected() ? 
				Float.MAX_VALUE : getValueAsFloat(this.diffuseCompNormalSpinner));
		param.setDiffuseInputNormalWeight(this.diffuseInputNormalInfCheckBox.isSelected() ?
				Float.MAX_VALUE : getValueAsFloat(this.diffuseInputNormalSpinner));
		param.setSpecularRoughnessComputationEnabled(this.specRoughCheckBox.isSelected());
		param.setSpecularNormalComputationEnabled(this.specNormalCheckBox.isSelected());
		param.setTrueBlinnPhongSpecularEnabled(this.blinnPhongCheckBox.isSelected());
		param.setSpecularSubtractDiffuseAmount(getValueAsFloat(this.specSubDiffuseSpinner));
		param.setSpecularInfluenceScale(getValueAsFloat(this.specInfluenceSpinner));
		param.setSpecularDeterminantThreshold(getValueAsFloat(this.specDetThreshSpinner));
		param.setSpecularComputedNormalWeight(getValueAsFloat(this.specCompNormalSpinner));
		param.setSpecularInputNormalComputedRoughnessWeight(getValueAsFloat(this.specCompRoughSpinner));
		param.setSpecularInputNormalDefaultRoughnessWeight(getValueAsFloat(this.specDefaultRoughSpinner));
		param.setDefaultSpecularRoughness(getValueAsFloat(this.defaultRoughnessSpinner));
		param.setSpecularRoughnessCap(getValueAsFloat(this.roughnessCapSpinner));
		return param;
	}
	
	public void addExecuteButtonActionListener(ActionListener actionListener)
	{
		executeButton.addActionListener(actionListener);
	}
	
	public void addWindowListener(WindowListener windowListener)
	{
		frame.addWindowListener(windowListener);
	}

	public void show()
	{
		frame.setVisible(true);
	}
}