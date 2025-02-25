
package CAEX;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class SupportedRoleClass {
	
	@XmlAttribute(name = "RefRoleClassPath")
	String RefRoleClassPath;
	
	public String getRefRoleClassPath() {
		return this.RefRoleClassPath;
	}

	public void setRefRoleClassPath(String RefRoleClassPath) {
		this.RefRoleClassPath = RefRoleClassPath;
	}

}