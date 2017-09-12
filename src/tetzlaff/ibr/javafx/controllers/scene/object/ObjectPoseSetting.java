package tetzlaff.ibr.javafx.controllers.scene.object;

import javafx.beans.property.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import tetzlaff.ibr.javafx.util.DOMConvertable;
import tetzlaff.ibr.javafx.util.StaticUtilities;

public class ObjectPoseSetting implements DOMConvertable
{
    private final DoubleProperty centerX = new SimpleDoubleProperty();
    private final DoubleProperty centerY = new SimpleDoubleProperty();
    private final DoubleProperty centerZ = new SimpleDoubleProperty();
    private final DoubleProperty rotateY = StaticUtilities.wrap(-180, 180, new SimpleDoubleProperty());
    private final DoubleProperty rotateX = StaticUtilities.bound(-90, 90, new SimpleDoubleProperty());
    private final DoubleProperty rotateZ = StaticUtilities.wrap(-180.0, 180.0, new SimpleDoubleProperty());
    private final BooleanProperty locked = new SimpleBooleanProperty();
    private final StringProperty name = new SimpleStringProperty();

    public ObjectPoseSetting(Double centerX, Double centerY, Double centerZ, Double rotateY, Double rotateX, Double rotateZ, Boolean locked, String name)
    {
        this.centerX.setValue(centerX);
        this.centerY.setValue(centerY);
        this.centerZ.setValue(centerZ);
        this.rotateY.setValue(rotateY);
        this.rotateX.setValue(rotateX);
        this.rotateZ.setValue(rotateZ);
        this.locked.setValue(locked);
        this.name.setValue(name);
    }

    @Override
    public Element toDOMElement(Document document)
    {
        Element element = document.createElement("Camera");
        element.setAttribute("centerX", centerX.getValue().toString());
        element.setAttribute("centerY", centerY.getValue().toString());
        element.setAttribute("centerZ", centerZ.getValue().toString());
        element.setAttribute("rotateY", rotateY.getValue().toString());
        element.setAttribute("rotateX", rotateX.getValue().toString());
        element.setAttribute("rotateZ", rotateZ.getValue().toString());
        element.setAttribute("locked", locked.getValue().toString());
        element.setAttribute("name", name.getValue());
        return element;
    }

    public static ObjectPoseSetting fromDOMElement(Element element)
    {
        return new ObjectPoseSetting(
            Double.valueOf(element.getAttribute("centerX")),
            Double.valueOf(element.getAttribute("centerY")),
            Double.valueOf(element.getAttribute("centerZ")),
            Double.valueOf(element.getAttribute("rotateY")),
            Double.valueOf(element.getAttribute("rotateX")),
            Double.valueOf(element.getAttribute("rotateZ")),
            Boolean.valueOf(element.getAttribute("locked")),
            element.getAttribute("name")
        );
    }

    @Override
    public String toString()
    {
        if (locked.getValue())
        {
            return "(L) " + name.getValue();
        }
        else
        {
            return name.getValue();
        }
    }

    public ObjectPoseSetting duplicate()
    {
        return new ObjectPoseSetting(
            this.centerX.getValue(),
            this.centerY.getValue(),
            this.centerZ.getValue(),
            this.rotateY.getValue(),
            this.rotateX.getValue(),
            this.rotateZ.getValue(),
            this.locked.getValue(),
            this.name.getValue() + " copy"
        );
    }

    public double getCenterX()
    {
        return centerX.get();
    }

    public DoubleProperty centerXProperty()
    {
        return centerX;
    }

    public void setCenterX(double centerX)
    {
        this.centerX.set(centerX);
    }

    public double getCenterY()
    {
        return centerY.get();
    }

    public DoubleProperty centerYProperty()
    {
        return centerY;
    }

    public void setCenterY(double centerY)
    {
        this.centerY.set(centerY);
    }

    public double getCenterZ()
    {
        return centerZ.get();
    }

    public DoubleProperty centerZProperty()
    {
        return centerZ;
    }

    public void setCenterZ(double centerZ)
    {
        this.centerZ.set(centerZ);
    }

    public double getRotateY()
    {
        return rotateY.get();
    }

    public DoubleProperty rotateYProperty()
    {
        return rotateY;
    }

    public void setRotateY(double rotateY)
    {
        this.rotateY.set(rotateY);
    }

    public double getRotateX()
    {
        return rotateX.get();
    }

    public DoubleProperty rotateXProperty()
    {
        return rotateX;
    }

    public void setRotateX(double rotateX)
    {
        this.rotateX.set(rotateX);
    }

    public double getRotateZ()
    {
        return rotateZ.get();
    }

    public DoubleProperty rotateZProperty()
    {
        return rotateZ;
    }

    public void setRotateZ(double rotateZ)
    {
        this.rotateZ.set(rotateZ);
    }

    public boolean isLocked()
    {
        return locked.get();
    }

    public BooleanProperty lockedProperty()
    {
        return locked;
    }

    public void setLocked(boolean locked)
    {
        this.locked.set(locked);
    }

    public String getName()
    {
        return name.get();
    }

    public StringProperty nameProperty()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name.set(name);
    }
}