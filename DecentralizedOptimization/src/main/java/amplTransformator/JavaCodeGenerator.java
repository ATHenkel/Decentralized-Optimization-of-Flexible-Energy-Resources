package amplTransformator;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class JavaCodeGenerator {

    public static void generateJavaCodeForGurobi(String inputDirectory, String outputDirectory) throws IOException {
        // Erstelle das Ausgabeverzeichnis, falls es nicht existiert
        Files.createDirectories(Paths.get(outputDirectory));

        // Lese alle Update-Dateien aus dem Eingabeverzeichnis
        File dir = new File(inputDirectory);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".ampl"));
        
        if (files == null || files.length == 0) {
            System.out.println("Keine Update-Dateien gefunden im Verzeichnis: " + inputDirectory);
            return;
        }

        for (File file : files) {
            // Lese den Inhalt der Update-Datei
            List<String> lines = Files.readAllLines(file.toPath());

            // Generiere Java-Code basierend auf dem Inhalt der Datei
            String javaCode = createGurobiJavaCode(lines);

            // Speichere die generierte Java-Datei
            String fileName = file.getName().replace(".ampl", ".java");
            Path outputPath = Paths.get(outputDirectory, fileName);
            Files.write(outputPath, javaCode.getBytes());

            System.out.println("Java-Datei erstellt: " + outputPath);
        }
    }

    private static String createGurobiJavaCode(List<String> amplLines) {
        StringBuilder javaCode = new StringBuilder();

        // Paket- und Import-Anweisungen
        javaCode.append("package generatedModels;\n\n");
        javaCode.append("import gurobi.*;\n\n");

        // Klassen-Definition
        javaCode.append("public class GeneratedModel {");
        javaCode.append("\n\n    public static void main(String[] args) {");
        javaCode.append("\n        try {");
        javaCode.append("\n            GRBEnv env = new GRBEnv();");
        javaCode.append("\n            GRBModel model = new GRBModel(env);\n\n");

        // Parsing der AMPL-Dateien für Sets, Parameter, Variablen und Constraints
        List<String> sets = new ArrayList<>();
        Map<String, List<String>> setElements = new HashMap<>();

        for (String line : amplLines) {
            line = line.trim();
            if (line.startsWith("set")) {
                // Sets definieren
                parseSet(line, sets, setElements);
            } else if (line.startsWith("param")) {
                // Parameter definieren
                javaCode.append(parseParameter(line));
            } else if (line.startsWith("var")) {
                // Variablen definieren
                javaCode.append(parseVariable(line, sets, setElements));
            } else if (line.startsWith("subject to")) {
                // Constraints definieren
                javaCode.append(parseConstraint(line, sets, setElements));
            } else if (line.startsWith("minimize")) {
                // Zielfunktion definieren
                javaCode.append(parseObjectiveFunction(line, sets, setElements));
            }
        }

        // Abschluss des Modells
        javaCode.append("\n            model.optimize();");
        javaCode.append("\n            model.dispose();");
        javaCode.append("\n            env.dispose();\n");
        javaCode.append("        } catch (GRBException e) {");
        javaCode.append("\n            System.out.println(\"Error: \" + e.getMessage());");
        javaCode.append("\n        }");
        javaCode.append("\n    }");
        javaCode.append("\n}");

        return javaCode.toString();
    }

    private static void parseSet(String line, List<String> sets, Map<String, List<String>> setElements) {
        // Beispiel: set I := 1 2 3;
        String[] parts = line.split("[:=]");
        String setName = parts[0].replace("set", "").trim();
        sets.add(setName);

        if (parts.length > 1) {
            String[] elements = parts[1].replace(";", "").trim().split(" ");
            setElements.put(setName, Arrays.asList(elements));
        } else {
            setElements.put(setName, new ArrayList<>());
        }
    }

    private static String parseParameter(String line) {
        // Beispiel: param a := 10;
        String[] parts = line.split("[:=]");
        String paramName = parts[0].replace("param", "").trim();
        String paramValue = parts.length > 1 ? parts[1].replace(";", "").trim() : "0";

        return "            double " + paramName + " = " + paramValue + ";\n";
    }

    private static String parseVariable(String line, List<String> sets, Map<String, List<String>> setElements) {
        // Beispiel: var x {i in I} >= 0;
        String[] parts = line.split(" ");
        String varName = parts[1];
        String setName = null;
        
        if (line.contains("{")) {
            setName = line.split("\\{")[1].split(" in ")[1].split("\\}")[0].trim();
        }

        StringBuilder code = new StringBuilder();
        if (setName != null && setElements.containsKey(setName)) {
            for (String element : setElements.get(setName)) {
                code.append("            GRBVar ").append(varName).append("_").append(element)
                    .append(" = model.addVar(0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, \"")
                    .append(varName).append("_").append(element).append("\");\n");
            }
        } else {
            code.append("            GRBVar ").append(varName)
                .append(" = model.addVar(0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, \"")
                .append(varName).append("\");\n");
        }

        return code.toString();
    }

    private static String parseConstraint(String line, List<String> sets, Map<String, List<String>> setElements) {
        // Beispiel: subject to c1: sum {i in I} x[i] <= 10;
        String constraintBody = line.split(":")[1].replace(";", "").trim();
        StringBuilder code = new StringBuilder();

        if (constraintBody.contains("sum")) {
            String setName = constraintBody.split("\\{")[1].split(" in ")[1].split("\\}")[0].trim();
            String expression = constraintBody.split("\\}")[1].trim();

            if (setElements.containsKey(setName)) {
                code.append("            model.addConstr(");
                for (String element : setElements.get(setName)) {
                    expression = expression.replace("[" + setName + "]", "_" + element);
                    code.append(expression).append(" + ");
                }
                code.setLength(code.length() - 3); // Entferne das letzte " + "
                code.append(", \"constraint\");\n");
            }
        } else {
            code.append("            model.addConstr(").append(parseExpression(constraintBody)).append(", \"constraint\");\n");
        }

        return code.toString();
    }

    private static String parseObjectiveFunction(String line, List<String> sets, Map<String, List<String>> setElements) {
        // Beispiel: minimize obj: sum {i in I} x[i];
        String objectiveBody = line.split(":")[1].replace(";", "").trim();
        StringBuilder code = new StringBuilder();

        if (objectiveBody.contains("sum")) {
            String setName = objectiveBody.split("\\{")[1].split(" in ")[1].split("\\}")[0].trim();
            String expression = objectiveBody.split("\\}")[1].trim();

            code.append("            GRBLinExpr objective = new GRBLinExpr();\n");

            if (setElements.containsKey(setName)) {
                for (String element : setElements.get(setName)) {
                    expression = expression.replace("[" + setName + "]", "_" + element);
                    code.append("            objective.addTerm(1.0, ").append(expression).append(");\n");
                }
            }

            code.append("            model.setObjective(objective, GRB.MINIMIZE);\n");
        } else {
            code.append("            model.setObjective(").append(parseExpression(objectiveBody)).append(", GRB.MINIMIZE);\n");
        }

        return code.toString();
    }

    private static String parseExpression(String expression) {
        // Beispiel: x + y <= 10 -> "x + y", GRB.LESS_EQUAL, 10
        if (expression.contains("<=")) {
            String[] parts = expression.split("<=");
            return parts[0].trim() + ", GRB.LESS_EQUAL, " + parts[1].trim();
        } else if (expression.contains(">=")) {
            String[] parts = expression.split(">=");
            return parts[0].trim() + ", GRB.GREATER_EQUAL, " + parts[1].trim();
        } else if (expression.contains("=")) {
            String[] parts = expression.split("=");
            return parts[0].trim() + ", GRB.EQUAL, " + parts[1].trim();
        }
        return expression;
    }

    public static void main(String[] args) {
        try {
            String inputDirectory = "D:\\Dokumente\\OneDrive - Helmut-Schmidt-Universität\\08_Veröffentlichungen\\2024\\Applied Sciences\\Optimierungsmodell\\Output";
            String outputDirectory = "D:\\Dokumente\\OneDrive - Helmut-Schmidt-Universität\\08_Veröffentlichungen\\2024\\Applied Sciences\\Optimierungsmodell\\Output";
            generateJavaCodeForGurobi(inputDirectory, outputDirectory);
        } catch (IOException e) {
            System.err.println("Fehler: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
