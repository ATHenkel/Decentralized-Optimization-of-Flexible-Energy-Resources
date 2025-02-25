
package CAEX;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class InterfaceClass {

	@XmlAttribute(name = "Name")
	String Name;

	@XmlAttribute(name = "RefBaseClassPath")
	String RefBaseClassPath;

	@XmlAttribute(name = "text")
	String text;

	@XmlElement(name = "InterfaceClass", namespace ="http://www.dke.de/CAEX")
	List<InterfaceClass> InterfaceClass;
	
	@XmlElement(name = "Attribute", namespace ="http://www.dke.de/CAEX")
	List<Attribute> Attribute;

	public List<Attribute> getAttribute() {
		return this.Attribute;
	}

	public void setAttribute(List<Attribute> Attribute) {
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

	public String gettext() {
		return this.text;
	}

	public void settext(String text) {
		this.text = text;
	}

	public List<InterfaceClass> getInterfaceClass() {
		return this.InterfaceClass;
	}

	public void setInterfaceClass(List<InterfaceClass> InterfaceClass) {
		this.InterfaceClass = InterfaceClass;
	}

}
