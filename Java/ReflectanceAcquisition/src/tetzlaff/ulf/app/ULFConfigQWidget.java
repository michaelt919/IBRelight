package tetzlaff.ulf.app;

import java.io.File;
import java.io.IOException;

import tetzlaff.interactive.EventPollable;
import tetzlaff.ulf.ULFListModel;
import tetzlaff.ulf.ULFLoadOptions;
import tetzlaff.ulf.ULFLoadingMonitor;
import tetzlaff.ulf.ULFMorphRenderer;
import tetzlaff.ulf.ViewSetImageOptions;

import com.trolltech.qt.core.QCoreApplication;
import com.trolltech.qt.gui.QCloseEvent;
import com.trolltech.qt.gui.QFileDialog;
import com.trolltech.qt.gui.QFileDialog.Filter;
import com.trolltech.qt.gui.QWidget;

public class ULFConfigQWidget extends QWidget implements EventPollable {

	private Ui_LightFieldConfigForm gui;
	private final ULFListModel<?> model;
	private boolean widgetClosed;
	private boolean halfResDefault;
	private boolean blockSignals;
	
	public Signal0 loadingFinished;
	
	public ULFConfigQWidget(ULFListModel<?> model, boolean isHighDPI, QWidget parent) {
		super(parent);
		this.blockSignals = true;

		this.loadingFinished = new Signal0();
		loadingFinished.connect(this, "onModelChanged()");
		
		this.halfResDefault = isHighDPI;
		this.widgetClosed = false;
		this.model = model;
		
		gui = new Ui_LightFieldConfigForm();
		gui.setupUi(this);
		
		if(this.model != null && this.model.getSelectedItem() != null) {
			this.model.getSelectedItem().setHalfResolution(isHighDPI);
		}
		
		gui.loadingProgressBar.setHidden(true);
		gui.halfResCheckBox.setChecked(halfResDefault);

		this.model.setLoadingMonitor(new ULFLoadingMonitor() {
			@Override
			public void setProgress(double progress)
			{
				gui.loadingProgressBar.setHidden(false);
				gui.loadingProgressBar.setMaximum(100);
				gui.loadingProgressBar.setValue((int)Math.round(progress * 100));
			}

			@Override
			public void loadingComplete()
			{
				gui.loadingProgressBar.setHidden(true);
				model.getSelectedItem().setHalfResolution(halfResDefault);
				loadingFinished.emit();
			}
		});
		this.blockSignals = false;
		onModelChanged();
	}

	// Set values from the 'model' parameter
	public void onModelChanged()
	{
		if(blockSignals) { return; }
		blockSignals = true;
		boolean enable = (model != null && model.getSelectedItem() != null);
		
		gui.modelComboBox.clear();
		gui.modelComboBox.setEnabled(enable?model.getSize()>1:false);

		gui.modelSlider.setEnabled(enable);	
		gui.gammaLabel.setEnabled(enable);
		gui.gammaSpinBox.setEnabled(enable);
		gui.exponentLabel.setEnabled(enable);
		gui.exponentSpinBox.setEnabled(enable);
		gui.visibilityCheckBox.setEnabled(enable);
		gui.visibilityBiasLabel.setEnabled(enable);
		gui.visibilityBiasSpinBox.setEnabled(enable);
		
		gui.halfResCheckBox.setEnabled(enable);
		gui.multisamplingCheckBox.setEnabled(enable);

		gui.resampleDimensionsLabel.setEnabled(enable);
		gui.resampleWidthSpinner.setEnabled(enable);
		gui.resampleXLabel.setEnabled(enable);
		gui.resampleHeightSpinner.setEnabled(enable);
		
		gui.resampleButton.setEnabled(enable);
		
		if(enable)
		{
			String selected = model.getSelectedItem().toString();
			for(int i=0; i<model.getSize(); i++) {
				String nextItem = model.getElementAt(i).toString();
				gui.modelComboBox.addItem(nextItem);
				if(nextItem.equals(selected))
				{
					gui.modelComboBox.setCurrentIndex(i);
				}
			}
			
			gui.gammaSpinBox.setValue(model.getSelectedItem().getGamma());
			gui.exponentSpinBox.setValue(model.getSelectedItem().getWeightExponent());
			gui.visibilityCheckBox.setChecked(model.getSelectedItem().isOcclusionEnabled());
			gui.visibilityBiasSpinBox.setValue(model.getSelectedItem().getOcclusionBias());

			if (model.getSelectedItem() instanceof ULFMorphRenderer<?>)
			{
				ULFMorphRenderer<?> morph = (ULFMorphRenderer<?>)(model.getSelectedItem());
				int currentStage = morph.getCurrentStage();
				gui.modelSlider.setEnabled(true);
				gui.modelSlider.setMaximum(morph.getStageCount() - 1);
				gui.modelSlider.setValue(currentStage);
			}
			else
			{
				gui.modelSlider.setMaximum(0);
				gui.modelSlider.setValue(0);
				gui.modelSlider.setEnabled(false);
			}
		}
		
		blockSignals = false;
	}
	
