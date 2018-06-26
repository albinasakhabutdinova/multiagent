package myproj;

import jade.core.Agent;
import jade.util.Logger;
import jade.wrapper.AgentController;
import jade.wrapper.PlatformController;
import myproj.map.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class InitAgent extends Agent {
    private Logger myLogger = Logger.getMyLogger(getClass().getName());

    // setup the environment
    protected void setup() {
        // Printout a welcome message
        myLogger.log(Logger.INFO,"Init-agent "+getAID().getName()+" is ready.");

        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            // initialize map of the city
            String mapFileName = (String) args[0];
            myLogger.log(Logger.INFO, "Gonna read map from " +mapFileName);
            readMap(mapFileName);
            // initialize agents
            String envFileName = (String) args[1];
            myLogger.log(Logger.INFO, "Gonna read agents properties from " +envFileName);
            readAgents(envFileName);
        }
        else {
            // Make the agent terminate immediately
            myLogger.log(Logger.WARNING,"No file specified");
        }

        doDelete();
    }

    protected void takeDown() {
        myLogger.log(Logger.INFO, "Init Agent "+getLocalName()+" terminating");
    }

    /**
     * Read map of the city from the file
     * @param fileName
     */
    private void readMap(String fileName) {

        try {
            myLogger.log(Logger.INFO,"Reading city map configuration from file");
            Scanner inFile = new Scanner(new File(fileName));
            String s = inFile.nextLine();
            int numberOfVertices = Integer.parseInt(s);
            AdjMatrixEdgeWeightedDigraph digraph = new AdjMatrixEdgeWeightedDigraph(numberOfVertices);
            while (inFile.hasNext()) {
                s = inFile.nextLine();
                String[] edges = s.split("\\s");
                digraph.addEdge(new DirectedEdge(Integer.parseInt(edges[0]), Integer.parseInt(edges[1]), 1));
                digraph.addEdge(new DirectedEdge(Integer.parseInt(edges[1]), Integer.parseInt(edges[0]), 1));
            }

            CityMap.initMap(digraph);
            myLogger.log(Logger.INFO, "Digraph is " + digraph.toString());
            CityMap.createFWMatrix();
        } catch (FileNotFoundException e) {
            myLogger.log(Logger.SEVERE,"File not found", e);
        } catch (NumberFormatException e){
            myLogger.log(Logger.SEVERE,"Cannot read from config file, please check number formatting", e);
        } catch (Exception e) {
            myLogger.log(Logger.SEVERE,"Unknown exception", e);
        }
    }

    /**
     * Initialize agents from file configuration
     * @param fileName
     */
    private void readAgents(String fileName) {
        try {
            myLogger.log(Logger.INFO,"Reading agents configuration from file");
            Scanner inFile = new Scanner(new File(fileName));

            String s = inFile.nextLine();
            String storageVertexLabel = s;
            if (!CityMap.setStorageVertex(Integer.valueOf(storageVertexLabel))) {
                throw new Exception("Storage vertex not found");
            }
            myLogger.log(Logger.INFO,"The storage is located on vertex " + storageVertexLabel);

            s = inFile.nextLine();
            int numberOfAgents = Integer.parseInt(s);
            myLogger.log(Logger.INFO,"System has " + numberOfAgents + " agents");
            myLogger.log(Logger.INFO, "Creating agents");
            int buyerIndex = 0;
            int driverIndex = 0;
            PlatformController container = getContainerController();
            AgentController agent;
            for(int i = 0; i < numberOfAgents; i++){
                if (inFile.hasNext()) {
                    s = inFile.nextLine();
                    String[] edges = s.split("\\s");
                    Object[] args = new Object[edges.length];
                    if(edges.length == 1){
                        args[0] = edges[0];
                        String localName = "buyer_" + buyerIndex;
                        buyerIndex++;
                        myLogger.log(Logger.INFO, "Creating agent " + localName);
                        agent = container.createNewAgent(localName, "myproj.BuyerAgent", args);
                    } else if (edges.length == 2){
                        args[0] = edges[0];
                        args[1] = edges[1];
                        String localName = "driver_" + driverIndex;
                        driverIndex++;
                        myLogger.log(Logger.INFO, "Creating agent " + localName);
                        agent = container.createNewAgent(localName, "myproj.DriverAgent", args);
                    } else {
                        throw new Exception("Error! Please, check agents configuration");
                    }
                    agent.start();
                } else {
                    return;
                }
            }

            // create logger agent
            Object[] loggerArgs = new Object[2];
            loggerArgs[0] = String.valueOf(buyerIndex);
            loggerArgs[1] = String.valueOf(driverIndex);
            agent = container.createNewAgent("logger_agent", "myproj.LoggerAgent", loggerArgs);
            agent.start();

        } catch (FileNotFoundException e) {
            myLogger.log(Logger.SEVERE,"File not found", e);
        } catch (NumberFormatException e){
            myLogger.log(Logger.SEVERE,"Cannot read from config file, please check number formatting", e);
        } catch (Exception e) {
            myLogger.log(Logger.SEVERE,"Unknown exception", e);
        }

    }
}