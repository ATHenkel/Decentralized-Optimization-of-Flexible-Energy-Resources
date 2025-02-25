
package CAEX;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class SystemUnitClassLib {
	
	@XmlAttribute(name = "text")
	String text;
	
	@XmlAttribute(name = "Description")
	String Description;
	
	@XmlAttribute(name = "Name")
	String Name;
	
	@XmlAttribute(name = "Version")
	int Version;
	
	@XmlElement(name = "SystemUnitClass", namespace ="http://www.dke.de/CAEX")
	List<SystemUnitClass> SystemUnitClass;
	
	public String getDescription() {
		return this.Description;
	}

	public void setDescription(String Description) {
		this.Description = Description;
	}


	public int getVersion() {
		return this.Version;
	}

	public void setVersion(int Version) {
		this.Version = Version;
	}

	public List<SystemUnitClass> getSystemUnitClass() {
		return this.SystemUnitClass;
	}

	public void setSystemUnitClass(List<SystemUnitClass> SystemUnitClass) {
		this.SystemUnitClass = SystemUnitClass;
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
