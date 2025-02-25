package CAEX;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class AttributeTypeLib {
	
	@XmlAttribute(name = "Description")
	String Description;
	
	@XmlAttribute(name = "Version")
	String Version;
	
	@XmlAttribute(name = "Copyright")
	String Copyright;
	
	@XmlElement(name = "AttributeType", namespace ="http://www.dke.de/CAEX")
	List<AttributeType> AttributeType;
	
	@XmlAttribute(name = "ChangeMode")
	String ChangeMode;
	
	@XmlAttribute(name = "Name")
	String Name;
	
	@XmlAttribute(name = "text")
	String text;
	
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


	public String getCopyright() {
		return this.Copyright;
	}

	public void setCopyright(String Copyright) {
		this.Copyright = Copyright;
	}

	public List<AttributeType> getAttributeType() {
		return this.AttributeType;
	}

	public void setAttributeType(List<AttributeType> AttributeType) {
		this.AttributeType = AttributeType;
	}

	public String getName() {
		return this.Name;
	}

	public void setName(String Name) {
		this.Name = Name;
	}

	public String getChangeMode() {
		return this.ChangeMode;
	}

	public void setChangeMode(String ChangeMode) {
		this.ChangeMode = ChangeMode;
	}

	public String gettext() {
		return this.text;
	}

	public void settext(String text) {
		this.text = text;
	}
}
