package opcuaServer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;

public class Namespace extends ManagedNamespaceWithLifecycle {

	private static final String NAMESPACE_URI = "http://hsu-hh";
	private final UaFolderNode parentFolderNode;
	private final SubscriptionModel subscriptionModel;
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

	/**
	 * Add Module Type Package Folder
	 */

	// Create a "ModuleTypePackage" folder and add it to the node manager
	NodeId folderNodeId = newNodeId("ModuleTypePackage");

	public Namespace(OpcUaServer server) {
		super(server, NAMESPACE_URI);

		subscriptionModel = new SubscriptionModel(server, this);
		getLifecycleManager().addLifecycle(subscriptionModel);

		// Initialize the parent folder node
		NodeId folderNodeId = newNodeId("ModuleTypePackage");
		this.parentFolderNode = new UaFolderNode(getNodeContext(), folderNodeId, newQualifiedName("ModuleTypePackage"),
				LocalizedText.english("ModuleTypePackage"));

		// Add the Parent Folder Node to the Node Manager
		getNodeManager().addNode(parentFolderNode);
		parentFolderNode.addReference(new Reference(parentFolderNode.getNodeId(), Identifiers.Organizes,
				Identifiers.ObjectsFolder.expanded(), false));

		createAndAddNodes();
		
		  // Schedule the update task to run every second
		 scheduler.scheduleAtFixedRate(this::updateNodesDirectly, 0, 1, TimeUnit.SECONDS);
		
	}
	
    private void updateNodesDirectly() {
        getNodeManager().getNodes().stream()
            .filter(node -> node instanceof UaVariableNode)
            .map(node -> (UaVariableNode) node)
            .forEach(this::updateNodeValue);
    }
	
    private void updateNodeValue(UaVariableNode node) {
        DataValue currentValue = node.getValue();
        Variant variant = currentValue.getValue();
        Object value = variant.getValue();

        // Logic to update value; currently just setting the same value
        node.setValue(new DataValue(new Variant(value)));
    }

    private Object parseValue(String value, NodeId dataType) {
        try {
            if (Identifiers.Int32.equals(dataType)) {
                return Integer.parseInt(value);
            } else if (Identifiers.Float.equals(dataType)) {
                return Float.parseFloat(value);
            } else if (Identifiers.Boolean.equals(dataType)) {
                return Boolean.parseBoolean(value);
            } else if (Identifiers.String.equals(dataType)) {
                return value;
            } else if (Identifiers.Byte.equals(dataType)) {
                return Short.parseShort(value);
            }
        } catch (NumberFormatException e) {
            System.err.println("Error parsing value: " + e.getMessage());
        }
        return null;
    }


	private void createAndAddNodes() {
		UaFolderNode folderNode = new UaFolderNode(getNodeContext(), folderNodeId,
				newQualifiedName("ModuleTypePackage"), LocalizedText.english("ModuleTypePackage"));
		getNodeManager().addNode(folderNode);

		// Ensure the folder shows up under the server's Objects folder
		folderNode.addReference(new Reference(folderNode.getNodeId(), Identifiers.Organizes,
				Identifiers.ObjectsFolder.expanded(), false));
	}

	@Override
	public void onDataItemsCreated(List<DataItem> dataItems) {
		this.subscriptionModel.onDataItemsCreated(dataItems);
	}

	@Override
	public void onDataItemsModified(List<DataItem> dataItems) {
		this.subscriptionModel.onDataItemsModified(dataItems);
	}

	@Override
	public void onDataItemsDeleted(List<DataItem> dataItems) {
		this.subscriptionModel.onDataItemsDeleted(dataItems);
	}

	@Override
	public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
		this.subscriptionModel.onMonitoringModeChanged(monitoredItems);
	}

	public void createNodeBasedOnIdentifier(UaFolderNode parentNode, String identifier, Object initialValue) {
		// Initialize the DataTypeRules
		DataTypeRules rules = new DataTypeRules();

		// Determine the data type based on the identifier
		String dataTypeString = rules.determineDataType(identifier);
		NodeId dataTypeNodeId = getDataTypeNodeId(dataTypeString); // A method to get NodeId

		// Call addVariable based on the determined data type
		addVariable(parentNode, identifier, initialValue, dataTypeNodeId, dataTypeString);

	}
	
	void addVariable(UaFolderNode parentNode, String identifier, Object initialValue, NodeId dataType,
			String datatypeString) {
		Object initialObject = null;

		try {
			switch (datatypeString) {
			case "Int":
			case "DInt":
				initialObject = Integer.parseInt(initialValue.toString());
				break;
			case "Real":
				initialObject = Float.parseFloat(initialValue.toString());
				break;
			case "Bool":
				initialObject = Boolean.parseBoolean(initialValue.toString());
				break;
			case "String":
				initialObject = initialValue.toString();
				break;
			case "Byte":
				short unsignedByteValue = (short) (Integer.parseInt(initialValue.toString()) & 0xFF);
				initialObject = unsignedByteValue;
				break;
			case "DWord":
				initialObject =  Integer.parseInt(initialValue.toString());
				break;
			case "WQC":
				short unsignedByteValue1 = (short) (Integer.parseInt(initialValue.toString()) & 0xFF);
				initialObject = unsignedByteValue1;
				break;
			default:
				System.err.println("Unsupported data type: " + datatypeString);
				return; // Exit if type is not supported
			}

		} catch (NumberFormatException e) {
			System.err.println("Error parsing initial value for " + identifier + ": " + e.getMessage());
			return;
		}

		NodeId variableId = newNodeId(identifier);
		UaVariableNode variableNode = new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
				.setNodeId(variableId)
				.setAccessLevel(AccessLevel.READ_WRITE)
				.setUserAccessLevel(AccessLevel.READ_WRITE)
				.setBrowseName(newQualifiedName(identifier))
				.setDisplayName(LocalizedText.english(identifier))
				.setDataType(dataType)
				.setTypeDefinition(Identifiers.BaseDataVariableType)
				.build();

		variableNode.setValue(new DataValue(new Variant(initialObject)));
		variableNode.getFilterChain().addLast(new AttributeLoggingFilter(AttributeId.Value::equals));

		getNodeManager().addNode(variableNode);
		parentNode.addOrganizes(variableNode);
	}

	private NodeId getDataTypeNodeId(String dataType) {
		switch (dataType) {
		case "DInt":
			//return Identifiers.UInt32;
			return Identifiers.Int32;
		case "Bool":
			return Identifiers.Boolean;
		case "DWord":
			//return Identifiers.UInt32;
			return Identifiers.Int32;
		case "Int":
			//return Identifiers.UInt32;
			return Identifiers.Int32;
		case "Byte":
			return Identifiers.Byte;
		case "String":
			return Identifiers.String;
		case "Real":
			return Identifiers.Float;
		case "WQC":
			return Identifiers.Byte;
		default:
			return Identifiers.BaseDataType;
		}
	}

	// Getter für den Parent Folder Node
	public UaFolderNode getParentFolderNode() {
		return this.parentFolderNode;
	}
}
