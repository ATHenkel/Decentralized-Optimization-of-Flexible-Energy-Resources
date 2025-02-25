package amplTransformator;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class CentralizedToADMMConverter {

    private static int slackVariableCounter = 1; // Counter for slack variables
    private static List<String> globalSlackConstraints = new ArrayList<>();


    public static void main(String[] args) {
        // Define relative paths for input and output directories
        String inputDirectory = "in";
        String outputDirectory = "out";

        // Define file names
        String inputFile = inputDirectory + File.separator + "CentralizedModel.ampl";
        String outputSlackFile = inputDirectory + File.separator + "CentralizedModel_Slack.ampl";

        try {
            System.out.println("Starting file processing: " + inputFile);

            // Read the content of the AMPL file
            String content = new String(Files.readAllBytes(Paths.get(inputFile)));

            // Parse the centralized optimization model
            CentralModel centralModel = parseCentralModel(content);

            // Identify and split range constraints into separate constraints
            splitRangeConstraints(centralModel);

            // Save the modified centralized model with Slack variables
            saveCentralModel(centralModel, outputSlackFile);

            // Reload the updated model from the saved file
            String updatedContent = new String(Files.readAllBytes(Paths.get(outputSlackFile)));
            CentralModel updatedModel = parseCentralModel(updatedContent);

            // Generate updates using the new model with Slack variables
            generateUpdates(updatedModel, outputDirectory);
            generateSlackUpdates(outputDirectory);

            System.out.println("Processing completed. Files have been created in the directory: " + outputDirectory);

        }
        catch (IOException e) {
            System.err.println("Error processing the file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Parses the centralized optimization model from the input file.
     */
    private static CentralModel parseCentralModel(String content) {
        Map<String, List<String>> setsAndVariables = extractSetsAndVariables(content);
        List<String> sets = setsAndVariables.get("sets");
        List<String> variablesDefinitions = setsAndVariables.get("variables");
        List<String> parameters = extractParameters(content); // Extract parameters
        Set<String> variables = extractVariables(content);
        List<String> constraints = extractAllConstraints(content);

        // Extract the objective function
        String objectiveFunction = extractObjectiveFunction(content);

        CentralModel centralModel = new CentralModel(sets, variablesDefinitions, parameters, variables, constraints);
        centralModel.setObjectiveFunction(objectiveFunction); // Set the objective function
        return centralModel;
    }

    /**
     * Extracts the objective function from the optimization model.
     */
    private static String extractObjectiveFunction(String content) {
        Matcher objectiveMatcher = Pattern.compile("minimize\\s+[^:]+:(.*?);", Pattern.DOTALL).matcher(content);
        if (objectiveMatcher.find()) {
            return objectiveMatcher.group(1).trim(); // Extract the objective function
        }
        return null; // No objective function found
    }

    /**
     * Identifies and splits range constraints into separate constraints with slack variables.
     */
    private static void splitRangeConstraints(CentralModel model) {
        List<String> updatedConstraints = new ArrayList<>();
        globalSlackConstraints.clear(); // Clear the global list before adding new constraints

        for (String constraint : model.getConstraints()) {
            Matcher rangeMatcher = Pattern.compile("(.*?)\\s*<=\\s*(.*?)\\s*<=\\s*(.*?);").matcher(constraint);

            if (rangeMatcher.find()) {
                String lowerBound = rangeMatcher.group(1).trim();
                String middle = rangeMatcher.group(2).trim();
                String upperBound = rangeMatcher.group(3).trim();

                // Create separate constraints with slack variables
                String lowerConstraint = "subject to Slack_Lower_Bound_" + slackVariableCounter + ": " +
                                          lowerBound + " - " + middle + " + s" + slackVariableCounter + " = 0;";
                globalSlackConstraints.add(lowerConstraint);
                slackVariableCounter++;

                String upperConstraint = "subject to Slack_Upper_Bound_" + slackVariableCounter + ": " +
                                          middle + " - " + upperBound + " + s" + slackVariableCounter + " = 0;";
                globalSlackConstraints.add(upperConstraint);
                slackVariableCounter++;

                updatedConstraints.add(lowerConstraint);
                updatedConstraints.add(upperConstraint);
            } else {
                updatedConstraints.add(constraint);
            }
        }

        model.setConstraints(updatedConstraints);
    }

    /**
     * Saves the modified centralized model including slack variables.
     */
    private static void saveCentralModel(CentralModel model, String outputPath) throws IOException {
        List<String> lines = new ArrayList<>();

        // Add sets
        lines.add("# Sets");
        lines.addAll(model.getSets());

        // Add parameters
        if (!model.getParameters().isEmpty()) {
            lines.addAll(model.getParameters());
            lines.add(""); // Empty line for better readability
        }

        // Add decision variables (including slack variables)
        lines.add("# Decision Variables");
        lines.addAll(model.getVariablesDefinitions());
        for (int i = 1; i < slackVariableCounter; i++) {
            lines.add("var s" + i + " >= 0;");
        }
        lines.add(""); // Empty line for better readability

        // Add constraints (including 'subject to' constraints for slack variables)
        lines.add("# Constraints");
        lines.addAll(model.getConstraints());
        lines.add(""); // Empty line for better readability

        // Write to file
        Files.write(Paths.get(outputPath), lines);
        System.out.println("Central Model saved: " + outputPath);
    }


    /**
     * Generates update files for each decision variable in the optimization model.
     */
    private static void generateUpdates(CentralModel model, String outputDirectory) throws IOException {
        for (String variable : model.getVariables()) {
            List<String> fileContent = new ArrayList<>();

            fileContent.add("# Sets");
            fileContent.addAll(model.getSets());

            if (!model.getParameters().isEmpty()) {
                fileContent.addAll(model.getParameters());
                fileContent.add("");
            }

            fileContent.add("# Decision Variables");
            fileContent.addAll(filterVariablesByName(model.getVariablesDefinitions(), variable));
            fileContent.add("");

            fileContent.add("# Constraints");
            fileContent.addAll(filterConstraintsByVariable(model.getConstraints(), variable));

            // Save update file
            String fileName = variable + "Update.ampl";
            writeToFile(outputDirectory, fileName, fileContent);
            System.out.println("File created: " + fileName);
        }
    }

    /**
     * Generates an AMPL update file for all slack variables.
     */
    private static void generateSlackUpdates(String outputDirectory) throws IOException {
        List<String> slackContent = new ArrayList<>();

        // Add Slack variable definitions
        slackContent.add("# Slack Variable Updates");
        for (int i = 1; i < slackVariableCounter; i++) {
            slackContent.add("var s" + i + " >= 0;");
        }
        slackContent.add("");

        // Add Slack constraints from the global list
        slackContent.add("# Slack Variable Constraints");
        slackContent.addAll(globalSlackConstraints);
        slackContent.add("");

        // Save the Slack update file
        String fileName = "sUpdate.ampl";
        writeToFile(outputDirectory, fileName, slackContent);
        System.out.println("Slack update file created: " + fileName);
    }

    /**
     * Writes content to a file in the specified directory.
     */
    private static void writeToFile(String directory, String fileName, List<String> lines) throws IOException {
        Path outputPath = Paths.get(directory, fileName);
        Files.createDirectories(outputPath.getParent());
        Files.write(outputPath, lines);
        System.out.println("File saved: " + outputPath);
    }

    private static List<String> filterConstraintsByVariable(List<String> constraints, String variable) {
        List<String> filteredConstraints = new ArrayList<>();

        for (String constraint : constraints) {
            // Angepasster regulärer Ausdruck für Variablen mit Indizes
            if (constraint.matches(".*\\b" + Pattern.quote(variable) + "\\b.*") || constraint.contains(variable + "[")) {
                filteredConstraints.add(constraint);
            }
        }

        System.out.println("Filtering completed. Number of constraints found for variable: '" + variable + "': " + filteredConstraints.size());
        return filteredConstraints;
    }
    

    /**
     * Extracts sets and variables from the given optimization model content.
     * This method identifies both declared sets and decision variables.
     *
     * @param content The content of the optimization model as a string.
     * @return A map containing two lists: one for sets and one for variables.
     */
    private static Map<String, List<String>> extractSetsAndVariables(String content) {
        Map<String, List<String>> components = new HashMap<>();
        components.put("sets", new ArrayList<>());
        components.put("variables", new ArrayList<>());

        // Extract sets (with or without assignments)
        System.out.println("Extracting sets:");
        Matcher setMatcher = Pattern.compile("\\bset\\s+[a-zA-Z_][a-zA-Z0-9_]*\\s*(:=\\s*\\{[^}]*\\})?\\s*;\\s*(#.*)?").matcher(content);
        while (setMatcher.find()) {
            String set = setMatcher.group().trim();
            components.get("sets").add(set);
            System.out.println("Found set: " + set);
        }

        // Extract variables
        System.out.println("Extracting variables:");
        Matcher varMatcher = Pattern.compile("\\bvar\\s+[a-zA-Z_][a-zA-Z0-9_]*\\s*(\\{[^}]*\\})?\\s*(binary|>=\\s*\\d+)?\\s*;").matcher(content);
        while (varMatcher.find()) {
            String variable = varMatcher.group().trim();
            components.get("variables").add(variable);
            System.out.println("Found variable: " + variable);
        }

        // Summary of extracted elements
        System.out.println("Extraction completed.");
        System.out.println("Total sets found: " + components.get("sets").size());
        System.out.println("Total variables found: " + components.get("variables").size());

        return components;
    }



    private static List<String> extractParameters(String content) {
        List<String> parameters = new ArrayList<>();
        Matcher paramMatcher = Pattern.compile("\\bparam\\s+[^;]+;\\s*(#.*)?").matcher(content); // Erfasst Parameter mit optionalen Kommentaren
        while (paramMatcher.find()) {
            String parameter = paramMatcher.group().trim();
            if (!parameter.isEmpty()) {
                parameters.add(parameter);
            }
        }
        return parameters;
    }

    private static Set<String> extractVariables(String content) {
        Set<String> variables = new HashSet<>();
        Matcher varMatcher = Pattern.compile("\\bvar\\s+([a-zA-Z_][a-zA-Z0-9_]*\\s*)\\{").matcher(content);
        while (varMatcher.find()) {
            variables.add(varMatcher.group(1));
        }
        return variables;
    }

    private static List<String> extractAllConstraints(String content) {
        List<String> constraints = new ArrayList<>();
        Matcher matcher = Pattern.compile("subject to.*?;", Pattern.DOTALL).matcher(content);
        while (matcher.find()) {
            constraints.add(matcher.group());
        }
        return constraints;
    }

    private static List<String> filterVariablesByName(List<String> variablesDefinitions, String variableName) {
        List<String> filteredVariables = new ArrayList<>();
        for (String varDef : variablesDefinitions) {
            if (varDef.contains("var " + variableName + "{")) {
                filteredVariables.add(varDef);
            }
        }
        return filteredVariables;
    }

}

class CentralModel {
    private final List<String> sets;
    private final List<String> variablesDefinitions;
    private final List<String> parameters;
    private final Set<String> variables;
    private List<String> constraints;
    private String objectiveFunction;
    private List<String> slackConstraints; // Remove initialization here

    public CentralModel(List<String> sets, List<String> variablesDefinitions, List<String> parameters, 
                        Set<String> variables, List<String> constraints) {
        this.sets = sets;
        this.variablesDefinitions = variablesDefinitions;
        this.parameters = parameters;
        this.variables = variables;
        this.constraints = constraints;
        this.slackConstraints = new ArrayList<>(); 
    }

    public List<String> getSlackConstraints() {
        return new ArrayList<>(slackConstraints); 
    }

    public void setSlackConstraints(List<String> slackConstraints) {
        this.slackConstraints = new ArrayList<>(slackConstraints); 
    }

    public List<String> getSets() {
        return sets;
    }

    public List<String> getVariablesDefinitions() {
        return variablesDefinitions;
    }

    public List<String> getParameters() {
        return parameters; 
    }

    public Set<String> getVariables() {
        return variables;
    }

    public List<String> getConstraints() {
        return constraints;
    }

    public void setConstraints(List<String> constraints) {
        this.constraints = constraints;
    }

    public String getObjectiveFunction() {
        return objectiveFunction;
    }

    public void setObjectiveFunction(String objectiveFunction) {
        this.objectiveFunction = objectiveFunction;
    }
}



