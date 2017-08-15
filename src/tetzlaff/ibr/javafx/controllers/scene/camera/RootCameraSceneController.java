package tetzlaff.ibr.javafx.controllers.scene.camera;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ResourceBundle;

import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionModel;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import tetzlaff.ibr.javafx.models.JavaFXCameraModel;
import tetzlaff.ibr.javafx.models.JavaFXToolSelectionModel;
import tetzlaff.ibr.tools.ToolBox;

import com.sun.javafx.collections.ObservableListWrapper;

public class RootCameraSceneController implements Initializable {

    private final ObservableList<CameraSetting> listOfCameras = new ObservableListWrapper<CameraSetting>(new ArrayList<CameraSetting>());
    @FXML
    private VBox settings;
    @FXML
    private SettingsCameraSceneController settingsController;
    @FXML
    private ListView<CameraSetting> cameraListView;
    @FXML
    private VBox listControls;
    @FXML
    private Button theRenameButton;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        cameraListView.setItems(listOfCameras);

        cameraListView.getSelectionModel().selectedItemProperty().addListener(settingsController.changeListener);

        CameraSetting freeCam = new CameraSetting(
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                1.0,
                1.0,
                false,
                true,
                "Free Camera"

        );
        listOfCameras.add(freeCam);
        cameraListView.getSelectionModel().select(freeCam);

    }

    public void init2(JavaFXCameraModel cameraModel, JavaFXToolSelectionModel toolModel){

        System.out.println("Cam in!");

        cameraModel.setSelectedCameraSettingProperty(
                cameraListView.getSelectionModel().selectedItemProperty()
        );

        settingsController.setOnActionSelectPoint(event -> toolModel.setTool(ToolBox.ToolType.CENTER_POINT));
    }

    private SelectionModel<CameraSetting> getCameraSelectionModel() {
        return cameraListView.getSelectionModel();
    }

    @FXML
    private void newCameraButton() {
        listOfCameras.add(getCameraSelectionModel().getSelectedItem().duplicate());
        getCameraSelectionModel().select(listOfCameras.size() - 1);
    }

    @FXML
    private void saveCameraButton() {
        System.out.println("TODO: saved " + getCameraSelectionModel().getSelectedItem() + " to the library.");
    }

    @FXML
    private void renameCameraButton() {
        if (getCameraSelectionModel().getSelectedIndex() == 0) return;

        EventHandler<ActionEvent> oldOnAction = theRenameButton.getOnAction();//backup the old on action event for the rename button

        //set up two buttons and a text field for name entry
        listControls.getChildren().iterator().forEachRemaining(n -> {
            n.setDisable(true);
        });
        theRenameButton.setDisable(false);

        settings.setDisable(true);

        int renameIndex = listControls.getChildren().indexOf(theRenameButton);

        TextField renameTextField = new TextField();

        listControls.getChildren().add(renameIndex + 1, renameTextField);

        Button cancelRenameButton = new Button("Cancel");

        listControls.getChildren().add(renameIndex + 2, cancelRenameButton);

        renameTextField.setMaxWidth(Double.MAX_VALUE);

        cancelRenameButton.setMaxWidth(Double.MAX_VALUE);

        //set up to event handlers, one to return the controls back to their original state,
        //and the other to actually perform the rename
        EventHandler<ActionEvent> finishRename = new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                listControls.getChildren().removeAll(renameTextField, cancelRenameButton);
                theRenameButton.setOnAction(oldOnAction);

                listControls.getChildren().iterator().forEachRemaining(n -> {
                    n.setDisable(false);
                });

                cameraListView.refresh();

                settings.setDisable(false);
            }
        };

        EventHandler<ActionEvent> doRename = new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                String newName = renameTextField.getText();
                if (!newName.equals("")) {
                    getCameraSelectionModel().getSelectedItem().setName(newName);
                }

                finishRename.handle(event);
            }
        };

        //set the on actions
        theRenameButton.setOnAction(doRename);
        cancelRenameButton.setOnAction(finishRename);
        renameTextField.setOnAction(doRename);

        renameTextField.setText(getCameraSelectionModel().getSelectedItem().getName());
        renameTextField.requestFocus();
        renameTextField.selectAll();
    }

    @FXML
    private void moveUPButton() {
        int i = getCameraSelectionModel().getSelectedIndex();
        if (i > 1) {
            Collections.swap(listOfCameras, i, i - 1);
            getCameraSelectionModel().select(i - 1);
        }
    }

    @FXML
    void moveDOWNButton() {
        int i = getCameraSelectionModel().getSelectedIndex();
        if (i != 0 && i < listOfCameras.size() - 1) {
            Collections.swap(listOfCameras, i, i + 1);
            getCameraSelectionModel().select(i + 1);
        }
    }

    @FXML
    void lockCameraButton() {
        Boolean newValue = ! getCameraSelectionModel().getSelectedItem().isLocked();
        getCameraSelectionModel().getSelectedItem().setLocked(newValue);
        settingsController.setDisabled(newValue);
        cameraListView.refresh();
    }

    @FXML
    void keyframeCameraButton() {
        //TODO
        System.out.println("TODO: keyframe added for " + getCameraSelectionModel().getSelectedItem());
    }

    @FXML
    void deleteCameraButton() {
        int i = getCameraSelectionModel().getSelectedIndex();
        if (i != 0) listOfCameras.remove(i);
    }


}