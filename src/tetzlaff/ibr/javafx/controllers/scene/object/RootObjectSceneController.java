package tetzlaff.ibr.javafx.controllers.scene.object;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;

import javafx.collections.ListChangeListener;
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
import tetzlaff.ibr.javafx.models.JavaFXModelAccess;
import tetzlaff.ibr.javafx.models.JavaFXObjectModel;

public class RootObjectSceneController implements Initializable
{
    @FXML
    private VBox settings;
    @FXML
    private SettingsObjectSceneController settingsController;
    @FXML
    private ListView<ObjectPoseSetting> objectPoseListView;
    @FXML
    private VBox listControls;
    @FXML
    private Button renameButton;

    @Override
    public void initialize(URL location, ResourceBundle resources)
    {
        objectPoseListView.setItems(JavaFXModelAccess.getInstance().getSceneModel().getObjectPoseList());
        objectPoseListView.getSelectionModel().selectedItemProperty().addListener(settingsController.changeListener);

        ObjectPoseSetting defaultPose = new ObjectPoseSetting(
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            false,
            "Default Pose"
        );

        ObservableList<ObjectPoseSetting> objectPoseList = JavaFXModelAccess.getInstance().getSceneModel().getObjectPoseList();

        objectPoseList.add(defaultPose);
        objectPoseListView.getSelectionModel().select(defaultPose);

        objectPoseList.addListener((ListChangeListener<? super ObjectPoseSetting>) change ->
        {
            change.next();
            if (change.wasAdded() && change.getAddedSize() == objectPoseList.size())
            {
                objectPoseListView.getSelectionModel().select(0);
            }
        });
    }

    public void init(JavaFXObjectModel objectModel)
    {
        objectModel.setSelectedObjectPoseSettingProperty(objectPoseListView.getSelectionModel().selectedItemProperty());
    }

    private SelectionModel<ObjectPoseSetting> getObjectPoseSelectionModel()
    {
        return objectPoseListView.getSelectionModel();
    }

    @FXML
    private void newPoseButton()
    {
        JavaFXModelAccess.getInstance().getSceneModel().getObjectPoseList()
            .add(getObjectPoseSelectionModel().getSelectedItem().duplicate());
        getObjectPoseSelectionModel().select(JavaFXModelAccess.getInstance().getSceneModel().getObjectPoseList().size() - 1);
    }

    @FXML
    private void savePoseButton()
    {
        System.out.println("TODO: saved " + getObjectPoseSelectionModel().getSelectedItem() + " to the library.");
    }

    @FXML
    private void renamePoseButton()
    {
        if (getObjectPoseSelectionModel().getSelectedIndex() == 0)
        {
            return;
        }

        EventHandler<ActionEvent> oldOnAction = renameButton.getOnAction();//backup the old on action event for the rename button

        //set up two buttons and a text field for name entry
        listControls.getChildren().iterator().forEachRemaining(n -> n.setDisable(true));
        renameButton.setDisable(false);

        settings.setDisable(true);

        int renameIndex = listControls.getChildren().indexOf(renameButton);

        TextField renameTextField = new TextField();

        listControls.getChildren().add(renameIndex + 1, renameTextField);

        Button cancelRenameButton = new Button("Cancel");

        listControls.getChildren().add(renameIndex + 2, cancelRenameButton);

        renameTextField.setMaxWidth(Double.MAX_VALUE);

        cancelRenameButton.setMaxWidth(Double.MAX_VALUE);

        //set up to event handlers, one to return the controls back to their original state,
        //and the other to actually perform the rename
        EventHandler<ActionEvent> finishRename = event ->
        {
            listControls.getChildren().removeAll(renameTextField, cancelRenameButton);
            renameButton.setOnAction(oldOnAction);

            listControls.getChildren().iterator().forEachRemaining(n -> n.setDisable(false));

            objectPoseListView.refresh();

            settings.setDisable(false);
        };

        EventHandler<ActionEvent> doRename = event ->
        {
            String newName = renameTextField.getText();
            if (newName != null && !newName.isEmpty())
            {
                getObjectPoseSelectionModel().getSelectedItem().setName(newName);
            }

            finishRename.handle(event);
        };

        //set the on actions
        renameButton.setOnAction(doRename);
        cancelRenameButton.setOnAction(finishRename);
        renameTextField.setOnAction(doRename);

        renameTextField.setText(getObjectPoseSelectionModel().getSelectedItem().getName());
        renameTextField.requestFocus();
        renameTextField.selectAll();
    }

    @FXML
    private void moveUPButton()
    {
        int i = getObjectPoseSelectionModel().getSelectedIndex();
        if (i > 1)
        {
            Collections.swap(JavaFXModelAccess.getInstance().getSceneModel().getCameraList(), i, i - 1);
            getObjectPoseSelectionModel().select(i - 1);
        }
    }

    @FXML
    void moveDOWNButton()
    {
        int i = getObjectPoseSelectionModel().getSelectedIndex();
        List<ObjectPoseSetting> objectPoseList = JavaFXModelAccess.getInstance().getSceneModel().getObjectPoseList();
        if (i != 0 && i < objectPoseList.size() - 1)
        {
            Collections.swap(objectPoseList, i, i + 1);
            getObjectPoseSelectionModel().select(i + 1);
        }
    }

    @FXML
    void lockPoseButton()
    {
        Boolean newValue = !getObjectPoseSelectionModel().getSelectedItem().isLocked();
        getObjectPoseSelectionModel().getSelectedItem().setLocked(newValue);
        settingsController.setDisabled(newValue);
        objectPoseListView.refresh();
    }

    @FXML
    void keyframePoseButton()
    {
        //TODO
        System.out.println("TODO: keyframe added for " + getObjectPoseSelectionModel().getSelectedItem());
    }

    @FXML
    void deletePoseButton()
    {
        int i = getObjectPoseSelectionModel().getSelectedIndex();
        if (i != 0)
        {
            JavaFXModelAccess.getInstance().getSceneModel().getObjectPoseList().remove(i);
        }
    }
}