	public ULFLoadOptions getLoadOptionsFromGui()
	{
		ViewSetImageOptions vsetOpts = 
				new ViewSetImageOptions(null, true,
						gui.mipmapsCheckbox.isChecked(),
						gui.compressCheckBox.isChecked());
		ULFLoadOptions loadOptions = new ULFLoadOptions(vsetOpts,
				gui.generateDepthImagesCheckBox.isChecked(),
				gui.depthImageWidthSpinner.value(),
				gui.depthImageHeightSpinner.value());
		
		return loadOptions;
	}
	
	// Add listener for the 'single' load button to read a single light field object.
	@SuppressWarnings("unused")
	private void on_loadSingleButton_clicked()
	{
		if(blockSignals) { return; }
		String camDefFilename = QFileDialog.getOpenFileName(this,
									"Select a camera definition file", "",
									new Filter("Agisoft Photoscan XML (*.xml);;" +
											   "View Set Files (*.vset);;" +
											   "Zip Archive (*.zip)"));
		if(camDefFilename.isEmpty()) return;

		try
		{
			if(camDefFilename.toUpperCase().endsWith(".ZIP"))
			{
				camDefFilename = camDefFilename.substring(0, camDefFilename.length()-4);
				camDefFilename += "/default.vset";
				System.out.printf("Zip file name converted to '%s'\n", camDefFilename);
			}
			
			ULFLoadOptions loadOptions = getLoadOptionsFromGui();
			
			if (camDefFilename.toUpperCase().endsWith(".XML"))
			{
				String meshFilename = QFileDialog.getOpenFileName(this,
						"Select the corresponding mesh", camDefFilename,
						new Filter("Wavefront OBJ (*.obj)"));
				if(meshFilename.isEmpty()) return;
				
				String undistImageDir = QFileDialog.getExistingDirectory(this,
						"Select the undistorted image directory", meshFilename);
				if(undistImageDir.isEmpty()) return;
				
				loadOptions.getImageOptions().setFilePath(new File(undistImageDir));
				model.addFromAgisoftXMLFile(new File(camDefFilename), new File(meshFilename), loadOptions);
			}
			else
			{
				model.addFromVSETFile(new File(camDefFilename), loadOptions);				
			}
			
			gui.loadingProgressBar.setMaximum(0);
			gui.loadingProgressBar.setHidden(false);
		}
		catch (IOException ex) 
		{
			System.err.println("Error while loading model.");
			ex.printStackTrace();
		}
	}
	
