
package CAEX;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class InterfaceClassLib {

	@XmlAttribute(name = "ChangeMode")
	String ChangeMode;

	@XmlAttribute(name = "Name")
	String Name;

	@XmlAttribute(name = "text")
	String text;

	@XmlAttribute(name = "Description")
	String Description;

	@XmlAttribute(name = "Copyright")
	String Copyright;

	@XmlAttribute(name = "Version")
	int Version;
	
	@XmlElement(name = "InterfaceClass", namespace ="http://www.dke.de/CAEX")
	List<InterfaceClass> InterfaceClass;

	public List<InterfaceClass> getInterfaceClass() {
		return this.InterfaceClass;
	}

	public void setInterfaceClass(List<InterfaceClass> InterfaceClass) {
		this.InterfaceClass = InterfaceClass;
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

	public String getCopyright() {
		return this.Copyright;
	}

	public void setCopyright(String Copyright) {
		this.Copyright = Copyright;
	}

	public String getChangeMode() {
		return this.ChangeMode;
	}

	public void setChangeMode(String ChangeMode) {
		this.ChangeMode = ChangeMode;
	}

}
