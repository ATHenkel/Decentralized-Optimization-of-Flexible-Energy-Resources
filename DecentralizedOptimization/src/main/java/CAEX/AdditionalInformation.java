package CAEX;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class AdditionalInformation {

    @XmlElement(name = "Document")
    Document document;

    @XmlAttribute(name = "DocumentVersions")
    String documentVersions;

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public String getDocumentVersions() {
        return documentVersions;
    }

    public void setDocumentVersions(String documentVersions) {
        this.documentVersions = documentVersions;
    }
}
