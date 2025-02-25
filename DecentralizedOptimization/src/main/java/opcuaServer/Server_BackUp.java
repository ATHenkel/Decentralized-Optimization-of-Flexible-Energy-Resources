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

public class Server_BackUp {

    private static final int TCP_BIND_PORT = 4200;
    private final OpcUaServer server;
    private final Namespace customNamespace;
	private String filePath;
    
//    public static void main(String[] args) throws Exception {
//    	
//    	// Save default values
//        GlobalStorage.getDefaultValueFromOpcUaNode();
//
//        // Start Server
//        Server server = new Server(filePath2);
//        server.startup().get();
//
//        // Wait for the server shutdown
//        final CompletableFuture<Void> future = new CompletableFuture<>();
//        Runtime.getRuntime().addShutdownHook(new Thread(() -> future.complete(null)));
//        future.get();
//    }
	

    public Server_BackUp(String filePath) throws Exception {
    	this.filePath = filePath; // Speichern des übergebenen Pfades
        OpcUaServerConfig serverConfig = OpcUaServerConfig.builder()
            .setApplicationName(LocalizedText.english("MTP-Server"))
            .setApplicationUri("urn:example:server")
            .setEndpoints(createEndpointConfigurations())
            .build();
        server = new OpcUaServer(serverConfig);
        customNamespace = new Namespace(server);
    }
    
    private Set<EndpointConfiguration> createEndpointConfigurations() {
        EndpointConfiguration endpointConfiguration = new EndpointConfiguration.Builder()
            .setBindAddress("localhost")
            .setBindPort(TCP_BIND_PORT)
            .setHostname("localhost")
            .setPath("/milo")
            .setSecurityMode(MessageSecurityMode.None)
            .setSecurityPolicy(SecurityPolicy.None)
            .build();

        return Set.of(endpointConfiguration);
    }

    public CompletableFuture<Void> startup() {
        UaFolderNode parentNode = customNamespace.getParentFolderNode();

        return server.startup().thenRun(() -> {
        	customNamespace.startup();

            // Get Manifest.aml file
    		File xmlfile = new File(filePath);
    		CAEXFile manifestAML = loadFromXmlFile(xmlfile);
    		AMLImport.getInternalElements(manifestAML);
    		
    		// Get OpcUaNodes from Manifest.aml file
    		AMLImport.getOpcUaNodes(manifestAML); 
            Map<String, String> opcUaNodes = GlobalStorage.getOpcUaNodes();
                    
            // Call the method to get default values based on OPC UA Nodes
            GlobalStorage.getDefaultValueFromOpcUaNode();
           
            // Create the nodes based on the data from GlobalStorage
            opcUaNodes.forEach((identifier, nodeName) -> {
                // Retrieving the default value from GlobalStorage
                String defaultValue = GlobalStorage.getDefaultValue(identifier);
                Object initialValue = defaultValue != null ? defaultValue : determineFallbackInitialValue(); // Fallback, falls kein Default-Wert vorhanden ist
                
                // Create the nodes based on the identifier
                customNamespace.createNodeBasedOnIdentifier(parentNode, nodeName, initialValue);
            });
        });
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
    
    private Object determineFallbackInitialValue() {
        return false; // oder ein anderer geeigneter Standardwert
    }
    
    public void initializeAndStartServer() throws Exception {     
    	
    	String pathString = "D:\\Dokumente\\OneDrive - Helmut-Schmidt-Universität\\04_Programmierung\\ElectrolyseurScheduling JADE\\MTP-Template\\out\\Manifest.aml";
    	Server_BackUp server = new Server_BackUp(pathString);
        server.startup().get();
        
        // Wait for the server shutdown
        final CompletableFuture<Void> future = new CompletableFuture<>();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> future.complete(null)));
        future.get();
        
        System.out.println("OPC-UA Server started successfully.");
    }

}
