package amplTransformator;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class AmplToAdmmConverter {

    private static int slackVariableCounter = 1; // Counter für Slack-Variablen

    public static void main(String[] args) {
        // Absoluter Pfad zur .ampl-Datei
        String amplFilePath = "D:\\Dokumente\\OneDrive - Helmut-Schmidt-Universität\\08_Veröffentlichungen\\2024\\Applied Sciences\\Optimierungsmodell\\CentralizedModel.ampl";
        // Absoluter Pfad zum Ausgabeverzeichnis
        String outputDirectory = "D:\\Dokumente\\OneDrive - Helmut-Schmidt-Universität\\08_Veröffentlichungen\\2024\\Applied Sciences\\Optimierungsmodell\\Output";

        try {
            System.out.println("Start der Verarbeitung der Datei: " + amplFilePath);

            // Dateiinhalt lesen
            String content = new String(Files.readAllBytes(Paths.get(amplFilePath)));

            // Zentrales Modell vorbereiten
            CentralModel centralModel = parseCentralModel(content);

            // Wertebereichsgleichungen identifizieren und aufteilen
            splitRangeConstraints(centralModel);

            // Zwischenspeicherung des Modells nach Hinzufügen von Slack-Variablen
            saveCentralModel(centralModel, "D:\\Dokumente\\OneDrive - Helmut-Schmidt-Universität\\08_Veröffentlichungen\\2024\\Applied Sciences\\Optimierungsmodell\\CentralizedModel_Slack.ampl");

            // Updates generieren
            generateUpdates(centralModel, outputDirectory);
            generateSlackUpdates(centralModel, outputDirectory);

            System.out.println("Verarbeitung abgeschlossen. Dateien wurden im Verzeichnis " + outputDirectory + " erstellt.");

        } catch (IOException e) {
            System.err.println("Fehler beim Verarbeiten der Datei: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static CentralModel parseCentralModel(String content) {
        Map<String, List<String>> setsAndVariables = extractSetsAndVariables(content);
        List<String> sets = setsAndVariables.get("sets");
        List<String> variablesDefinitions = setsAndVariables.get("variables");
        List<String> parameters = extractParameters(content); // Direkt als List speichern
        Set<String> variables = extractVariables(content);
        List<String> constraints = extractAllConstraints(content);

        // Zielfunktion extrahieren
        String objectiveFunction = extractObjectiveFunction(content);

        CentralModel centralModel = new CentralModel(sets, variablesDefinitions, parameters, variables, constraints);
        centralModel.setObjectiveFunction(objectiveFunction); // Zielfunktion setzen
        return centralModel;
    }
    
    private static String extractObjectiveFunction(String content) {
        Matcher objectiveMatcher = Pattern.compile("minimize\\s+[^:]+:(.*?);", Pattern.DOTALL).matcher(content);
        if (objectiveMatcher.find()) {
            return objectiveMatcher.group(1).trim(); // Zielfunktion extrahieren
        }
        return null; // Keine Zielfunktion gefunden
    }

    private static void splitRangeConstraints(CentralModel model) {
        List<String> updatedConstraints = new ArrayList<>();

        for (String constraint : model.getConstraints()) {
            // Erkennen von Wertebereichsgleichungen
            Matcher rangeMatcher = Pattern.compile("(.*?)\\s*<=\\s*(.*?)\\s*<=\\s*(.*?);").matcher(constraint);

            if (rangeMatcher.find()) {
                String lowerBound = rangeMatcher.group(1).trim();
                String middle = rangeMatcher.group(2).trim();
                String upperBound = rangeMatcher.group(3).trim();

                // Umformulieren in zwei separate Gleichungen mit "subject to"
                String lowerConstraint = "subject to Slack_Lower_Bound_" + slackVariableCounter + ": " +
                                          lowerBound + " - " + middle + " + s" + slackVariableCounter + " = 0;";
                slackVariableCounter++;

                String upperConstraint = "subject to Slack_Upper_Bound_" + slackVariableCounter + ": " +
                                          middle + " - " + upperBound + " + s" + slackVariableCounter + " = 0;";
                slackVariableCounter++;

                updatedConstraints.add(lowerConstraint);
                updatedConstraints.add(upperConstraint);
            } else {
                // Unveränderte Gleichungen beibehalten
                updatedConstraints.add(constraint);
            }
        }

        model.setConstraints(updatedConstraints);
    }
    
    private static Map<String, List<String>> analyzeObjectiveFunction(String objectiveFunction, Set<String> variables) {
        Map<String, List<String>> variableToTermsMap = new HashMap<>();
        variables.forEach(variable -> variableToTermsMap.put(variable, new ArrayList<>()));

        // Splitte die Zielfunktion in Terme (berücksichtige + und - als Trenner)
        String[] terms = objectiveFunction.split("(?=\\+)|(?=\\-)");

        for (String term : terms) {
            term = term.trim(); // Entferne Leerzeichen

            // Überprüfe, ob der Term eine der Variablen enthält
            boolean matched = false;
            for (String variable : variables) {
                if (term.matches(".*\\b" + Pattern.quote(variable) + "\\b.*")) { // Präzise Suche nach der Variablen
                    variableToTermsMap.get(variable).add(term);
                    matched = true;
                    break; // Verlasse die Schleife, da ein Term nur zu einer Variablen gehört
                }
            }

            if (!matched) {
                System.err.println("Ignorierter Term (keine passende Variable gefunden): " + term);
            }
        }
        return variableToTermsMap;
    }


    private static void generateUpdates(CentralModel model, String outputDirectory) throws IOException {
        String objectiveFunction = model.getObjectiveFunction();
        if (objectiveFunction == null) {
            System.err.println("Keine Zielfunktion im zentralen Modell gefunden.");
            return;
        }

        // Analysiere die Zielfunktion und ordne Terme den Variablen zu
        Map<String, List<String>> variableToTermsMap = analyzeObjectiveFunction(objectiveFunction, model.getVariables());

        for (String variable : model.getVariables()) {
            List<String> combinedContent = new ArrayList<>();
            
            // Sets hinzufügen
            combinedContent.add("# Sets");
            combinedContent.addAll(model.getSets());

            // Parameters hinzufügen, wenn sie nicht leer sind
            if (!model.getParameters().isEmpty()) {
                combinedContent.addAll(model.getParameters());
                combinedContent.add(""); // Leerzeile zur besseren Lesbarkeit
            }

            // Decision Variables hinzufügen
            combinedContent.add("# Decision Variables");
            combinedContent.addAll(filterVariablesByName(model.getVariablesDefinitions(), variable));
            combinedContent.add(""); // Leerzeile zur besseren Lesbarkeit

            // Zielfunktion hinzufügen
            List<String> relatedTerms = variableToTermsMap.get(variable);
            if (!relatedTerms.isEmpty()) {
                combinedContent.add("# Objective Function");
                String filteredObjective = String.join(" + ", relatedTerms) + ";";
                combinedContent.add("minimize obj: " + filteredObjective);
                combinedContent.add(""); // Leerzeile zur besseren Lesbarkeit
            }

            // Constraints hinzufügen
            combinedContent.add("# Constraints");
            List<String> filteredConstraints = filterConstraintsByVariable(model.getConstraints(), variable);
            combinedContent.addAll(filteredConstraints);

            // Datei speichern
            String fileName = variable + "Update" + ".ampl";
            writeToFile(outputDirectory, fileName, combinedContent);
            System.out.println("Datei erstellt: " + fileName);
        }
    }

    private static List<String> filterConstraintsByVariable(List<String> constraints, String variable) {
        List<String> filteredConstraints = new ArrayList<>();

        for (String constraint : constraints) {
            // Angepasster regulärer Ausdruck für Variablen mit Indizes
            if (constraint.matches(".*\\b" + Pattern.quote(variable) + "\\b.*") || constraint.contains(variable + "[")) {
                filteredConstraints.add(constraint);
            }
        }

        System.out.println("Filterung abgeschlossen. Anzahl gefundener Constraints für Variable '" + variable + "': " + filteredConstraints.size());
        return filteredConstraints;
    }
    
    private static void generateSlackUpdates(CentralModel model, String outputDirectory) throws IOException {
        List<String> slackContent = new ArrayList<>();

        // Header
        slackContent.add("# Slack Variable Updates");
        slackContent.add("");

        // Füge alle Slack-Variablen hinzu
        for (int i = 1; i < slackVariableCounter; i++) {
            slackContent.add("var s" + i + " >= 0;");
        }
        slackContent.add(""); // Leerzeile

        // Füge alle zugehörigen Constraints für Slack-Variablen hinzu
        slackContent.add("# Slack Variable Constraints");
        slackContent.addAll(model.getSlackConstraints()); // Stelle sicher, dass die Methode im Modell implementiert ist
        slackContent.add(""); // Leerzeile

        // Speichere die Slack-Update-Datei
        String fileName = "sUpdate.ampl";
        writeToFile(outputDirectory, fileName, slackContent);
        System.out.println("Slack-Update-Datei erstellt: " + fileName);
    }


    private static Map<String, List<String>> extractSetsAndVariables(String content) {
        Map<String, List<String>> components = new HashMap<>();
        components.put("sets", new ArrayList<>());
        components.put("variables", new ArrayList<>());

        // Sets mit oder ohne Zuweisungen extrahieren
        System.out.println("Extrahiere Sets:");
        Matcher setMatcher = Pattern.compile("\\bset\\s+[a-zA-Z_][a-zA-Z0-9_]*\\s*(:=\\s*\\{[^}]*\\})?\\s*;\\s*(#.*)?").matcher(content);
        while (setMatcher.find()) {
            String set = setMatcher.group().trim();
            components.get("sets").add(set);
            System.out.println("Gefundenes Set: " + set);
        }

        // Variablen extrahieren
        System.out.println("Extrahiere Variablen:");
        Matcher varMatcher = Pattern.compile("\\bvar\\s+[a-zA-Z_][a-zA-Z0-9_]*\\s*(\\{[^}]*\\})?\\s*(binary|>=\\s*\\d+)?\\s*;").matcher(content);
        while (varMatcher.find()) {
            String variable = varMatcher.group().trim();
            components.get("variables").add(variable);
            System.out.println("Gefundene Variable: " + variable);
        }

        // Zusammenfassung der Ergebnisse
        System.out.println("Extraktion abgeschlossen.");
        System.out.println("Gefundene Sets: " + components.get("sets").size());
        System.out.println("Gefundene Variablen: " + components.get("variables").size());

        return components;
    }



    private static List<String> extractParameters(String content) {
        List<String> parameters = new ArrayList<>();
        Matcher paramMatcher = Pattern.compile("\\bparam\\s+[^;]+;\\s*(#.*)?").matcher(content); // Erfasst Parameter mit optionalen Kommentaren
        while (paramMatcher.find()) {
            String parameter = paramMatcher.group().trim();
            if (!parameter.isEmpty()) { // Prüfe, ob die Zeile nicht leer ist
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

    private static void writeToFile(String outputDirectory, String fileName, List<String> lines) throws IOException {
        Path outputPath = Paths.get(outputDirectory, fileName);
        Files.createDirectories(outputPath.getParent());
        Files.write(outputPath, lines);
        System.out.println("Datei geschrieben: " + outputPath);
    }
    
    private static void saveCentralModel(CentralModel model, String outputPath) throws IOException {
        List<String> lines = new ArrayList<>();

        // Sets hinzufügen
        lines.add("# Sets");
        lines.addAll(model.getSets());

        // Parameters hinzufügen
        if (!model.getParameters().isEmpty()) {
            lines.addAll(model.getParameters());
            lines.add(""); // Leerzeile zur besseren Lesbarkeit
        }

        // Decision Variables hinzufügen (inkl. Slack-Variablen)
        lines.add("# Decision Variables");
        lines.addAll(model.getVariablesDefinitions());
        for (int i = 1; i < slackVariableCounter; i++) {
            lines.add("var s" + i + " >= 0;");
        }
        lines.add(""); // Leerzeile zur besseren Lesbarkeit

        // Constraints hinzufügen (inkl. `subject to` für Slack-Constraints)
        lines.add("# Constraints");
        lines.addAll(model.getConstraints());
        lines.add(""); // Leerzeile zur besseren Lesbarkeit

        // Datei schreiben
        Files.write(Paths.get(outputPath), lines);
        System.out.println("Zentrales Modell zwischengespeichert: " + outputPath);
    }

}

class CentralModel {
    private final List<String> sets;
    private final List<String> variablesDefinitions;
    private final List<String> parameters; // Ändere von Set zu List
    private final Set<String> variables;
    private List<String> constraints;
    private String objectiveFunction;
    private List<String> slackConstraints = new ArrayList<>(); 

    public CentralModel(List<String> sets, List<String> variablesDefinitions, List<String> parameters, Set<String> variables, List<String> constraints) {
        this.sets = sets;
        this.variablesDefinitions = variablesDefinitions;
        this.parameters = parameters; // Übernimmt die Liste der Parameterdefinitionen
        this.variables = variables;
        this.constraints = constraints;
    }

    public List<String> getSlackConstraints() {
        return slackConstraints;
    }

    public void setSlackConstraints(List<String> slackConstraints) {
        this.slackConstraints = slackConstraints;
    }

    public List<String> getSets() {
        return sets;
    }

    public List<String> getVariablesDefinitions() {
        return variablesDefinitions;
    }

    public List<String> getParameters() {
        return parameters; // Gibt die vollständigen Parameterdefinitionen zurück
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



