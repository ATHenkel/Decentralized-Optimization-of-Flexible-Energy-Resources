package opcuaServer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GetNodesFromMTP {

  //  private static final String FILE_PATH = "MTP-Template/out/Manifest.aml";
    
    private static String FILE_PATH = "D:\\Dokumente\\OneDrive - Helmut-Schmidt-Universität\\04_Programmierung\\ElectrolyseurScheduling JADE\\MTP-Template\\out\\Manifest.aml";
    
    public static void main(String[] args) {
        sortNodeNames(FILE_PATH, "<InternalElement Name=\"SourceList\"", "</InternalElement>");
    }

    public static void sortNodeNames(String filePath, String startTag, String endTag) {
        List<String> dIntViews = new ArrayList<>();
        List<String> anaViews = new ArrayList<>();
        List<String> others = new ArrayList<>();
        List<String> allNodes = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean capture = false;
            Pattern pattern = Pattern.compile("<ExternalInterface Name=\"([^\"]+)\"");

            while ((line = reader.readLine()) != null) {
                if (line.trim().contains(startTag)) {
                    capture = true;
                }
                if (capture && line.trim().startsWith("<ExternalInterface Name=")) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        String nodeName = matcher.group(1);
                        allNodes.add(nodeName);
                        if (nodeName.startsWith("DIntView")) {
                            dIntViews.add(nodeName);
                        } else if (nodeName.startsWith("AnaView")) {
                            anaViews.add(nodeName);
                        } else {
                            others.add(nodeName);
                        }
                    }
                }
                if (line.trim().contains(endTag) && capture) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
