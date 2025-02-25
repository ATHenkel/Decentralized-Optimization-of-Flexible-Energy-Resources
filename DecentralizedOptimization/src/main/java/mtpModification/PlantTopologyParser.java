package mtpModification;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a topology file and extracts modules.
 */
public class PlantTopologyParser {
    /**
     * Reads the specified topology file and extracts module information.
     * Each module is defined by its visible name, endpoint URL, and instance name.
     *
     * @param filePath The file path of the topology file to be parsed.
     * @return A list of extracted modules.
     */
    public static List<Module> parseTopologyFile(String filePath) {
        List<Module> modules = new ArrayList<>();
        String line;
        String visibleName = null;
        String endpointUrl = null;
        String instanceName = null;

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            while ((line = br.readLine()) != null) {
                if (line.contains("<VisibleName>")) {
                    visibleName = line.trim().replace("<VisibleName>", "").replace("</VisibleName>", "");
                    if (visibleName != null && endpointUrl != null && instanceName != null) {
                        modules.add(new Module(visibleName, endpointUrl, instanceName));
                        visibleName = null;
                        endpointUrl = null;
                        instanceName = null;
                    }
                } else if (line.contains("<OPCServer ID=")) {
                    int startIdx = line.indexOf(">")+1;
                    int endIdx = line.indexOf("</OPCServer>");
                    if (startIdx != 0 && endIdx != -1 && endIdx > startIdx) {
                        endpointUrl = line.substring(startIdx, endIdx);
                    }
                    if (visibleName != null && endpointUrl != null && instanceName != null) {
                        modules.add(new Module(visibleName, endpointUrl, instanceName));
                        visibleName = null;
                        endpointUrl = null;
                        instanceName = null;
                    }
                } else if (line.trim().startsWith("<Tag ID=")) {
                    instanceName = line.trim().replaceFirst(".*<Tag ID=\"\\d+\">", "").replace("</Tag>", "");
                    if (visibleName != null && endpointUrl != null && instanceName != null) {
                        modules.add(new Module(visibleName, endpointUrl, instanceName));
                        visibleName = null;
                        endpointUrl = null;
                        instanceName = null;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return modules;
    }
}

/**
 * Represents a module with visible name, endpoint URL, and instance name.
 */
class Module {
    String visibleName;
    String endpointUrl;
    String instanceName;

    public Module(String visibleName, String endpointUrl, String instanceName) {
        this.visibleName = visibleName;
        this.endpointUrl = endpointUrl;
        this.instanceName = instanceName;
    }

    public String getVisibleName() {
        return visibleName;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public String getInstanceName() {
        return instanceName;
    }
}
