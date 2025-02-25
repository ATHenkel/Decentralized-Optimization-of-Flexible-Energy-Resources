package opcuaServer;

import java.util.HashMap;
import java.util.Map;

import CAEX.Attribute;
import CAEX.InternalElement;

import java.util.List;

public class GlobalStorage {
    private static Map<String, String> opcUaNodes = new HashMap<>();
    private static Map<String, InternalElement> internalElementsMap = new HashMap<>();
    private static Map<String, String> defaultValuesMap = new HashMap<>();

    public static void addOpcUaNodes(String id, String identifier) {
        opcUaNodes.put(id, identifier);
    }

    public static Map<String, String> getOpcUaNodes() {
        return opcUaNodes;
    }
    
    public static void addInternalElement(String id, InternalElement internalElement) {
        internalElementsMap.put(id, internalElement);
    }

    public static InternalElement getInternalElement(String id) {
        return internalElementsMap.get(id);
    }

    public static Map<String, InternalElement> getAllInternalElements() {
        return internalElementsMap;
    }

    public static void setAllInternalElements(Map<String, InternalElement> map) {
        internalElementsMap = map;
    }
    
    public static void addDefaultValue(String id, String defaultValue) {
        defaultValuesMap.put(id, defaultValue);
    }

    public static String getDefaultValue(String id) {
        return defaultValuesMap.get(id);
    }

    public static Map<String, String> getAllDefaultValues() {
        return defaultValuesMap;
    }
    
    public static void getDefaultValueFromOpcUaNode() {
        // Durchgehen aller OPC UA Nodes
        for (Map.Entry<String, String> opcEntry : opcUaNodes.entrySet()) {
            String opcId = opcEntry.getKey();  // ID aus OPC UA Nodes

            // Durchgehen aller Internal Elements
            for (InternalElement element : internalElementsMap.values()) {
                
                // Holen der Attribute des Internal Elements
                List<Attribute> attributes = element.getAttribute();

                // Durchgehen aller Attribute jedes Internal Elements
                for (Attribute attribute : attributes) {
                    
                    if (attribute.getValue() == null) {
                        continue;
                    }

                    // Vergleiche die ID mit dem Wert des Attributs
                    if (attribute.getValue().equals(opcId)) {
                        // Ausgabe des Default-Wertes, wenn die IDs übereinstimmen
                        addDefaultValue(opcId, attribute.getDefaultValue());
                    }
                }
            }
        }
    }
}
