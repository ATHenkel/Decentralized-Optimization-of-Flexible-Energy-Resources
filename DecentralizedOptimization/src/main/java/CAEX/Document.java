package CAEX;

import java.util.Date;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class Document {

	@XmlAttribute(name = "DocumentIdentifier")
	String DocumentIdentifier;

	@XmlAttribute(name = "Version")
	String Version;

	@XmlAttribute(name = "xmlns")
	String xmlns;

	public String getDocumentIdentifier() {
		return this.DocumentIdentifier;
	}

	public void setDocumentIdentifier(String DocumentIdentifier) {
		this.DocumentIdentifier = DocumentIdentifier;
	}

	public String getVersion() {
		return this.Version;
	}

	public void setVersion(String Version) {
		this.Version = Version;
	}

	public String getxmlns() {
		return this.xmlns;
	}

	public void setxmlns(String xmlns) {
		this.xmlns = xmlns;
	}

}
