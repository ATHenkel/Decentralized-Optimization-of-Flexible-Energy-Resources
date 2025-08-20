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
        // Check if totalAgents parameter was passed at startup
        Object[] args = getArguments();
        if (args != null && args.length > 0 && args[0] instanceof Integer) {
            totalNumberADMMAgents = (Integer) args[0];
        }

        System.out.println("AMSAgent " + this.getAID() + " started. Expected number of agents: " + totalNumberADMMAgents);

        // Add behavior to receive AID messages from ADMMAgents
        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            private static final long serialVersionUID = -1480562027183279172L;

            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    // Output received message
                    System.out.println("Message received from " + msg.getSender().getLocalName() + ": " + msg.getContent());

                    if (msg.getContent().startsWith("register:")) {
                        // Extract host and port information from message
                        String[] contentParts = msg.getContent().substring("register:".length()).split(",");
                        if (contentParts.length == 2) {
                            String senderHost = contentParts[0];
                            String senderHttpPort = contentParts[1];

                            // Create AID for agent with received address information
                            AID senderAID = new AID(msg.getSender().getLocalName() + "@" + senderHost + ":1099/JADE", AID.ISGUID);
                            senderAID.addAddresses("http://" + senderHost + ":" + senderHttpPort + "/acc");
                            phoneBook.add(senderAID);
                            System.out.println("AID from " + senderAID.getLocalName() + " received and added to phone book.");

                            // Check if all agents are registered
                            if (phoneBook.size() == totalNumberADMMAgents) {
                                System.out.println("All agents registered. Sending phone book to all agents.");
                                // Send complete phone book to all agents
                                sendPhoneBookToAgents();
                            }
                        } else {
                            System.out.println("Error: Invalid message format for registration.");
                        }
                    }
                } else {
                    block();
                }
            }

            // Method to send phone book to all registered agents
            private void sendPhoneBookToAgents() {
                try {
                    ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
                    StringBuilder phoneBookContent = new StringBuilder();

                    // Create string with all AIDs for phone book
                    for (AID agentAID : phoneBook) {
                        String agentEntry = agentAID.getLocalName() + "," + agentAID.getAddressesArray()[0];
                        phoneBookContent.append(agentEntry).append(";");
                        reply.addReceiver(agentAID); // Add all agents as recipients
                    }

                    // Remove last semicolon
                    if (phoneBookContent.length() > 0) {
                        phoneBookContent.setLength(phoneBookContent.length() - 1);
                    }

                    // Set phone book as message
                    reply.setContent("phoneBook:" + phoneBookContent.toString());
                    send(reply); // Send phone book to all agents
            
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // Main method for Docker and local environments
    public static void main(String[] args) {
        // Load environment variable for MAIN_HOST
        String mainHost = System.getenv("MAIN_HOST");

        // Set up the JADE runtime environment
        Runtime rt = Runtime.instance();

        // Create a profile for the main container
        Profile p = new ProfileImpl();
        p.setParameter(Profile.MAIN, "true");
        p.setParameter(Profile.MAIN_HOST, mainHost != null ? mainHost : "localhost"); // Use environment variable or localhost
        p.setParameter(Profile.MAIN_PORT, "1099");

        // Create the main container
        AgentContainer mainContainer = rt.createMainContainer(p);

        try {
            // Load totalNumberADMMAgents from environment variable
            int totalNumberADMMAgents = Integer.parseInt(System.getenv("TOTAL_ADMM_AGENTS"));

            // Create AMSAgent with expected number of ADMM agents
            Object[] agentArgs = new Object[]{totalNumberADMMAgents};
            AgentController agentController = mainContainer.createNewAgent("AMSAgent", AMSAgent.class.getName(), agentArgs);
            agentController.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