	// Add listener for the 'morph' load button to read many light field objects.
	@SuppressWarnings("unused")
	private void on_loadMultipleButton_clicked()
	{
		if(blockSignals) { return; }
		String sequenceFilename = QFileDialog.getOpenFileName(this,
				"Select a sequence file", "", new Filter("light field sequence file (*.lfm)"));
		if(sequenceFilename.isEmpty()) return;
	
		try 
		{
			model.addMorphFromLFMFile(new File(sequenceFilename), getLoadOptionsFromGui());
			gui.loadingProgressBar.setMaximum(0);
			gui.loadingProgressBar.setHidden(false);
		} 
		catch (IOException ex) 
		{
			ex.printStackTrace();
		}
	}
	
	// Respond to combo box item changed event
	@SuppressWarnings("unused")
	private void on_modelComboBox_currentIndexChanged(int newIndex)
	{
		if(blockSignals) { return; }
		System.out.println("Model changed");
		model.setSelectedItem(model.getElementAt(newIndex));
		onModelChanged();
	}
	
	// Add listener for the 'resample' button to generate new vies for the current light field.
	@SuppressWarnings("unused")
	private void on_resampleButton_clicked()
	{
		if(blockSignals) { return; }
		String vsetFilename = QFileDialog.getOpenFileName(this,
				"Choose a Target VSET File", "", new Filter("View Set File (*.vset)"));
		if(vsetFilename.isEmpty()) return;
		
		String outputDir = QFileDialog.getOpenFileName(this,
				"Choose an output directory", vsetFilename);
		if(outputDir.isEmpty()) return;
			
		try 
		{
			gui.loadingProgressBar.setMaximum(0);
			gui.loadingProgressBar.setHidden(false);
			model.getSelectedItem().requestResample(
				gui.resampleWidthSpinner.value(), gui.resampleHeightSpinner.value(), 
				new File(vsetFilename), new File(outputDir));
		} 
		catch (IOException ex)
		{
			ex.printStackTrace();
		}
	}
	
	@SuppressWarnings("unused")
	private void on_gammaSpinBox_valueChanged(double newValue)
	{
		if(blockSignals) { return; }
		System.out.println("Gamma changed");
		model.getSelectedItem().setGamma((float)newValue);
	}
	
	@SuppressWarnings("unused")
	private void on_exponentSpinBox_valueChanged(double newValue)
	{
		if(blockSignals) { return; }
		System.out.println("Exponent changed");
		model.getSelectedItem().setWeightExponent((float)newValue);
	}
	
	// Add listener for changes to half resolution checkbox.
	@SuppressWarnings("unused")
	private void on_halfResCheckBox_toggled(boolean isChecked)
	{
		if(blockSignals) { return; }
		System.out.println("Half Res changed");
		model.getSelectedItem().setHalfResolution(isChecked);
	}

	// Add listener for changes to occlusion checkbox.
	@SuppressWarnings("unused")
	private void on_visibilityCheckBox_toggled(boolean isChecked)
	{
		if(blockSignals) { return; }
		System.out.println("Visibility changed");
		model.getSelectedItem().setOcclusionEnabled(isChecked);
	}
	
	// Add listener for changes to the occlusion bias spinner.
	@SuppressWarnings("unused")
	private void on_visibilityBiasSpinBox_valueChanged(double newValue)
	{
		if(blockSignals) { return; }
		System.out.println("Bias changed");
		model.getSelectedItem().setOcclusionBias((float)newValue);
	}
	
	protected void closeEvent(QCloseEvent event)
	{
		event.accept();
		widgetClosed = true;
	}
	
	/**
	 * This should be called once after the object is constructed but before the event loop is begun.
	 * This is a convenience method so it is compatible with the ULFUserInterface class (which was the
	 * old GUI class). This makes the two classes essentially interchangeable.
	 */
	public void showGUI()
	{
		this.setVisible(true);
	}

	@Override
	public void pollEvents() {
		if(QCoreApplication.hasPendingEvents()) {
			QCoreApplication.sendPostedEvents();
			QCoreApplication.processEvents();
		}
	}

	@Override
	public boolean shouldTerminate() {
		return widgetClosed;
	}
}