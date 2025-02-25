package CAEX;

import javax.xml.bind.annotation.XmlRegistry;

/**
 * This object contains factory methods for each Java content interface and Java
 * element interface generated in the caexneu package.
 */
@XmlRegistry
public class ObjectFactory {

    /**
     * Create an instance of ObjectFactory that can be used to create new instances
     * of schema derived classes for package: caexneu
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of CAEXFile
     */
    public CAEXFile createCAEXFile() {
        return new CAEXFile();
    }

    /**
     * Create an instance of Document
     */
    public Document createDocument() {
        return new Document();
    }

    /**
     * Create an instance of AdditionalInformation
     */
    public AdditionalInformation createAdditionalInformation() {
        return new AdditionalInformation();
    }

    /**
     * Create an instance of SourceDocumentInformation
     */
    public SourceDocumentInformation createSourceDocumentInformation() {
        return new SourceDocumentInformation();
    }

    /**
     * Create an instance of InternalElement
     */
    public InternalElement createInternalElement() {
        return new InternalElement();
    }

    /**
     * Create an instance of SystemUnitClass
     */
    public SystemUnitClass createSystemUnitClass() {
        return new SystemUnitClass();
    }

    /**
     * Create an instance of InterfaceClassLib
     */
    public InterfaceClassLib createInterfaceClassLib() {
        return new InterfaceClassLib();
    }

    /**
     * Create an instance of Attribute
     */
    public Attribute createAttribute() {
        return new Attribute();
    }
    
}
