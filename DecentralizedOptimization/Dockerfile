# Für den Raspberry Pi (auskommentieren, wenn auf einem x86-System)
FROM --platform=linux/arm64 arm64v8/openjdk:11-jdk-slim

# Für ein x86_64-System (wie Windows, Linux oder Mac mit x86-Architektur)
#FROM openjdk:11-jdk-slim

# Erstelle das Verzeichnis für die Gurobi-Lizenz und die Bibliotheken
RUN mkdir -p /opt/gurobi

# Kopiere die Gurobi-Lizenzdatei
COPY in/gurobi.lic /opt/gurobi/gurobi.lic

# Setze den Pfad zur Lizenzdatei
ENV GRB_LICENSE_FILE="/opt/gurobi/gurobi.lic"

# Kopiere die Gurobi-Dateien (JAR und .so-Dateien)
COPY in/gurobi.jar /opt/gurobi/
COPY in/libgurobi.so.11.0.3 /opt/gurobi/lib/
COPY in/libGurobiJni110.so /opt/gurobi/lib/

# Erstelle eine symbolische Verknüpfung für libgurobi110.so
RUN ln -s /opt/gurobi/lib/libgurobi.so.11.0.3 /opt/gurobi/lib/libgurobi110.so

# Setze den Pfad für die Bibliotheken
ENV LD_LIBRARY_PATH="/opt/gurobi/lib:$LD_LIBRARY_PATH"

# Erstelle Arbeitsverzeichnisse
WORKDIR /JavaApp

# Kopiere die Java-Dateien und die Bibliotheken
COPY src/main/java/agents /JavaApp/src/main/java/agents
COPY src/main/java/behaviours /JavaApp/src/main/java/behaviours
COPY src/main/java/models /JavaApp/src/main/java/models
COPY lib/jade.jar /JavaApp/libs/
COPY target/libs /JavaApp/libs/

# Kopiere die statische InputData.xlsx Datei
#COPY in/InputData.xlsx /InputData.xlsx

# Erstelle das Verzeichnis für die kompilierten Klassen
RUN mkdir -p /JavaApp/bin

# Kompiliere spezifische Java-Dateien und speichere die kompilierte Klasse im "bin"-Verzeichnis
RUN javac -cp "/JavaApp/libs/*" -d /JavaApp/bin \
    /JavaApp/src/main/java/agents/ADMMAgent.java \
	/JavaApp/src/main/java/behaviours/SWO_SUpdateBehaviour.java \
	/JavaApp/src/main/java/behaviours/SWO_XUpdateBehaviour.java \
	/JavaApp/src/main/java/behaviours/SWO_YUpdateBehaviour.java \
    /JavaApp/src/main/java/behaviours/SWO_CyclicBehaviour.java \
	/JavaApp/src/main/java/behaviours/SWO_DualUpdateBehaviour.java \
	/JavaApp/src/main/java/behaviours/RTO_SUpdateBehaviour.java \
	/JavaApp/src/main/java/behaviours/RTO_XUpdateBehaviour.java \
	/JavaApp/src/main/java/behaviours/RTO_CyclicBehaviour.java \
	/JavaApp/src/main/java/behaviours/RTO_DualUpdateBehaviour.java \
	/JavaApp/src/main/java/behaviours/LoadParametersBehaviour.java \
    /JavaApp/src/main/java/models/ADMMDataModel.java \
	/JavaApp/src/main/java/models/Electrolyzer.java \
	/JavaApp/src/main/java/models/Parameters.java \
	/JavaApp/src/main/java/models/Period.java \
	/JavaApp/src/main/java/models/State.java 
	
# Setze das Arbeitsverzeichnis auf das "bin"-Verzeichnis
WORKDIR /JavaApp/bin

# Führe die Agents aus
CMD ["java", "-cp", ".:/JavaApp/libs/*", "agents.ADMMAgent"]
