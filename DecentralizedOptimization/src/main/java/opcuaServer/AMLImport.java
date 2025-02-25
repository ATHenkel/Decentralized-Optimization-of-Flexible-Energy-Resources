package opcuaServer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;


import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import CAEX.*;

public class AMLImport {

	private static final String MODULETYPEPACKAGE = "ModuleTypePackage";
	private static final String MTP_INSTANCE = "Optimization"; //TODO: Here: Set MTP-Instance name
	private static final String COMMUNICATION = "Communication";
	private static final String INSTANCE_LIST = "InstanceList";
	private static final String SOURCE_LIST = "SourceList";
	public static final String OPC_UA_SERVER = "OPC UA Server";

	public static CAEXFile loadFromXmlFile(File file) {
		CAEXFile amlElements = null;
		if (file.exists()) {
			System.out.println("File exists, proceeding with unmarshalling.");
		} else {
			System.err.println("File does not exist: " + file.getAbsolutePath());
			return null;
		}

		FileReader fileReader = null;
		try {
			JAXBContext context = JAXBContext.newInstance(CAEXFile.class);
			Unmarshaller unmarshaller = context.createUnmarshaller();

			fileReader = new FileReader(file);
			amlElements = (CAEXFile) unmarshaller.unmarshal(fileReader);
		} catch (JAXBException ex) {
			System.err.println("JAXB Exception: " + ex.getMessage());
		} catch (FileNotFoundException ex) {
			System.err.println("File not found: " + ex.getMessage());
		} 
		finally {
			if (fileReader != null) {
				try {
					fileReader.close();
				} catch (IOException e) {
					System.err.println("Error closing file reader: " + e.getMessage());
				}
			}
		}

		if (amlElements == null) {
			System.out.println("No data loaded, amlElements is null.");
		} else {
			System.out.println("Data loaded successfully.");
		}
		return amlElements;
	}

	/**
	 * Get Source-List from MTP
	 */
	static void getOpcUaNodes(CAEXFile caexFile) {
		if (caexFile != null) {
			for (InstanceHierarchy hierarchy : caexFile.getInstanceHierarchy()) {
				if (hierarchy.getName().equals(MODULETYPEPACKAGE)) {
					for (InternalElement internalElement : hierarchy.getInternalElement()) {
						if (internalElement.getName().equals(MTP_INSTANCE)) {
							for (InternalElement subElement : internalElement.getInternalElement()) {
								if (subElement.getName().equals(COMMUNICATION)) {
									for (InternalElement instanceList : subElement.getInternalElement()) {
										if (instanceList.getName().equals(SOURCE_LIST)) {
											for (InternalElement server : instanceList.getInternalElement()) {
												if (server.getName().equals(OPC_UA_SERVER)) {
													for (ExternalInterface externalInterface : server
															.getExternalInterface()) {
														GlobalStorage.addOpcUaNodes(externalInterface.getID(),
																externalInterface.getName());
													}
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Get InternalElements from Instance List
	 */
	 static void getInternalElements(CAEXFile caexFile) {
		if (caexFile != null) {
			for (InstanceHierarchy hierarchy : caexFile.getInstanceHierarchy()) {
				if (hierarchy.getName().equals(MODULETYPEPACKAGE)) {
					for (InternalElement internalElement : hierarchy.getInternalElement()) {
						if (internalElement.getName().equals(MTP_INSTANCE)) {
							for (InternalElement subElement : internalElement.getInternalElement()) {
								if (subElement.getName().equals(COMMUNICATION)) {
									for (InternalElement instanceList : subElement.getInternalElement()) {
										if (instanceList.getName().equals(INSTANCE_LIST)) {
											for (InternalElement server : instanceList.getInternalElement()) {
												// Speichern jedes InternalElement in der globalen HashMap
												GlobalStorage.addInternalElement(server.getID(), server);
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}

}
