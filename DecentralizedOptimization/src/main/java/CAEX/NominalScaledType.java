
package CAEX;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class NominalScaledType {
	
	@XmlAttribute(name = "RequiredValue")
	List<String> RequiredValue;
	
	public List<String> getRequiredValue() {
		return this.RequiredValue;
	}

	public void setRequiredValue(List<String> RequiredValue) {
		this.RequiredValue = RequiredValue;
	}

}
