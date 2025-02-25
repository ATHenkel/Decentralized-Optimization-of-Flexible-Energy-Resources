
package CAEX;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class InternalElement {
	
	@XmlAttribute(name = "Name")
	String Name;
	
	@XmlAttribute(name = "ID")
	String ID;

	@XmlAttribute(name = "RefBaseSystemUnitPath")
	String RefBaseSystemUnitPath;
	
	@XmlAttribute(name = "text")
	String text;
	
	@XmlAttribute(name = "Description")
	String Description;
	
	@XmlElement(name = "ExternalInterface", namespace = "http://www.dke.de/CAEX")
	List<ExternalInterface> ExternalInterface;
	
	@XmlElement(name = "InternalElement", namespace = "http://www.dke.de/CAEX")
	List<InternalElement> InternalElement;
	
	@XmlElement(name = "Attribute", namespace = "http://www.dke.de/CAEX")
	List<Attribute> Attribute;
	
	@XmlElement(name = "RoleRequirements", namespace = "http://www.dke.de/CAEX")
	RoleRequirements RoleRequirements;
	
	public List<Attribute> getAttribute() {
		return this.Attribute;
	}

	public void setAttribute(List<Attribute> Attribute) {
		this.Attribute = Attribute;
	}

	public RoleRequirements getRoleRequirements() {
		return this.RoleRequirements;
	}

	public void setRoleRequirements(RoleRequirements RoleRequirements) {
		this.RoleRequirements = RoleRequirements;
	}

	public String getName() {
		return this.Name;
	}

	public void setName(String Name) {
		this.Name = Name;
	}

	public String getID() {
		return this.ID;
	}

	public void setID(String ID) {
		this.ID = ID;
	}

	public String getRefBaseSystemUnitPath() {
		return this.RefBaseSystemUnitPath;
	}

	public void setRefBaseSystemUnitPath(String RefBaseSystemUnitPath) {
		this.RefBaseSystemUnitPath = RefBaseSystemUnitPath;
	}

	public String gettext() {
		return this.text;
	}

	public void settext(String text) {
		this.text = text;
	}

	public String getDescription() {
		return this.Description;
	}

	public void setDescription(String Description) {
		this.Description = Description;
	}

	public List<InternalElement> getInternalElement() {
		return this.InternalElement;
	}

	public void setInternalElement(List<InternalElement> InternalElement) {
		this.InternalElement = InternalElement;
	}


	public List<ExternalInterface> getExternalInterface() {
		return this.ExternalInterface;
	}

	public void setExternalInterface(List<ExternalInterface> ExternalInterface) {
		this.ExternalInterface = ExternalInterface;
	}

}
