
package CAEX;

import java.util.Date;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class RoleClassLib {
	
	@XmlAttribute(name = "Description")
	String Description;
	
	@XmlAttribute(name = "Version")
	String Version;
	
	@XmlAttribute(name = "Name")
	String Name;
	
	@XmlAttribute(name = "text")
	String text;
	
	@XmlElement(name = "RoleClass", namespace ="http://www.dke.de/CAEX")
	RoleClass RoleClass;
	
	
	public String getDescription() {
		return this.Description;
	}

	public void setDescription(String Description) {
		this.Description = Description;
	}

	public String getVersion() {
		return this.Version;
	}

	public void setVersion(String Version) {
		this.Version = Version;
	}

	public RoleClass getRoleClass() {
		return this.RoleClass;
	}

	public void setRoleClass(RoleClass RoleClass) {
		this.RoleClass = RoleClass;
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

}
