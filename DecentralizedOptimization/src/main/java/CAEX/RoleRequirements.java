
package CAEX;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class RoleRequirements {
	
	@XmlAttribute(name = "RefBaseRoleClassPath")
	String RefBaseRoleClassPath;
	
	public String getRefBaseRoleClassPath() {
		return this.RefBaseRoleClassPath;
	}

	public void setRefBaseRoleClassPath(String RefBaseRoleClassPath) {
		this.RefBaseRoleClassPath = RefBaseRoleClassPath;
	}

}
