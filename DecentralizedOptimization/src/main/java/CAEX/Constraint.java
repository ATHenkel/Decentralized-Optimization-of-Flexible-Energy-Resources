
package CAEX;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class Constraint {

	@XmlElement(name = "NominalScaledType", namespace ="http://www.dke.de/CAEX")
	NominalScaledType NominalScaledType;

	@XmlAttribute(name = "Name")
	String Name;

	@XmlAttribute(name = "text")
	String text;

	public NominalScaledType getNominalScaledType() {
		return this.NominalScaledType;
	}

	public void setNominalScaledType(NominalScaledType NominalScaledType) {
		this.NominalScaledType = NominalScaledType;
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
