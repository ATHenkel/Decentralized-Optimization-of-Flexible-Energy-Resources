package CAEX;

import java.util.Date;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class SourceDocumentInformation {
	
	@XmlAttribute(name = "OriginName")
	String originName;
	
	@XmlAttribute(name = "OriginID")
	String originID;
	
	@XmlAttribute(name = "OriginVersion")
	String originVersion;
	
	@XmlAttribute(name = "LastWritingDateTime")
	Date lastWritingDateTime;
	
	@XmlAttribute(name = "OriginProjectID")
	String originProjectID;
	
	@XmlAttribute(name = "OriginProjectTitle")
	String originProjectTitle;
	
	@XmlAttribute(name = "OriginVendor")
	String originVendor;
	
	@XmlAttribute(name = "OriginVendorURL")
	String originVendorURL;

	
	public String getOriginName() {
		return this.originName;
	}

	public void setOriginName(String originName) {
		this.originName = originName;
	}

	public String getOriginID() {
		return this.originID;
	}

	public void setOriginID(String originID) {
		this.originID = originID;
	}

	public String getOriginVersion() {
		return this.originVersion;
	}

	public void setOriginVersion(String originVersion) {
		this.originVersion = originVersion;
	}

	public Date getLastWritingDateTime() {
		return this.lastWritingDateTime;
	}

	public void setLastWritingDateTime(Date lastWritingDateTime) {
		this.lastWritingDateTime = lastWritingDateTime;
	}

	public String getOriginProjectID() {
		return this.originProjectID;
	}

	public void setOriginProjectID(String originProjectID) {
		this.originProjectID = originProjectID;
	}

	public String getOriginProjectTitle() {
		return this.originProjectTitle;
	}

	public void setOriginProjectTitle(String originProjectTitle) {
		this.originProjectTitle = originProjectTitle;
	}

	public String getOriginVendor() {
		return this.originVendor;
	}

	public void setOriginVendor(String originVendor) {
		this.originVendor = originVendor;
	}

	public String getOriginVendorURL() {
		return this.originVendorURL;
	}

	public void setOriginVendorURL(String originVendorURL) {
		this.originVendorURL = originVendorURL;
	}

}
