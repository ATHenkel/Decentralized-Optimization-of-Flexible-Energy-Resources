package CAEX;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class Attribute {

	@XmlAttribute(name = "Name")
	String Name;

	@XmlAttribute(name = "text") 
	String text;

	@XmlAttribute(name = "Description")
	String Description;

	@XmlAttribute(name = "RefAttributeType")
	String RefAttributeType;

	@XmlAttribute(name = "AttributeDataType")
	String AttributeDataType;

	@XmlAttribute(name = "Unit")
	String Unit;

	@XmlElement(name = "Constraint", namespace ="http://www.dke.de/CAEX")
	Constraint Constraint;
	
	@XmlElement(name = "Attribute", namespace ="http://www.dke.de/CAEX")
	List<Attribute> Attribute;
	
	@XmlElement(name = "Value", namespace ="http://www.dke.de/CAEX")
	String Value;
	
	@XmlElement(name = "DefaultValue", namespace ="http://www.dke.de/CAEX")
	String DefaultValue;
	
	public String getValue() {
		return this.Value;
	}

	public void setValue(String Value) {
		this.Value = Value;
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

	public String gettext() {
		return this.text;
	}

	public void settext(String text) {
		this.text = text;
	}

	public String getRefAttributeType() {
		return this.RefAttributeType;
	}

	public void setRefAttributeType(String RefAttributeType) {
		this.RefAttributeType = RefAttributeType;
	}

	public String getDefaultValue() {
		return this.DefaultValue;
	}

	public void setDefaultValue(String DefaultValue) {
		this.DefaultValue = DefaultValue;
	}

	public String getDescription() {
		return this.Description;
	}

	public void setDescription(String Description) {
		this.Description = Description;
	}

	public Constraint getConstraint() {
		return this.Constraint;
	}

	public void setConstraint(Constraint Constraint) {
		this.Constraint = Constraint;
	}

	public List<Attribute> getAttribute() {
		return this.Attribute;
	}

	public void setAttribute(List<Attribute> Attribute) {
		this.Attribute = Attribute;
	}

	public String getUnit() {
		return this.Unit;
	}

	public void setUnit(String Unit) {
		this.Unit = Unit;
	}

}
