package tetzlaff.ibr.app.old.util;

import java.awt.*;
import java.io.File;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

import tetzlaff.ibr.app.old.IBRRequestUI;
import tetzlaff.ibr.core.ReadonlySettingsModel;
import tetzlaff.ibr.fidelity.FidelityMetricRequest;

public class FidelityMetricRequestUI implements IBRRequestUI
{
    private final Component parent;
    private final JFileChooser fileChooser;
    private final ReadonlySettingsModel settings;

    public FidelityMetricRequestUI(Component parent, JFileChooser fileChooser, ReadonlySettingsModel settings)
    {
        this.parent = parent;
        this.fileChooser = fileChooser;
        this.settings = settings;
    }

    @Override
    public FidelityMetricRequest prompt()
    {
        fileChooser.setDialogTitle("Choose an Export Filename");
        fileChooser.resetChoosableFileFilters();
        fileChooser.removeChoosableFileFilter(fileChooser.getAcceptAllFileFilter());
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setFileFilter(new FileNameExtensionFilter("Text files (.txt)", "txt"));

        if (fileChooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION)
        {
            File fidelityOutputFile = fileChooser.getSelectedFile();

            fileChooser.setDialogTitle("Choose a Target VSET File");
            fileChooser.resetChoosableFileFilters();
            fileChooser.removeChoosableFileFilter(fileChooser.getAcceptAllFileFilter());
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fileChooser.setFileFilter(new FileNameExtensionFilter("View Set files (.vset)", "vset"));

            if (fileChooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION)
            {
                File targetVSETFile = fileChooser.getSelectedFile();

                fileChooser.setDialogTitle("Choose a Mask File");
                fileChooser.resetChoosableFileFilters();
                fileChooser.removeChoosableFileFilter(fileChooser.getAcceptAllFileFilter());
                fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                fileChooser.setFileFilter(new FileNameExtensionFilter("Image files", "jpg", "jpeg", "png", "bmp", "wbmp", "gif"));

                if (fileChooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION)
                {
                    return new FidelityMetricRequest(fidelityOutputFile, targetVSETFile, fileChooser.getSelectedFile(), settings);
                }
                else
                {
                    return new FidelityMetricRequest(fidelityOutputFile, targetVSETFile, null, settings);
                }
            }
        }

        return null;
    }

}