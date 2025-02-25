package CAEX;

import javax.xml.bind.annotation.*;


@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CAEXObject")
@XmlSeeAlso({
	CAEX.AdditionalInformation.class,
	CAEX.Attribute.class,
	CAEX.Document.class
})
public class CAEXBasicObject {

    @XmlAttribute(name = "ID")
    private String id;

    @XmlAttribute(name = "Name")
    private String name;

    @XmlElement(name = "Description")
    private String description;

    // Konstruktor ohne Argumente für JAXB
    public CAEXBasicObject() {
    }

    // Getter und Setter
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
