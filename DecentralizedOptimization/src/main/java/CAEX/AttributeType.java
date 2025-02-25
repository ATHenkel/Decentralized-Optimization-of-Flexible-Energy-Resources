
package CAEX;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class AttributeType {
	
	@XmlAttribute(name = "DefaultValue")
	String DefaultValue;
	
	@XmlAttribute(name = "AttributeDataType")
	String AttributeDataType;
	
	@XmlAttribute(name = "Description")
	String Description;
	
	@XmlAttribute(name = "RefAttributeType")
	String RefAttributeType;
	
	@XmlAttribute(name = "Name")
	String Name;
	
	@XmlAttribute(name = "text")
	String text;
	
	@XmlElement(name = "AttributeType", namespace = "http://www.dke.de/CAEX")
	List<AttributeType> AttributeType;
	
	@XmlElement(name = "Attribute", namespace = "http://www.dke.de/CAEX")
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

	public String getAttributeDataType() {
		return this.AttributeDataType;
	}

	public void setAttributeDataType(String AttributeDataType) {
		this.AttributeDataType = AttributeDataType;
	}

	public String getDefaultValue() {
		return this.DefaultValue;
	}

	public void setDefaultValue(String DefaultValue) {
		this.DefaultValue = DefaultValue;
	}

	public Constraint getConstraint() {
		return this.Constraint;
	}

	public void setConstraint(Constraint Constraint) {
		this.Constraint = Constraint;
	}

	Constraint Constraint;

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

	public String getRefAttributeType() {
		return this.RefAttributeType;
	}

	public void setRefAttributeType(String RefAttributeType) {
		this.RefAttributeType = RefAttributeType;
	}

	public List<AttributeType> getAttributeType() {
		return this.AttributeType;
	}

	public void setAttributeType(List<AttributeType> AttributeType) {
		this.AttributeType = AttributeType;
	}

}