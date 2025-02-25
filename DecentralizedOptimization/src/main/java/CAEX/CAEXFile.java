package CAEX;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.*;


@XmlRootElement(name = "CAEXFile", namespace = "http://www.dke.de/CAEX")
@XmlAccessorType(XmlAccessType.FIELD)
public class CAEXFile extends CAEXBasicObject {

	@XmlElement(name = "AdditionalInformation", namespace = "http://www.dke.de/CAEX")
	List<AdditionalInformation> additionalInformation;

    @XmlElement(name = "SuperiorStandardVersion", namespace = "http://www.dke.de/CAEX")
    String superiorStandardVersion;

    @XmlElement(name = "SourceDocumentInformation", namespace = "http://www.dke.de/CAEX")
    SourceDocumentInformation sourceDocumentInformation;

    @XmlElement(name = "InstanceHierarchy", namespace = "http://www.dke.de/CAEX")
    List<InstanceHierarchy> instanceHierarchy;

    @XmlElement(name = "InterfaceClassLib", namespace = "http://www.dke.de/CAEX")
    List<InterfaceClassLib> interfaceClassLib;

    @XmlElement(name = "RoleClassLib", namespace = "http://www.dke.de/CAEX")
    List<RoleClassLib> roleClassLib;

    @XmlElement(name = "SystemUnitClassLib", namespace = "http://www.dke.de/CAEX")
    List<SystemUnitClassLib> systemUnitClassLib;

    @XmlElement(name = "AttributeTypeLib", namespace = "http://www.dke.de/CAEX")
    List<AttributeTypeLib> attributeTypeLib;

    @XmlAttribute(name = "SchemaVersion", namespace = "http://www.dke.de/CAEX")
    double schemaVersion;

    @XmlAttribute(name = "FileName")
    String fileName;

    @XmlAttribute(name = "xsi")
    String xsi;

    @XmlAttribute(name = "xmlns")
    String xmlns;

    @XmlAttribute(name = "schemaLocation")
    String schemaLocation;

    @XmlAttribute(name = "text")
    String text;

    // Getter and Setter Methods
    
    public List<AdditionalInformation> getAdditionalInformation() {
    	if (additionalInformation == null) {
    		additionalInformation = new ArrayList<AdditionalInformation>();
		}
        return additionalInformation;
    }

    public void setAdditionalInformation(List<AdditionalInformation> additionalInformation) {
        this.additionalInformation = additionalInformation;
    }

    public String getSuperiorStandardVersion() {
        return superiorStandardVersion;
    }

    public void setSuperiorStandardVersion(String superiorStandardVersion) {
        this.superiorStandardVersion = superiorStandardVersion;
    }

    public SourceDocumentInformation getSourceDocumentInformation() {
        return sourceDocumentInformation;
    }

    public void setSourceDocumentInformation(SourceDocumentInformation sourceDocumentInformation) {
        this.sourceDocumentInformation = sourceDocumentInformation;
    }

    public List<InstanceHierarchy> getInstanceHierarchy() {
        return instanceHierarchy;
    }

    public void setInstanceHierarchy(List<InstanceHierarchy> instanceHierarchy) {
        this.instanceHierarchy = instanceHierarchy;
    }

    public List<InterfaceClassLib> getInterfaceClassLib() {
        return interfaceClassLib;
    }

    public void setInterfaceClassLib(List<InterfaceClassLib> interfaceClassLib) {
        this.interfaceClassLib = interfaceClassLib;
    }

    public List<RoleClassLib> getRoleClassLib() {
        return roleClassLib;
    }

    public void setRoleClassLib(List<RoleClassLib> roleClassLib) {
        this.roleClassLib = roleClassLib;
    }

    public List<SystemUnitClassLib> getSystemUnitClassLib() {
        return systemUnitClassLib;
    }

    public void setSystemUnitClassLib(List<SystemUnitClassLib> systemUnitClassLib) {
        this.systemUnitClassLib = systemUnitClassLib;
    }

    public List<AttributeTypeLib> getAttributeTypeLib() {
        return attributeTypeLib;
    }

    public void setAttributeTypeLib(List<AttributeTypeLib> attributeTypeLib) {
        this.attributeTypeLib = attributeTypeLib;
    }

    public double getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(double schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getxsi() {
        return xsi;
    }

    public void setxsi(String xsi) {
        this.xsi = xsi;
    }

    public String getxmlns() {
        return xmlns;
    }

    public void setxmlns(String xmlns) {
        this.xmlns = xmlns;
    }

    public String getschemaLocation() {
        return schemaLocation;
    }

    public void setschemaLocation(String schemaLocation) {
        this.schemaLocation = schemaLocation;
    }

    public String gettext() {
        return text;
    }

    public void settext(String text) {
        this.text = text;
    }
}
