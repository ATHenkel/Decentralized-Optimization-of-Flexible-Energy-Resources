package agents;

import behaviours.*;
import jade.core.AID;
import jade.core.Agent;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.lang.acl.ACLMessage;

import jade.core.behaviours.SequentialBehaviour;
import models.Parameters;
import models.Period;
import models.ADMMDataModel;
import models.Electrolyzer;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.gurobi.gurobi.GRBEnv;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBModel;

public class ADMMAgent extends Agent {

    private static final long serialVersionUID = 1L;

    private GRBModel model; // Instanzvariable für das GRBModel
    private Parameters parameters;
    private ADMMDataModel dataModel;
    private Workbook workbook;
    private double rho;
    private int iteration;
    private int totalNumberADMMAgents;
    private int maxIterations;
    private Set<Integer> electrolyzerIds;
    private AID amsAgentAID;  // Dynamische AID des AMSAgents

    @SuppressWarnings("unchecked")
    @Override
    protected void setup() {
        System.out.println("Agent " + getLocalName() + " startet");

        // Initialisiere ADMMDataModel
        dataModel = new ADMMDataModel();

        // Lade Argumente (Workbook, electrolyzerIds, totalNumberADMMAgents, rho, maxIterations, amsAgentAID)
        Object[] args = getArguments();
        
        // Umgebungsvariable für Perioden-Sets lesen
        String periodsEnvVar = System.getenv(getLocalName() + "_PERIODS");
        Set<Period> assignedPeriods = new HashSet<>();

        // Falls die Umgebungsvariable nicht gefunden wird, versuche, die Java-Systemeigenschaft zu verwenden
        if (periodsEnvVar == null || periodsEnvVar.isEmpty()) {
            periodsEnvVar = System.getProperty(getLocalName() + "_PERIODS");
        }

        if (periodsEnvVar != null && !periodsEnvVar.isEmpty()) {
            String[] periodStrings = periodsEnvVar.split(",");
            for (String periodStr : periodStrings) {
                try {
                    int periodValue = Integer.parseInt(periodStr.trim());
                    assignedPeriods.add(new Period(periodValue)); // Annahme: Period-Klasse hat einen int-Konstruktor
                } catch (NumberFormatException e) {
                    System.err.println("Ungültiger Periodenwert: " + periodStr);
                }
            }
        } else {
            doDelete();
            return;
        }

        
        // Setzen der Perioden im DataModel
        dataModel.setAssignedPeriods(assignedPeriods);
        
        if (args != null && args.length == 6) {
            System.out.println("Überprüfe erhaltene Argumente:");
            for (int i = 0; i < args.length; i++) {
                System.out.println("Argument " + i + ": " + args[i]);
            }

            // Werte richtig zuordnen
            workbook = (Workbook) args[0];
            electrolyzerIds = (Set<Integer>) args[1]; 
            totalNumberADMMAgents = (int) args[2];   
            rho = (double) args[3];                 
            maxIterations = (int) args[4];           
            amsAgentAID = (AID) args[5];             
        } 
        else {
            System.out.println("Fehler: Nicht alle benötigten Argumente wurden übergeben.");
            doDelete();
            return;
        }

        try {
            // Initialisiere das Gurobi Solver
            GRBEnv env = new GRBEnv(true);
            env.set("logFile", "gurobi.log");
            env.start();
            model = new GRBModel(env);
        } catch (GRBException e) {
            e.printStackTrace();
            doDelete(); // Beende den Agenten bei Fehler
            return;
        }

        // Create a SequentialBehaviour to ensure that the steps are executed one after the other
        SequentialBehaviour sequentialBehaviour = new SequentialBehaviour();

        // Step 1: Registration with AMSAgent
        sequentialBehaviour.addSubBehaviour(new RegisterWithAMSBehaviour());

        // Step 2: Receive phone book from AMS agent
        sequentialBehaviour.addSubBehaviour(new ReceivePhoneBookBehaviour());

        // Step 3: Start the optimisation process ( starts after receiving the phone book )
        sequentialBehaviour.addSubBehaviour(new StartOptimizationBehaviour());

        // Add the SequentialBehaviour to the agent
        addBehaviour(sequentialBehaviour);
    }

    // Verhalten zur Registrierung beim AMSAgent
    private class RegisterWithAMSBehaviour extends jade.core.behaviours.OneShotBehaviour {
        private static final long serialVersionUID = 6780623362396968936L;

