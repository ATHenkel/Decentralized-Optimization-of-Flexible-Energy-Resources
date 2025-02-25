package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.lang.acl.ACLMessage;

import java.util.ArrayList;
import java.util.List;

public class AMSAgent extends Agent {
    private static final long serialVersionUID = -4859743803908993993L;
    private List<AID> phoneBook = new ArrayList<>();
    private int totalNumberADMMAgents;

    @Override
    protected void setup() {
        // Überprüfen, ob der Parameter totalAgents beim Start übergeben wurde
        Object[] args = getArguments();
        if (args != null && args.length > 0 && args[0] instanceof Integer) {
            totalNumberADMMAgents = (Integer) args[0];
        }

        System.out.println("AMSAgent " + this.getAID() + " gestartet. Erwartete Anzahl von Agenten: " + totalNumberADMMAgents);

        // Verhalten hinzufügen, um AID-Nachrichten von ADMMAgents zu empfangen
        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            private static final long serialVersionUID = -1480562027183279172L;

            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    // Ausgabe der empfangenen Nachricht
                    System.out.println("Nachricht von " + msg.getSender().getLocalName() + " erhalten: " + msg.getContent());

                    if (msg.getContent().startsWith("register:")) {
                        // Extrahiere die Host- und Portinformationen aus der Nachricht
                        String[] contentParts = msg.getContent().substring("register:".length()).split(",");
                        if (contentParts.length == 2) {
                            String senderHost = contentParts[0];
                            String senderHttpPort = contentParts[1];

                            // Erstelle eine AID für den Agenten mit den empfangenen Adressinformationen
                            AID senderAID = new AID(msg.getSender().getLocalName() + "@" + senderHost + ":1099/JADE", AID.ISGUID);
                            senderAID.addAddresses("http://" + senderHost + ":" + senderHttpPort + "/acc");
                            phoneBook.add(senderAID);
                            System.out.println("AID von " + senderAID.getLocalName() + " erhalten und zum Telefonbuch hinzugefügt.");

                            // Prüfen, ob alle Agenten registriert sind
                            if (phoneBook.size() == totalNumberADMMAgents) {
                                System.out.println("Alle Agenten registriert. Sende Telefonbuch an alle Agenten.");
                                // Sende das vollständige Telefonbuch an alle Agenten
                                sendPhoneBookToAgents();
                            }
                        } else {
                            System.out.println("Fehler: Ungültiges Nachrichtenformat für die Registrierung.");
                        }
                    }
                } else {
                    block();
                }
            }

            // Methode zum Senden des Telefonbuchs an alle registrierten Agenten
            private void sendPhoneBookToAgents() {
                try {
                    ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
                    StringBuilder phoneBookContent = new StringBuilder();

                    // Erstelle einen String mit allen AIDs für das Telefonbuch
                    for (AID agentAID : phoneBook) {
                        String agentEntry = agentAID.getLocalName() + "," + agentAID.getAddressesArray()[0];
                        phoneBookContent.append(agentEntry).append(";");
                        reply.addReceiver(agentAID); // Füge alle Agenten als Empfänger hinzu
                    }

                    // Entferne das letzte Semikolon
                    if (phoneBookContent.length() > 0) {
                        phoneBookContent.setLength(phoneBookContent.length() - 1);
                    }

                    // Setze das Telefonbuch als Nachricht
                    reply.setContent("phoneBook:" + phoneBookContent.toString());
                    send(reply); // Sende das Telefonbuch an alle Agenten
                    System.out.println("Telefonbuch gesendet: " + phoneBookContent);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // Main-Methode für Docker und lokale Umgebungen
    public static void main(String[] args) {
        // Lade die Umgebungsvariable für die MAIN_HOST
        String mainHost = System.getenv("MAIN_HOST");

        // Set up the JADE runtime environment
        Runtime rt = Runtime.instance();

        // Create a profile for the main container
        Profile p = new ProfileImpl();
        p.setParameter(Profile.MAIN, "true");
        p.setParameter(Profile.MAIN_HOST, mainHost != null ? mainHost : "localhost"); // Verwende die Umgebungsvariable oder localhost
        p.setParameter(Profile.MAIN_PORT, "1099");

        // Create the main container
        AgentContainer mainContainer = rt.createMainContainer(p);

        try {
            // Lade totalNumberADMMAgents aus der Umgebungsvariable
            int totalNumberADMMAgents = Integer.parseInt(System.getenv("TOTAL_ADMM_AGENTS"));

            // Erstelle den AMSAgent mit der Anzahl der erwarteten ADMM-Agenten
            Object[] agentArgs = new Object[]{totalNumberADMMAgents};
            AgentController agentController = mainContainer.createNewAgent("AMSAgent", AMSAgent.class.getName(), agentArgs);
            agentController.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
