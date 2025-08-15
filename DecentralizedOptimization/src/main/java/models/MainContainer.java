package models;

import jade.core.AID;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import agents.ADMMAgent;
import agents.AMSAgent;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class MainContainer {

    public static void main(String[] args) {
        // Step 1: Create the JADE instance
        Runtime rt = Runtime.instance();

        // Step 2: Create a standard profile for the main container
        Profile mainProfile = new ProfileImpl();
        mainProfile.setParameter(Profile.MAIN_HOST, "localhost");
        mainProfile.setParameter(Profile.GUI, "false");

        AgentContainer mainContainer = rt.createMainContainer(mainProfile);

        try {
            // Load the Excel workbook
            FileInputStream excelFile = new FileInputStream("in/InputData.xlsx");
            Workbook workbook = new XSSFWorkbook(excelFile);

            // Set parameters
            int totalNumberADMMAgents = 4; 
            double rho = 1.37728;
            int maxIterations = 20;
            
            // Start the AMSAgent
            Object[] amsAgentArgs = new Object[]{totalNumberADMMAgents};
            AgentController amsAgent = mainContainer.createNewAgent("AMSAgent", AMSAgent.class.getName(), amsAgentArgs);
            amsAgent.start();

            // Dynamic AID of the AMSAgent
            AID amsAgentAID = new AID("AMSAgent", AID.ISLOCALNAME);

            // Distribute electrolyzer IDs
            Map<Integer, Set<Integer>> agentElectrolyzerMap = distributeElectrolyzers(totalNumberADMMAgents, 120);

            // Set periods for agents
            Map<Integer, String> periodSets = distributePeriods(totalNumberADMMAgents, 96);

            // Start agents
            for (int i = 1; i <= totalNumberADMMAgents; i++) {
                String agentName = "ADMM" + (i + 2); // ADMM3, ADMM4, ...
                Set<Integer> electrolyzerIds = agentElectrolyzerMap.get(i);
                String periods = periodSets.get(i);

                // Set system property
                System.setProperty(agentName + "_PERIODS", periods);
                System.out.println(agentName + "_PERIODS: " + System.getProperty(agentName + "_PERIODS"));

                // Create agent arguments
                Object[] agentArgs = new Object[]{workbook, electrolyzerIds, totalNumberADMMAgents, rho, maxIterations, amsAgentAID};

                // Start agents
                AgentController agentController = mainContainer.createNewAgent(agentName, ADMMAgent.class.getName(), agentArgs);
                agentController.start();
            }

        } catch (StaleProxyException | IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Distributes electrolyzer IDs evenly among agents.
     */
    private static Map<Integer, Set<Integer>> distributeElectrolyzers(int numberOfAgents, int totalElectrolyzers) {
        Map<Integer, Set<Integer>> agentMap = new HashMap<>();
        int perAgent = totalElectrolyzers / numberOfAgents;
        int remainder = totalElectrolyzers % numberOfAgents;

        int currentId = 1;
        for (int i = 1; i <= numberOfAgents; i++) {
            Set<Integer> ids = new HashSet<>();
            for (int j = 0; j < perAgent; j++) {
                ids.add(currentId++);
            }
            if (remainder > 0) {
                ids.add(currentId++);
                remainder--;
            }
            agentMap.put(i, ids);
        }
        return agentMap;
    }

    /**
     * Distributes period sets evenly among agents.
     */
    private static Map<Integer, String> distributePeriods(int numberOfAgents, int totalPeriods) {
        Map<Integer, String> periodMap = new HashMap<>();
        int perAgent = totalPeriods / numberOfAgents;
        int remainder = totalPeriods % numberOfAgents;

        int currentPeriod = 1;
        for (int i = 1; i <= numberOfAgents; i++) {
            StringBuilder periods = new StringBuilder();
            for (int j = 0; j < perAgent; j++) {
                periods.append(currentPeriod++);
                if (j < perAgent - 1) periods.append(",");
            }
            if (remainder > 0) {
                periods.append(",").append(currentPeriod++);
                remainder--;
            }
            periodMap.put(i, periods.toString());
        }
        return periodMap;
    }
}