        @Override
        public void action() {
        	// Load the environment variables for the AMS agent
            String amsAgentHost = System.getenv("AMS_AGENT_HOST");
            String amsAgentPort = System.getenv("AMS_AGENT_PORT");
            String amsAgentHttpPort = System.getenv("AMS_AGENT_HTTP_PORT");
            String agentHost = System.getenv("AGENT_HOST");
            String agentHttpPort = System.getenv("AGENT_HTTP_PORT");
            
            if (amsAgentHost == null || amsAgentPort == null || amsAgentHttpPort == null || 
                agentHost == null || agentHttpPort == null) {
                System.out.println("Fehler: Eine oder mehrere Umgebungsvariablen sind nicht gesetzt.");
            }
            
            //  If no environment variables are available, use default values
            if (amsAgentHost == null || amsAgentPort == null || amsAgentHttpPort == null) {
                amsAgentHost = "192.168.56.1";
                amsAgentPort = "1099"; // Standard-JADE-Port
                amsAgentHttpPort = "7778"; // Example HTTP-Port
                System.out.println("Lokal: Standardwerte für AMS-Agent-Registrierung werden verwendet.");
            }

            if (agentHost == null || agentHttpPort == null) {
                agentHost = "192.168.56.1"; // Standard localhost
                agentHttpPort = "7778";  // Beispiel-HTTP-Port für den Agenten
                System.out.println("Lokal: Standardwerte für Agent-Host und -Port werden verwendet.");
            }

            // Erstelle eine AID für den AMSAgent
            AID amsAgentAID = new AID("AMSAgent@" + amsAgentHost + ":" + amsAgentPort + "/JADE", AID.ISGUID);
            amsAgentAID.addAddresses("http://" + amsAgentHost + ":" + amsAgentHttpPort + "/acc");
        	
            // Sende die Registrierung an den AMSAgent mit Host- und Portinformationen
            ACLMessage registerMsg = new ACLMessage(ACLMessage.INFORM);
            String content = "register:" + agentHost + "," + agentHttpPort;
            registerMsg.setContent(content);
            registerMsg.addReceiver(amsAgentAID);
            send(registerMsg);

            System.out.println("Agent " + getLocalName() + " hat sich beim AMSAgent registriert.");
        }
    }

    // Verhalten zum Empfangen des Telefonbuchs
    private class ReceivePhoneBookBehaviour extends jade.core.behaviours.Behaviour {
        private static final long serialVersionUID = -1746952716743079101L;

