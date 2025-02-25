
package CAEX;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class SystemUnitClass {
	
    @XmlAttribute(name = "Description")
	String Description;
	
	@XmlAttribute(name = "Name")
	String Name;

	@XmlAttribute(name = "text")
	String text;
	
    @XmlElement(name = "Attribute", namespace ="http://www.dke.de/CAEX")
	List<Attribute> Attribute;
	
    @XmlElement(name = "SupportedRoleClass", namespace ="http://www.dke.de/CAEX")
	SupportedRoleClass SupportedRoleClass;
	
	public String getDescription() {
		return this.Description;
	}

	public void setDescription(String Description) {
		this.Description = Description;
	}

	public List<Attribute> getAttribute() {
		return this.Attribute;
	}

	public void setAttribute(List<Attribute> Attribute) {
		this.Attribute = Attribute;
	}

	public SupportedRoleClass getSupportedRoleClass() {
		return this.SupportedRoleClass;
	}

	public void setSupportedRoleClass(SupportedRoleClass SupportedRoleClass) {
		this.SupportedRoleClass = SupportedRoleClass;
	}

	public String getName() {
		return this.Name;
	}

	public void setName(String Name) {
		this.Name = Name;
	}

	public String gettext() {
		return this.text;
	}

	public void settext(String text) {
		this.text = text;
	}

	public List<SystemUnitClass> getSystemUnitClass() {
		return this.SystemUnitClass;
	}

	public void setSystemUnitClass(List<SystemUnitClass> SystemUnitClass) {
		this.SystemUnitClass = SystemUnitClass;
	}

	List<SystemUnitClass> SystemUnitClass;

	public String getRefBaseClassPath() {
		return this.RefBaseClassPath;
	}

	public void setRefBaseClassPath(String RefBaseClassPath) {
		this.RefBaseClassPath = RefBaseClassPath;
	}

	String RefBaseClassPath;

	public String getID() {
		return this.ID;
	}

	public void setID(String ID) {
		this.ID = ID;
	}

	String ID;

	public ExternalInterface getExternalInterface() {
		return this.ExternalInterface;
	}

	public void setExternalInterface(ExternalInterface ExternalInterface) {
		this.ExternalInterface = ExternalInterface;
	}

	ExternalInterface ExternalInterface;

	public List<InternalElement> getInternalElement() {
		return this.InternalElement;
	}

	public void setInternalElement(List<InternalElement> InternalElement) {
		this.InternalElement = InternalElement;
	}

	List<InternalElement> InternalElement;
}