package amplTransformator;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class AmplParser {

    // Class to store the extracted data
	static class AmplData {
	    Map<String, Double> parameters = new HashMap<>();
	    Map<String, List<String>> sets = new HashMap<>(); // Werte können nun Strings sein
	    List<String> variables = new ArrayList<>();
	    List<String> equations = new ArrayList<>();
	}

    public static AmplData parseAmplFile(String filePath) throws IOException {
        AmplData amplData = new AmplData();

        // Regular expressions for parsing
        String variablePattern = "var\\s+(\\w+).*?;";
        String parameterPattern = "param\\s+(\\w+).*?=.*?([0-9.Ee+-]+);";
        String setPattern = "set\\s+(\\w+)\\s*:=\\s*(.*?);";
        String equationPattern = "subject\\sto\\s+(\\w+):\\s*(.*?);";

        Pattern varRegex = Pattern.compile(variablePattern);
        Pattern paramRegex = Pattern.compile(parameterPattern);
        Pattern setRegex = Pattern.compile(setPattern);
        Pattern equationRegex = Pattern.compile(equationPattern);

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Check for variables
                Matcher varMatcher = varRegex.matcher(line);
                if (varMatcher.find()) {
                    amplData.variables.add(varMatcher.group(1));
                    continue;
                }

                // Check for parameters
                Matcher paramMatcher = paramRegex.matcher(line);
                if (paramMatcher.find()) {
                    amplData.parameters.put(paramMatcher.group(1), Double.parseDouble(paramMatcher.group(2)));
                    continue;
                }

             // Check for sets
                Matcher setMatcher = setRegex.matcher(line);
                if (setMatcher.find()) {
                    String setName = setMatcher.group(1);
                    String[] setValues = setMatcher.group(2).replace("{", "").replace("}", "").split("\\s+|,\\s*");
                    List<String> values = new ArrayList<>();
                    for (String value : setValues) {
                        if (!value.isEmpty()) {
                            values.add(value.replace("\"", "").trim()); // Entferne Anführungszeichen und Leerzeichen
                        }
                    }
                    amplData.sets.put(setName, values);
                    continue;
                }

                // Check for equations
                Matcher equationMatcher = equationRegex.matcher(line);
                if (equationMatcher.find()) {
                    amplData.equations.add(equationMatcher.group(2));
                }
            }
        }

        return amplData;
    }

    public static void main(String[] args) {
        try {
            String filePath = "D:\\Dokumente\\OneDrive - Helmut-Schmidt-Universität\\08_Veröffentlichungen\\2024\\Applied Sciences\\Optimierungsmodell\\Output\\xUpdate.ampl"; // Update with the actual file path
            AmplData amplData = parseAmplFile(filePath);

            // Output parsed data
            System.out.println("Variables:");
            for (String variable : amplData.variables) {
                System.out.println(" - " + variable);
            }

            System.out.println("\nParameters:");
            for (Map.Entry<String, Double> param : amplData.parameters.entrySet()) {
                System.out.println(" - " + param.getKey() + " = " + param.getValue());
            }

            System.out.println("\nSets:");
            for (Map.Entry<String, List<String>> set : amplData.sets.entrySet()) {
                System.out.println(" - " + set.getKey() + " = " + set.getValue());
            }

            System.out.println("\nEquations:");
            for (String equation : amplData.equations) {
                System.out.println(" - " + equation);
            }

        } catch (IOException e) {
            System.err.println("Error reading the file: " + e.getMessage());
        }
    }
}
