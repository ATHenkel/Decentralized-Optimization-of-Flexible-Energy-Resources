
package CAEX;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class RoleClass {
	
	@XmlAttribute(name = "Name")
	String Name;
	
	@XmlAttribute(name = "RefBaseClassPath")
	String RefBaseClassPath;

	@XmlAttribute(name = "ChangeMode")
	String ChangeMode;
	
	@XmlAttribute(name = "Description")
	String Description;
	
	@XmlAttribute(name = "text")	
	String text; 
	
	@XmlElement(name = "Attribute", namespace ="http://www.dke.de/CAEX")
	Attribute Attribute;
	
	@XmlElement(name = "RoleClass", namespace ="http://www.dke.de/CAEX")
	List<RoleClass> RoleClass;
	
	
	public Attribute getAttribute() {
		return this.Attribute;
	}

	public void setAttribute(Attribute Attribute) {
		this.Attribute = Attribute;
	}

	public String getName() {
		return this.Name;
	}

	public void setName(String Name) {
		this.Name = Name;
	}

	public String getRefBaseClassPath() {
		return this.RefBaseClassPath;
	}

	public void setRefBaseClassPath(String RefBaseClassPath) {
		this.RefBaseClassPath = RefBaseClassPath;
	}

	public List<RoleClass> getRoleClass() {
		return this.RoleClass;
	}

	public void setRoleClass(List<RoleClass> RoleClass) {
		this.RoleClass = RoleClass;
	}

	public String getChangeMode() {
		return this.ChangeMode;
	}

	public void setChangeMode(String ChangeMode) {
		this.ChangeMode = ChangeMode;
	}

	public String getDescription() {
		return this.Description;
	}

	public void setDescription(String Description) {
		this.Description = Description;
	}

	public String gettext() {
		return this.text;
	}

	public void settext(String text) {
		this.text = text;
	}

}
