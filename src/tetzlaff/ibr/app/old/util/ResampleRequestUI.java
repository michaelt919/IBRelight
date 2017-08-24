package tetzlaff.ibr.app.old.util;

import java.awt.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

import tetzlaff.ibr.app.old.IBRRequestUI;
import tetzlaff.ibr.rendering.ResampleRequest;

public class ResampleRequestUI implements IBRRequestUI
{
    private final Component parent;
    private final JFileChooser fileChooser;
    private final JSpinner spinnerWidth;
    private final JSpinner spinnerHeight;

    public ResampleRequestUI(Component parent, JFileChooser fileChooser, JSpinner spinnerWidth, JSpinner spinnerHeight)
    {
        this.parent = parent;
        this.fileChooser = fileChooser;
        this.spinnerWidth = spinnerWidth;
        this.spinnerHeight = spinnerHeight;
    }

    @Override
    public ResampleRequest prompt()
    {
        fileChooser.setDialogTitle("Choose a Target VSET File");
        fileChooser.resetChoosableFileFilters();
        fileChooser.removeChoosableFileFilter(fileChooser.getAcceptAllFileFilter());
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setFileFilter(new FileNameExtensionFilter("View Set files (.vset)", "vset"));
        if (fileChooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION)
        {
            JFileChooser exportFileChooser = new JFileChooser(fileChooser.getSelectedFile().getParentFile());
            exportFileChooser.setDialogTitle("Choose an Export Directory");
            exportFileChooser.removeChoosableFileFilter(exportFileChooser.getAcceptAllFileFilter());
            exportFileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

            if (exportFileChooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION)
            {
                return new ResampleRequest(
                    ((Number)spinnerWidth.getValue()).intValue(),
                    ((Number)spinnerHeight.getValue()).intValue(),
                    fileChooser.getSelectedFile(),
                    exportFileChooser.getSelectedFile());
            }
        }

        return null;
    }
}