        private boolean phoneBookReceived = false;

        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg != null && msg.getContent().startsWith("phoneBook:")) {
                System.out.println("Initialisiere Telefonbuch für Agent: " + myAgent.getLocalName());
                String[] agentsInfo = msg.getContent().substring("phoneBook:".length()).split(";");

                // Füge die AIDs und Adressen in das Telefonbuch des ADMMAgenten ein
                for (String agentInfo : agentsInfo) {
                    // Aufteilen in Name und Adresse anhand des Kommas
                    String[] parts = agentInfo.split(",");
                    if (parts.length == 2) {
                        String agentName = parts[0].trim();
                        String address = parts[1].trim();

                        System.out.println("AgentName im Telefonbuch: " + agentName);
                        System.out.println("Adresse im Telefonbuch: " + address);

                        // Extrahiere Host und Port aus der Adresse
                        String host = address.replaceAll("http://|/acc", "").split(":")[0];
                        String port = address.replaceAll("http://|/acc", "").split(":")[1];

                        // Erstelle den vollständigen Namen und die Adresse im gewünschten Format
                        String fullAgentName = agentName + "@" + host + ":1099/JADE";
                        String fullAddress = "http://" + host + ":" + port + "/acc";

                        // Erstelle und füge AID zum Telefonbuch hinzu
                        AID agentAID = new AID(fullAgentName, AID.ISGUID);
                        agentAID.addAddresses(fullAddress);
                        dataModel.addAID2PhoneBook(agentAID);
                    }
                }

                System.out.println("Telefonbuch für " + getLocalName() + " erhalten: " + dataModel.getPhoneBook());

                // Markiere das Telefonbuch als empfangen
                phoneBookReceived = true;
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return phoneBookReceived;
        }
    }

    // Verhalten zum Starten des Optimierungsprozesses nach dem Empfang des Telefonbuchs
    private class StartOptimizationBehaviour extends jade.core.behaviours.OneShotBehaviour {
        private static final long serialVersionUID = -3765962122765028605L;

        @Override
        public void action() {
            // Erstelle eine Sequenz von Verhaltensweisen (Behaviours)
            SequentialBehaviour admmSequentialBehaviour = new SequentialBehaviour();

            // 1. Lade die Parameter aus der Excel-Datei
            admmSequentialBehaviour.addSubBehaviour(new LoadParametersBehaviour(workbook, dataModel) {
                private static final long serialVersionUID = 1L;

                @Override
                public int onEnd() {
                    // Parameter nach dem Laden aus dem ADMMDataModel abrufen
                    parameters = dataModel.getParameters();
                    int numPeriods = parameters.getPeriods().size();

                    dataModel.initializeAllIterations(maxIterations + 1, numPeriods);
                    dataModel.setMaxIterations(maxIterations);

                    if (parameters != null) {
                        iteration = 0;
                        
                        dataModel.setAllElectrolyzers(parameters.getElectrolyzers());

                        // Starte den ADMM Zyklus und übergebe die maximale Anzahl der Iterationen
                        addBehaviour(new SWO_CyclicBehaviour(totalNumberADMMAgents, model, parameters, dataModel, filterElectrolyzers(), parameters.getPeriods(), rho, iteration, maxIterations));
                    } else {
                        System.out.println("Fehler beim Laden der Parameter.");
                        doDelete();
                    }
                    return super.onEnd();
                }
            });

            // Fügen Sie die Sequenz dem Agenten hinzu
            addBehaviour(admmSequentialBehaviour);
        }

    }

    // Filtere die Elektrolyseure basierend auf den übergebenen IDs
    private Set<Electrolyzer> filterElectrolyzers() {
        Set<Electrolyzer> allElectrolyzers = parameters.getElectrolyzers();
        Set<Electrolyzer> filteredElectrolyzers = new HashSet<>();
        for (Electrolyzer e : allElectrolyzers) {
            if (electrolyzerIds.contains(e.getId())) {
                filteredElectrolyzers.add(e);
            }
        }
        return filteredElectrolyzers;
    }

    @Override
    protected void takeDown() {
        try {
            if (model != null) {
                model.dispose();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Agent " + getLocalName() + " beendet.");
    }

    public static void main(String[] args) {
        try {
            // Lade die Umgebungsvariablen
            String mainHost = System.getenv("MAIN_HOST");
            String amsAgentName = System.getenv("AMS_AGENT_AID");
            String agentName = System.getenv("AGENT_NAME");
            int totalNumberADMMAgents = Integer.parseInt(System.getenv("TOTAL_ADMM_AGENTS"));
            int maxIterations = Integer.parseInt(System.getenv("MAX_ITERATIONS"));
            double rho = Double.parseDouble(System.getenv("RHO"));
            String[] electrolyzerIdsArray = System.getenv("ELECTROLYZER_IDS").split(",");
            Set<Integer> electrolyzerIds = new HashSet<>();
            for (String id : electrolyzerIdsArray) {
                electrolyzerIds.add(Integer.parseInt(id));
            }

            // Debugging Informationen
            System.out.println("Umgebungsvariablen:");
            System.out.println("MAIN_HOST: " + mainHost);
            System.out.println("TOTAL_ADMM_AGENTS: " + totalNumberADMMAgents);
            System.out.println("MAX_ITERATIONS: " + maxIterations);
            System.out.println("RHO: " + rho);
            System.out.println("ELECTROLYZER_IDS: " + Arrays.toString(electrolyzerIdsArray));
            System.out.println("AMS_AGENT_AID: " + amsAgentName);
            System.out.println("AGENT_NAME: " + agentName);
            
            // Verwende Umgebungsvariable für den Dateipfad
            String excelFilePath = System.getenv("EXCEL_FILE_PATH");
            if (excelFilePath == null || excelFilePath.isEmpty()) {
                excelFilePath = "/InputData.xlsx"; // Standardpfad
            }

            String agentLocalName = agentName != null ? agentName : "ADMMAgent";

            FileInputStream excelFile = null;
            Workbook workbook = null;

            try {
                // Versuche, die Excel-Datei zu öffnen
                excelFile = new FileInputStream(excelFilePath);
                System.out.println(excelFilePath + " found and loaded.");
                
                // Versuche, das Workbook zu erstellen
                workbook = new XSSFWorkbook(excelFile);
                System.out.println("Workbook erfolgreich erstellt.");
                
            } catch (FileNotFoundException e) {
                System.out.println(excelFilePath + " not found.");
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Fehler beim Laden des Workbooks.");
                e.printStackTrace();
            } finally {
                // Schließe den FileInputStream, falls er geöffnet wurde
                if (excelFile != null) {
                    try {
                        excelFile.close();
                    } catch (Exception e) {
                        System.out.println("Fehler beim Schließen der Excel-Datei.");
                        e.printStackTrace();
                    }
                }
            }

            // Überprüfe, ob das Workbook erfolgreich geladen wurde
            if (workbook == null) {
                System.out.println("Fehler: Workbook konnte nicht geladen werden. Agent wird beendet.");
                return;
            }

            // Set up the JADE runtime environment
            jade.core.Runtime rt = jade.core.Runtime.instance();
            jade.core.Profile p = new ProfileImpl();
            p.setParameter(Profile.MAIN_HOST, mainHost != null ? mainHost : "localhost");
            p.setParameter(Profile.MAIN_PORT, "1099");

            AgentContainer mainContainer = rt.createMainContainer(p);

            // Erstelle den ADMMAgent mit dem angegebenen Namen
            AID amsAgentAID = new AID(amsAgentName != null ? amsAgentName : "AMSAgent", AID.ISLOCALNAME);
            Object[] agentArgs = new Object[]{workbook, electrolyzerIds, totalNumberADMMAgents, rho, maxIterations, amsAgentAID};
            System.out.println("Agent arguments: " + Arrays.toString(agentArgs));
            AgentController agentController = mainContainer.createNewAgent(agentLocalName, ADMMAgent.class.getName(), agentArgs); 
            agentController.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
