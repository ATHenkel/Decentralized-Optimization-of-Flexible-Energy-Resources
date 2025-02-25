package mtpModification;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class AMLSectionGenerator {
	// Method for generating a section for the AML file for #1 - Add to Process
	// Values
	public static String generateAMLSection1(String name, String processValueID) {
		// Generate a unique ID for the element
		String uniqueID = UUID.randomUUID().toString();

		// Build the section
		StringBuilder sectionBuilder = new StringBuilder();
		sectionBuilder.append("<InternalElement Name=\"").append(name).append("\" ");
		sectionBuilder.append("ID=\"").append(uniqueID).append("\" ");
		sectionBuilder.append("RefBaseSystemUnitPath=\"MTPProcessValueSUCLib/ProcessValue/ProcessValueOut\">\n");
		sectionBuilder.append("  <Description>").append(name).append("</Description>\n");
		sectionBuilder.append(
				"  <Attribute Name=\"RefID\" AttributeDataType=\"xs:string\" RefAttributeType=\"MTPATLib/IDReferenceType/RefIDAttributeType\">\n");
		sectionBuilder.append("    <Value>").append(processValueID).append("</Value>\n");
		sectionBuilder.append("  </Attribute>\n");
		sectionBuilder.append(
				"  <RoleRequirements RefBaseRoleClassPath=\"AutomationMLBaseRoleClassLib/AutomationMLBaseRole\" />\n");
		sectionBuilder.append("</InternalElement>");

		// Return the generated section
		return sectionBuilder.toString();
	}

	// Method for generating a section for the AML file for #2 - Add to Service
	public static String generateAMLSection2(String name, String uniqueID, String processValueID) {
		// Build the section
		StringBuilder sectionBuilder = new StringBuilder();
		sectionBuilder.append("<InternalElement Name=\"").append(name).append("\" ");
		sectionBuilder.append("ID=\"").append(uniqueID).append("\" ");
		sectionBuilder.append("RefBaseSystemUnitPath=\"MTPProcessValueSUCLib/ProcessValue/ProcessValueOut\">\n");
		sectionBuilder.append("  <Description>").append(name).append("</Description>\n");
		sectionBuilder.append(
				"  <Attribute Name=\"RefID\" AttributeDataType=\"xs:string\" RefAttributeType=\"MTPATLib/IDReferenceType/RefIDAttributeType\">\n");
		sectionBuilder.append("    <Value>").append(processValueID).append("</Value>\n");
		sectionBuilder.append("  </Attribute>\n");
		sectionBuilder.append(
				"  <RoleRequirements RefBaseRoleClassPath=\"AutomationMLBaseRoleClassLib/AutomationMLBaseRole\" />\n");
		sectionBuilder.append("</InternalElement>");

		// Return the generated section
		return sectionBuilder.toString();
	}

	// Method for generating a section for the AML file for #3 Instance-List
	public static String generateAMLSection3(String name, String processValueID, Map<String, String> uniqueIdentifiers,
			boolean DIntView, boolean AnaView) {
		// Generate a unique ID for the element
		String uniqueID = UUID.randomUUID().toString();

		String parameterType = null;

		if (DIntView) {
			parameterType = "DIntView";
		} else if (AnaView) {
			parameterType = "AnaView";
		}

		// Build the section
		StringBuilder sectionBuilder = new StringBuilder();
		sectionBuilder.append("<InternalElement Name=\"").append(name).append("\" ");
		sectionBuilder.append("ID=\"").append(uniqueID).append("\" ");
		sectionBuilder.append("RefBaseSystemUnitPath=\"MTPDataObjectSUCLib/DataAssembly/IndicatorElement/")
				.append(parameterType).append("\">\n");
		sectionBuilder.append("  <Description>").append(name).append("</Description>\n");

		// Add TagDescription and TagName
		sectionBuilder.append("  <Attribute Name=\"TagName\" AttributeDataType=\"xs:string\">\n");
		sectionBuilder.append("    <DefaultValue>").append(name).append("</DefaultValue>\n");
		sectionBuilder.append("    <Value>").append(name).append("</Value>\n");
		sectionBuilder.append("  </Attribute>\n");
		sectionBuilder.append("  <Attribute Name=\"TagDescription\" AttributeDataType=\"xs:string\">\n");
		sectionBuilder.append("    <DefaultValue>").append(name).append("</DefaultValue>\n");
		sectionBuilder.append("    <Value>").append(name).append("</Value>\n");
		sectionBuilder.append("  </Attribute>\n");

		// Add attributes
		String[] attributeOrder = { "WQC", "V", "VSclMin", "VSclMax", "VUnit", "RefID" };
		for (String attribute : attributeOrder) {
			if (uniqueIdentifiers.containsKey(attribute)) {
				String value = uniqueIdentifiers.get(attribute);
				sectionBuilder.append("  <Attribute Name=\"").append(attribute)
						.append("\" AttributeDataType=\"xs:string\"");
				sectionBuilder.append(" RefAttributeType=\"MTPATLib/IDReferenceType/IDLinkAttributeType\"");

				sectionBuilder.append(">\n");
				if (!attribute.equals("RefID")) {
					sectionBuilder.append("    <DefaultValue>").append(getDefaultValue(attribute))
							.append("</DefaultValue>\n");
				}
				sectionBuilder.append("    <Value>").append(value).append("</Value>\n");
				sectionBuilder.append("  </Attribute>\n");
			}
		}

		sectionBuilder.append(
				"  <RoleRequirements RefBaseRoleClassPath=\"AutomationMLBaseRoleClassLib/AutomationMLBaseRole\" />\n");
		sectionBuilder.append("</InternalElement>");

		// Return the generated section
		return sectionBuilder.toString();
	}

	// Method for generating a section for the AML file for #4
	public static String generateAMLSection4(String name, Map<String, String> uniqueIdentifiers, boolean DIntView,
			boolean AnaView) {
		// Build the section
		StringBuilder sectionBuilder = new StringBuilder();

		// Define the desired order of attributes
		String[] attributeOrder = { "VUnit", "VSclMax", "VSclMin", "V", "WQC" };

		// Iterate over the desired order of attributes
		for (String attribute : attributeOrder) {
			// Check if the current identifier is present
			if (uniqueIdentifiers.containsKey(attribute)) {
				// Extract the ID of the current identifier
				String id = uniqueIdentifiers.get(attribute);

				String parameterType = null;
				// Label is the name parameter

				if (DIntView == true) {
					parameterType = "DIntView";
				} else if (AnaView == true) {
					parameterType = "AnaView";
				}

				// Identifier name corresponds to the current attribute
				String identifierName = attribute;

				// Add the structure for the current identifier
				sectionBuilder.append("<ExternalInterface Name=\"").append(parameterType).append("_").append(name)
						.append(".").append(identifierName).append("\" ");
				sectionBuilder.append("ID=\"").append(id).append("\" ");
				sectionBuilder.append("RefBaseClassPath=\"MTPCommunicationICLib/DataItem/OPCUAItem\">\n");

				// Add attributes for the current identifier
				sectionBuilder.append(
						"  <Attribute Name=\"Identifier\" AttributeDataType=\"xs:string\" RefAttributeType=\"MTPCommunicationATLib/OPCUABaseNodeIDType/OPCUAStringNodeIDType\">\n");
				sectionBuilder.append("    <Value>").append(parameterType).append("_").append(name).append(".")
						.append(identifierName).append("</Value>\n");
				sectionBuilder.append("  </Attribute>\n");
				sectionBuilder.append("  <Attribute Name=\"Namespace\" AttributeDataType=\"xs:string\">\n");
				sectionBuilder.append("    <Value>http://hsu-hh</Value>\n");
				sectionBuilder.append("  </Attribute>\n");
				sectionBuilder.append("  <Attribute Name=\"Access\" AttributeDataType=\"xs:unsignedByte\">\n");
				sectionBuilder.append("    <Value>1</Value>\n");
				sectionBuilder.append("  </Attribute>\n");
				sectionBuilder.append("</ExternalInterface>\n");
			}
		}

		// Return the generated section
		return sectionBuilder.toString();
	}

	// Method for generating a random UUID for the ProcessValueID
	public static String generateRandomUUID() {
		return UUID.randomUUID().toString();
	}

	// Method for returning the DefaultValue for a specific attribute
	private static String getDefaultValue(String attribute) {
		switch (attribute) {
		case "WQC":
			return "255";
		case "V":
			return "1";
		case "VSclMin":
			return "0";
		case "VSclMax":
			return "100";
		case "VUnit":
			return "1998";
		default:
			return "";
		}
	}

	// Method for reading an AML file and outputting the sections between the
	// markers
	public static void addSetpointToAML(String filePath, String SetpointName, boolean DIntView, boolean AnaView) {
		try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
			StringBuilder fileContent = new StringBuilder();
			String line;
			boolean foundSection1 = false;
			boolean foundSection2 = false;
			boolean foundSection3 = false;
			boolean foundSection4 = false;
			String processValueID = generateRandomUUID();
			Map<String, String> uniqueIdentifiers = new HashMap<>();

			while ((line = reader.readLine()) != null) {
				fileContent.append(line).append("\n");
				if (!foundSection1 && line.contains("<!-- #1 -->")) {
					// Add the generated section for #1 below the found line
					String generatedSection = generateAMLSection1(SetpointName, processValueID);
					fileContent.append(generatedSection).append("\n");
					foundSection1 = true;
				}
				if (!foundSection2 && line.contains("<!-- #2 -->")) {
					// Generate a unique ID for #2
					String uniqueID = UUID.randomUUID().toString();
					// Add the generated section for #2 below the found line
					String generatedSection = generateAMLSection2(SetpointName, uniqueID, processValueID);
					fileContent.append(generatedSection).append("\n");
					foundSection2 = true;
				}
				if (!foundSection3 && line.contains("<!-- #3 -->")) {
					// Generate the structure for #3
					uniqueIdentifiers.put("WQC", UUID.randomUUID().toString());
					uniqueIdentifiers.put("V", UUID.randomUUID().toString());
					uniqueIdentifiers.put("VSclMin", UUID.randomUUID().toString());
					uniqueIdentifiers.put("VSclMax", UUID.randomUUID().toString());
					uniqueIdentifiers.put("VUnit", UUID.randomUUID().toString());
					uniqueIdentifiers.put("RefID", processValueID);

					// Add the generated section for #3 below the found line
					String generatedSection = generateAMLSection3(SetpointName, processValueID, uniqueIdentifiers,
							DIntView, AnaView);
					fileContent.append(generatedSection).append("\n");
					foundSection3 = true;
				}
				if (!foundSection4 && line.contains("<!-- #4 -->")) {
					// Add the generated section for #4 below the found line
					String generatedSection = generateAMLSection4(SetpointName, uniqueIdentifiers, DIntView, AnaView);
					fileContent.append(generatedSection).append("\n");
					foundSection4 = true;
				}
			}

			// Save the new content to the file
			try (FileWriter writer = new FileWriter(filePath)) {
				writer.write(fileContent.toString());
			} catch (IOException e) {
				System.err.println("Error writing to file: " + e.getMessage());
			}
		} catch (IOException e) {
			System.err.println("Error reading file: " + e.getMessage());
		}
	}

	public static void addFileToZip(String zipFilePath, String fileToAddPath) throws IOException {
		// Temporärer Puffer für das Lesen von Daten
		byte[] buffer = new byte[1024];

		// Temporäre Datei zum Schreiben der Änderungen
		File tempFile = File.createTempFile("temp_mtp", ".mtp");
		tempFile.deleteOnExit();

		try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath));
				ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempFile))) {

			// Übertragen bestehender Einträge in die temporäre Datei
			ZipEntry entry = zis.getNextEntry();
			while (entry != null) {
				if (!entry.getName().equals("Manifest.aml")) { // Überspringen der alten Manifest.aml
					zos.putNextEntry(new ZipEntry(entry.getName()));
					int len;
					while ((len = zis.read(buffer)) > 0) {
						zos.write(buffer, 0, len);
					}
					zos.closeEntry();
				}
				entry = zis.getNextEntry();
			}

			// Hinzufügen der neuen Manifest.aml zum Zip-Archiv
			try (FileInputStream fis = new FileInputStream(fileToAddPath)) {
				zos.putNextEntry(new ZipEntry("Manifest.aml"));
				int len;
				while ((len = fis.read(buffer)) > 0) {
					zos.write(buffer, 0, len);
				}
				zos.closeEntry();
			}
		}

		// Ersetzen des Original-Zip-Archivs durch das temporäre
		Files.move(tempFile.toPath(), Paths.get(zipFilePath), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}

	public static void main(String[] args) {
		// Path to the original AML file in the "in" folder
		Path sourcePath = Paths.get("MTP-Template/in/Manifest.aml");

		// Target path for the copied AML file in the "out" folder
		Path targetPath = Paths.get("MTP-Template/out/Manifest.aml");
		String sourcePathString = "MTP-Template/out/Manifest.aml";

		String ZipFilePath = "MTP-Template/out/AP4_HSU_AgentenIntegration.mtp";

		try {
			// Copy the file from "in" to "out"
			Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
			System.out.println("File successfully copied and modified.");

		} catch (IOException e) {
			System.err.println("Error when copying or modifying the file: " + e.getMessage());
		}

		try {
			addFileToZip(ZipFilePath, sourcePathString);
			System.out.println("File added to zip successfully.");
		} catch (IOException e) {
			System.err.println("Error modifying the zip file: " + e.getMessage());
		}

	}

}
