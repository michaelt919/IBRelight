package tetzlaff.ibr.javafx.controllers.scene.lights;//Created by alexk on 7/16/2017.

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;

import com.sun.javafx.scene.control.skin.TableHeaderRow;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import tetzlaff.ibr.javafx.models.JavaFXLightingModel;
import tetzlaff.ibr.javafx.models.JavaFXModelAccess;

public class RootLightSceneController implements Initializable
{
    @FXML private VBox settings;
    @FXML private SettingsLightSceneController settingsController;
    @FXML private TableView<LightGroupSetting> tableView;
    @FXML private VBox groupControls;
    @FXML private VBox lightControls;
    @FXML private Button renameButton;

    private final Property<LightInstanceSetting> selectedLight = new SimpleObjectProperty<>();
    private int lastSelectedIndex = -1;

    @SuppressWarnings("rawtypes")
    @Override
    public void initialize(URL location, ResourceBundle resources)
    {
        //TABLE SET UP
        //columns
        TableColumn<LightGroupSetting, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(param ->
        {
            if (param.getValue().isLocked())
            {
                return new SimpleStringProperty("(L) " + param.getValue().getName());
            }
            else
            {
                return new SimpleStringProperty( param.getValue().getName());
            }
        });
        nameCol.setPrefWidth(100);
        tableView.getColumns().add(nameCol);

        for (int i = 0; i < LightGroupSetting.LIGHT_LIMIT; i++)
        {
            Integer tempFinalInt = i;

            TableColumn<LightGroupSetting, LightInstanceSetting> newCol = new TableColumn<>(String.valueOf(tempFinalInt + 1));

            newCol.setCellValueFactory(param -> param.getValue().lightListProperty().valueAt(tempFinalInt));

            newCol.setPrefWidth(20);

            tableView.getColumns().add(newCol);
        }

        tableView.getSelectionModel().setCellSelectionEnabled(true);

        //light selection listener
        //noinspection rawtypes
        tableView.getSelectionModel().getSelectedCells().addListener((ListChangeListener<TablePosition>) c ->
        {
            while (c.next())
            {
                if (c.wasAdded())
                {
                    //new cell selected
                    assert c.getAddedSize() == 1;
                    TablePosition<?, ?> tb = c.getAddedSubList().get(0);
                    ObservableValue<?> selected = tb.getTableColumn().getCellObservableValue(tb.getRow());
                    if (selected != null && selected.getValue() instanceof LightInstanceSetting)
                    {
                        selectedLight.setValue((LightInstanceSetting) selected.getValue());
                        lastSelectedIndex = tb.getColumn() - 1;
                    }
                    else
                    {
                        selectedLight.setValue(null);
                        lastSelectedIndex = -1;
                    }
                }
            }
        });

        //preventing reordering or rearranging
        tableView.skinProperty().addListener((obs, oldS, newS) ->
        {
            TableHeaderRow tableHeaderRow = (TableHeaderRow) tableView.lookup("TableHeaderRow");
            tableHeaderRow.reorderingProperty().addListener((p, o, n) ->
                tableHeaderRow.setReordering(false));
        });

        tableView.setSortPolicy(param -> false);

        tableView.setColumnResizePolicy(param -> false);

        tableView.setItems(JavaFXModelAccess.getInstance().getSceneModel().getLightGroupList());

        //TABLE SET UP DONE

        //lightGroupList.add(new LightGroupSetting("Free Lights"));

        selectedLight.addListener(settingsController.changeListener);
    }

    public void init(JavaFXLightingModel lightingModel)
    {
        ObservableValue<LightGroupSetting> observableValue = tableView.getSelectionModel().selectedItemProperty();
        lightingModel.setLightGroupSettingObservableValue(observableValue );

        // Setup an initial light group with a single light source.
        // TODO don't do this if a default environment map is available.
        newGroup();
        newLight();
    }

    @FXML
    private void newGroup()
    {
        //for now we will create a blank group
        //in the future we may want to duplicate the previous group instead
        LightGroupSetting newGroup = new LightGroupSetting("New Group");
        List<LightGroupSetting> lightGroupList = JavaFXModelAccess.getInstance().getSceneModel().getLightGroupList();
        lightGroupList.add(newGroup);
        tableView.getSelectionModel().select(lightGroupList.size() - 1, tableView.getColumns().get(0));
    }

    @FXML
    private void saveGroup()
    {
        System.out.println("TODO saveGroup");//TODO
    }

