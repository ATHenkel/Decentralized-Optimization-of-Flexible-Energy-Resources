package opcuaServer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.server.EndpointConfiguration;

import CAEX.CAEXFile;

public class Server {

    private static final int TCP_BIND_PORT = 4200;
    private static OpcUaServer server;
    private static Namespace customNamespace;
    private static String filePath = "MTP-Template/out/Manifest.aml";
   

    // Diese Methode initialisiert die statische Instanz
    public static void init() throws Exception {
        OpcUaServerConfig serverConfig = OpcUaServerConfig.builder()
            .setApplicationName(LocalizedText.english("MTP-Server"))
            .setApplicationUri("urn:example:server")
            .setEndpoints(createEndpointConfigurations())
            .build();
        server = new OpcUaServer(serverConfig);
        customNamespace = new Namespace(server);
    }

    private static Set<EndpointConfiguration> createEndpointConfigurations() {
        EndpointConfiguration endpointConfiguration = new EndpointConfiguration.Builder()
            .setBindAddress("0.0.0.0") //accept all addresses
            .setBindPort(TCP_BIND_PORT)
            .setHostname("10.246.55.179")
            .setPath("") //removed 
            .setSecurityMode(MessageSecurityMode.None)
            .setSecurityPolicy(SecurityPolicy.None)
            .build();

        return Set.of(endpointConfiguration);
    }

    public static CompletableFuture<Void> startup() {
        UaFolderNode parentNode = customNamespace.getParentFolderNode();

        return server.startup().thenRun(() -> {
            customNamespace.startup();

            // Laden und Verarbeiten der Manifest.aml
            File xmlfile = new File(filePath);
            CAEXFile manifestAML = loadFromXmlFile(xmlfile);
            AMLImport.getInternalElements(manifestAML);
            AMLImport.getOpcUaNodes(manifestAML);
            Map<String, String> opcUaNodes = GlobalStorage.getOpcUaNodes();

            // Knoten basierend auf den gespeicherten Daten erstellen
            opcUaNodes.forEach((identifier, nodeName) -> {
                // Retrieving the default value from GlobalStorage
            	GlobalStorage.getDefaultValueFromOpcUaNode();
                String defaultValue = GlobalStorage.getDefaultValue(identifier);
                Object initialValue = defaultValue != null ? defaultValue : determineFallbackInitialValue(); // Fallback, falls kein Default-Wert vorhanden ist
                customNamespace.createNodeBasedOnIdentifier(parentNode, nodeName, initialValue);
            });
        });
    }
    
    private static Object determineFallbackInitialValue() {
        return false; // oder ein anderer geeigneter Standardwert
    }
    
    
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
			ex.printStackTrace(); 
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

      public static CompletableFuture<Void> initializeAndStartServer() {
        try {
            init(); // Initialisiert den Server, falls noch nicht geschehen
        } catch (Exception e) {
            CompletableFuture<Void> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
        
        return startup().thenRun(() -> {
            // Server erfolgreich gestartet
            System.out.println("OPC-UA Server started successfully.");
        }).exceptionally(ex -> {
            System.err.println("Error starting the OPC-UA server: " + ex.getMessage());
            return null;
        });
    }


}

