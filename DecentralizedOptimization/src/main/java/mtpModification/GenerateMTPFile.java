package mtpModification;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

//import ch.qos.logback.classic.Level;
import opcuaServer.*;
import java.util.Iterator;

public class GenerateMTPFile {
	 public static void main(String[] args) {
	        List<Module> modules = loadModules();
	        filterModules(modules);
	        List<String> phoneBook = createPhoneBook(modules);
	        handleAMLModification(phoneBook);

	        // Deactivate Logger-Notification
//	        Logger logger = LoggerFactory.getLogger("opcuaServer.AttributeLoggingFilter");
//	        ((ch.qos.logback.classic.Logger) logger).setLevel(Level.OFF);

	        // Start the OPC-UA Server
	        Server.initializeAndStartServer()
	        .thenRun(() -> {
	            // Wait for user input to continue
	            System.out.println("Press 'enter' to initialize system.");
	            Scanner scanner = new Scanner(System.in);
	            while (!scanner.nextLine().equals("enter")) {
	                System.out.println("Please press 'enter' to continue.");
	            }
	            scanner.close();
	        })
	        .exceptionally(e -> {
	            System.err.println("Error when starting the OPC-UA server: " + e.getMessage());
	            return null;
	        });
	    }

    private static List<Module> loadModules() {
        String topologyFilePath = "C:\\Program Files\\OrchestrationDesigner With800xA (2024)\\2024_Dtec-ElectrolysisPlant\\Topology\\ElectrolysisPlant.mtd";
        return PlantTopologyParser.parseTopologyFile(topologyFilePath);
    }

    private static void filterModules(List<Module> modules) {
        Iterator<Module> iterator = modules.iterator();
        while (iterator.hasNext()) {
            if (!isElectrolyser(iterator.next())) {
                iterator.remove();
            }
        }
    }

    private static boolean isElectrolyser(Module module) {
        String amlFilePath = "C:\\Program Files\\OrchestrationDesigner With800xA (2024)\\2024_Dtec-ElectrolysisPlant\\MTP Lib\\" + module.visibleName + ".aml";
        if (new File(amlFilePath).exists()) {
            return containsElectrolyserInfo(amlFilePath);
        } else {
            System.out.println("File not found: " + amlFilePath);
            return false;
        }
    }

    private static List<String> createPhoneBook(List<Module> modules) {
        modules.sort(Comparator.comparingInt(module -> extractNumber(module.instanceName)));
        List<String> phoneBook = new ArrayList<>();
        for (int i = 0; i < modules.size(); i++) {
            phoneBook.add(modules.get(i).instanceName + "--PEAAgent" + (i + 1));
        }
        return phoneBook;
    }

    private static void handleAMLModification(List<String> phoneBook) {
    	Path sourcePath = Paths.get("MTP-Template/in/Manifest.aml");
    	Path targetPath = Paths.get("MTP-Template/out/Manifest.aml");
    	String zipFilePath = "MTP-Template/out/AP4_HSU_AgentenIntegration.mtp";

        try {
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            for (String agent : phoneBook) {
                String baseName = agent.split("--PEAAgent")[0];
                AMLSectionGenerator.addSetpointToAML(targetPath.toString(), baseName + "_OptimizedState", true, false);
                AMLSectionGenerator.addSetpointToAML(targetPath.toString(), baseName + "_OptimizedSetpoint", false, true);
            }
            System.out.println("File modified successfully.");
            AMLSectionGenerator.addFileToZip(zipFilePath, targetPath.toString());
            System.out.println("File added to zip successfully.");
        } catch (IOException e) {
            System.err.println("Error modifying the file: " + e.getMessage());
        }
    }

    private static int extractNumber(String name) {
        Matcher matcher = Pattern.compile("\\d+").matcher(name);
        return matcher.find() ? Integer.parseInt(matcher.group()) : 0;
    }

    public static boolean containsElectrolyserInfo(String filePath) {
        try {
            File inputFile = new File(filePath);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inputFile);
            doc.getDocumentElement().normalize();

            NodeList externalInterfaceList = doc.getElementsByTagName("ExternalInterface");

            for (int temp = 0; temp < externalInterfaceList.getLength(); temp++) {
                Node externalInterfaceNode = externalInterfaceList.item(temp);

                if (externalInterfaceNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element externalInterfaceElement = (Element) externalInterfaceNode;
                    NodeList valueList = externalInterfaceElement.getElementsByTagName("Value");

                    for (int i = 0; i < valueList.getLength(); i++) {
                        Node valueNode = valueList.item(i);

                        if (valueNode.getNodeType() == Node.ELEMENT_NODE) {
                            String valueContent = valueNode.getTextContent().toLowerCase();
                            if (valueContent.contains("electrolyser")) {
                                return true; // Electrolyzer information found
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false; // Electrolyzer information not found
    }
}