    @FXML
    private void renameGroup()
    {

        EventHandler<ActionEvent> oldOnAction = renameButton.getOnAction();//backup the old on action event for the rename button

        //disable all
        groupControls.getChildren().iterator().forEachRemaining(node -> node.setDisable(true));
        lightControls.getChildren().iterator().forEachRemaining(node -> node.setDisable(true));
        settings.setDisable(true);

        renameButton.setDisable(false);

        int renameIndex = groupControls.getChildren().indexOf(renameButton);

        TextField renameTextField = new TextField();

        groupControls.getChildren().add(renameIndex + 1, renameTextField);

        Button cancelRenameButton = new Button("Cancel");

        groupControls.getChildren().add(renameIndex + 2, cancelRenameButton);

        renameTextField.setMaxWidth(Double.MAX_VALUE);

        cancelRenameButton.setMaxWidth(Double.MAX_VALUE);

        //set up to event handlers, one to return the controls back to their original state,
        //and the other to actually perform the rename
        EventHandler<ActionEvent> finishRename = event ->
        {
            groupControls.getChildren().removeAll(renameTextField, cancelRenameButton);
            renameButton.setOnAction(oldOnAction);

            groupControls.getChildren().iterator().forEachRemaining(n -> n.setDisable(false));

            lightControls.getChildren().iterator().forEachRemaining(n -> n.setDisable(false));

//                tableView.refresh();

            settings.setDisable(false);

            tableView.refresh();
        };

        EventHandler<ActionEvent> doRename = event ->
        {
            String newName = renameTextField.getText();
            if (!newName.isEmpty())
            {
                tableView.getSelectionModel().getSelectedItem().setName(newName);
            }

            finishRename.handle(event);
        };

        //set the on actions
        renameButton.setOnAction(doRename);
        cancelRenameButton.setOnAction(finishRename);
        renameTextField.setOnAction(doRename);

        renameTextField.setText(tableView.getSelectionModel().getSelectedItem().getName());
        renameTextField.requestFocus();
        renameTextField.selectAll();
    }

    @FXML
    private void moveUPGroup()
    {
        if (getRow() > 0)
        {
            Collections.swap(JavaFXModelAccess.getInstance().getSceneModel().getLightGroupList(), getRow(), getRow() - 1);
        }
    }

    @FXML
    private void moveDOWNGroup()
    {
        List<LightGroupSetting> lightGroupList = JavaFXModelAccess.getInstance().getSceneModel().getLightGroupList();
        if (getRow() < lightGroupList.size() - 1)
        {
            Collections.swap(lightGroupList, getRow(), getRow() + 1);
        }
    }

    @FXML
    private void lockGroup()
    {
        if (getSelected() != null)
        {
            boolean n = !getSelected().isLocked();
            getSelected().setLocked(n);
            if (selectedLight.getValue() != null)
            {
                settingsController.setDisabled(n || selectedLight.getValue().isLocked());
            }
        }

        tableView.refresh();
    }

    @FXML
    private void keyframeGroup()
    {
        System.out.println("TODO keyframeGroup");//TODO
    }

    @FXML
    private void deleteGroup()
    {
        List<LightGroupSetting> lightGroupList = JavaFXModelAccess.getInstance().getSceneModel().getLightGroupList();
        if (lightGroupList.size() > getRow() && getRow() >= 0)
        {
            lightGroupList.remove(getRow());
        }
    }

    @FXML
    private void newLight()
    {
        if (getSelected() != null)
        {
            getSelected().addLight(lastSelectedIndex);
        }
        tableView.refresh();
        tableView.getSelectionModel().selectRightCell();
    }

    @FXML
    private void saveLight()
    {
        System.out.println("TODO saveLight");//TODO
    }

    @FXML
    private void lockLight()
    {
        if (selectedLight.getValue() != null)
        {
            boolean newValue = !selectedLight.getValue().isLocked();
            selectedLight.getValue().setLocked(newValue);
            settingsController.setDisabled(newValue || selectedLight.getValue().isGroupLocked());
            if (newValue)
            {
                selectedLight.getValue().setName("(X)");
            }
            else
            {
                selectedLight.getValue().setName("X");
            }
            tableView.refresh();
        }
    }

    @FXML
    private void deleteLight()
    {
        if (getSelected() != null)
        {
            getSelected().removeLight(lastSelectedIndex);
            tableView.refresh();
            tableView.getSelectionModel().selectPrevious();
        }
    }

    private int getRow()
    {
        return tableView.getSelectionModel().getSelectedIndex();
    }

    private LightGroupSetting getSelected()
    {
        return tableView.getSelectionModel().getSelectedItem();
    }
}
