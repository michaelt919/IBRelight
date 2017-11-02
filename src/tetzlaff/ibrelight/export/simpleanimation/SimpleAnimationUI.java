package tetzlaff.ibrelight.export.simpleanimation;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import tetzlaff.ibrelight.core.IBRRequest;
import tetzlaff.ibrelight.core.IBRRequestUI;
import tetzlaff.ibrelight.core.IBRelightModels;
import tetzlaff.ibrelight.export.simpleanimation.SimpleAnimationRequestBase.Builder;

public class SimpleAnimationUI implements IBRRequestUI
{
    @FXML private TextField widthTextField;
    @FXML private TextField heightTextField;
    @FXML private TextField exportDirectoryField;
    @FXML private TextField frameCountTextField;
    @FXML private Button runButton;

    private final DirectoryChooser directoryChooser = new DirectoryChooser();
    private File lastDirectory;

    private Supplier<Builder<?>> builderSupplier;

    private Stage stage;

    public static SimpleAnimationUI create(Window window, IBRelightModels modelAccess) throws IOException
    {
        String fxmlFileName = "fxml/export/SimpleAnimationUI.fxml";
        URL url = SimpleAnimationUI.class.getClassLoader().getResource(fxmlFileName);
        assert url != null : "Can't find " + fxmlFileName;

        FXMLLoader fxmlLoader = new FXMLLoader(url);
        Parent parent = fxmlLoader.load();
        SimpleAnimationUI simpleAnimationUI = fxmlLoader.getController();

        simpleAnimationUI.stage = new Stage();
        simpleAnimationUI.stage.setTitle("Animation request");
        simpleAnimationUI.stage.setScene(new Scene(parent));
        simpleAnimationUI.stage.initOwner(window);

        return simpleAnimationUI;
    }

    public void setBuilderSupplier(Supplier<Builder<?>> builderSupplier)
    {
        this.builderSupplier = builderSupplier;
    }

    @FXML
    private void exportDirectoryButtonAction()
    {
        this.directoryChooser.setTitle("Choose an export directory");
        if (exportDirectoryField.getText().isEmpty())
        {
            if (lastDirectory != null)
            {
                this.directoryChooser.setInitialDirectory(lastDirectory);
            }
        }
        else
        {
            File currentValue = new File(exportDirectoryField.getText());
            this.directoryChooser.setInitialDirectory(currentValue);
        }
        File file = this.directoryChooser.showDialog(stage.getOwner());
        if (file != null)
        {
            exportDirectoryField.setText(file.toString());
            lastDirectory = file;
        }
    }

    @FXML
    public void cancelButtonAction(ActionEvent actionEvent)
    {
        stage.close();
    }

    @Override
    public void prompt(Consumer<IBRRequest> requestHandler)
    {
        stage.show();

        runButton.setOnAction(event ->
        {
            //stage.close();
            if (builderSupplier != null)
            {
                requestHandler.accept(
                    builderSupplier.get()
                        .setWidth(Integer.parseInt(widthTextField.getText()))
                        .setHeight(Integer.parseInt(heightTextField.getText()))
                        .setFrameCount(Integer.parseInt(frameCountTextField.getText()))
                        .setExportPath(new File(exportDirectoryField.getText()))
                        .create());
            }
        });
    }
}
