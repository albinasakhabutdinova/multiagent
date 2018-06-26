package myproj;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.util.Logger;
import myproj.map.DirectedEdge;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LoggerAgent extends Agent {
    private Logger myLogger = Logger.getMyLogger(getClass().getName());
    private FileWriter writer;
    private String newLine = System.getProperty("line.separator");
    private int numBuyers;
    private int numDrivers;
    private int messagesCount;
    private int driverInfoCount;

    @Override
    protected void setup() {
        myLogger.log(Logger.INFO,"LoggerAgent " +getLocalName() + " is setting up");
        messagesCount = 0;
        driverInfoCount = 0;
        Object[] args = getArguments();
        if (args != null && args.length == 2) {
            this.numBuyers = Integer.valueOf((String)args[0]);
            this.numDrivers = Integer.valueOf((String)args[1]);
        } else {
            myLogger.log(Logger.WARNING, "Not enough arguments for logger agent");
        }
        // Registration with the DF
        ServiceDescription sd = new ServiceDescription();
        sd.setType("logger-agent");
        sd.setName(getName());
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
            writer = new FileWriter("final-plan.txt");
            writer.write("City graph: " + newLine);
            writer.write(CityMap.getCityMapString() + newLine);
            writer.write("Storage is located at: " + CityMap.getStorageVertex() + newLine);
            writer.write("Total number of agents: " + (numDrivers+numBuyers) + newLine);

        } catch (FIPAException e) {
            myLogger.log(Logger.SEVERE, "Agent "+getLocalName()+" - Cannot register with DF", e);
            doDelete();
        } catch (IOException e) {
            myLogger.log(Logger.SEVERE, "Problem writing into file", e);
        }

        // wait for information messages
        addBehaviour(new ResultRequestServer());
    }

    protected void leave() {
        try {
            DFService.deregister( this );
            doDelete();
        }
        catch (Exception e) {
            myLogger.log(Logger.SEVERE, "Problem while terminating agent "+getName(), e);
        }
    }

    // agent terminating
    @Override
    protected void takeDown() {
        // Printout a dismissal message
        myLogger.log(Logger.INFO, "Logger agent "+getAID().getName()+" terminating.");
    }

    class ResultRequestServer extends Behaviour {
        UUID uuid;
        String driversConversationId;

        @Override
        public void action() {
            // receive messages from agents that they are ready
            MessageTemplate mtReady = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchReplyWith("agent-is-ready"));
            ACLMessage msgReady = myAgent.receive(mtReady);
            if (msgReady != null) {
//                myLogger.log(Logger.INFO, "Logger agent received message: " + msgReady);
                myLogger.log(Logger.INFO, "Logger received \"ready\" message from "+msgReady.getSender().getLocalName());
                messagesCount = messagesCount + 1;
                try{
                    if (msgReady.getContent().length()>0){
                        writer.write(msgReady.getContent()+newLine);
                    }
                }
                catch (IOException e) {
                    myLogger.log(Logger.SEVERE, "Problem writing into file", e);
                }
            } else {
                block();
            }

            if (messagesCount == (numBuyers+numDrivers)){
                myLogger.log(Logger.INFO, "All agents have decided with plans, ask them for information");

                // ask drivers for path
                ACLMessage infoDriversMsg = new ACLMessage(ACLMessage.REQUEST);
                AID[] driverAgents = findAllDrivers();
                uuid = UUID.randomUUID();
                driversConversationId = uuid.toString();
                infoDriversMsg.setConversationId(driversConversationId);
                infoDriversMsg.setOntology("request-drivers-path");
                for (AID driverAgent : driverAgents) {
                    infoDriversMsg.addReceiver(driverAgent);
                }
                myAgent.send(infoDriversMsg);
                myAgent.addBehaviour(new InfoReqServer(myAgent, driversConversationId));

                // ask buyers for info

                messagesCount++;
            }
        }

        @Override
        public boolean done() {
            return messagesCount>(numBuyers+numDrivers);
        }

        // find active driver agents in system
        private AID[] findAllDrivers() {
            AID[] driverAgents = new AID[0];
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("driver-agent");
            template.addServices(sd);
            try {
                DFAgentDescription[] driversList = DFService.search(myAgent, template);
                StringBuilder driversListStr = new StringBuilder();
                driverAgents = new AID[driversList.length];
                for (int i = 0; i < driversList.length; ++i) {
                    driverAgents[i] = driversList[i].getName();
                    driversListStr.append(driversList[i].getName().getName()).append("; ");
                }
//                myLogger.log(Logger.INFO, "Found the following driver agents:" + driversListStr);
            }
            catch (FIPAException fe) {
                myLogger.log(Logger.SEVERE, "Problem while searching for agents", fe);
            }
            return driverAgents;
        }
    }

    class InfoReqServer extends CyclicBehaviour{
        private String driversConversationId;

        public InfoReqServer(Agent a, String driversConversationId) {
            super(a);
            this.driversConversationId = driversConversationId;
        }

        @Override
        public void action() {
            MessageTemplate driverInfoMT = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchConversationId(driversConversationId));
            ACLMessage driverInfoMsg = receive(driverInfoMT);
            if(driverInfoMsg!=null){
                driverInfoCount++;
//                myLogger.log(Logger.INFO, "Logger received info from "+driverInfoCount+" drivers");
//                myLogger.log(Logger.INFO, "Total drivers: "+numDrivers+" drivers");
//                myLogger.log(Logger.INFO, "driversConversationId: "+driversConversationId);

                try {
                    DriverAgent.DriverInfo info = (DriverAgent.DriverInfo) driverInfoMsg.getContentObject();
                    ArrayList<Integer> pathPoints = info.pathPoints;
                    // write information into file
                    List<DirectedEdge> fullPath = new ArrayList<DirectedEdge>();
                    List<Integer> fullPathArray = new ArrayList<Integer>();
                    for(int i=0; i<pathPoints.size()-1; i++){
                        CityMap.getShortestPath(pathPoints.get(i), pathPoints.get(i+1)).forEach(fullPath::add);
                    }
                    for (DirectedEdge edge : fullPath) {
                        fullPathArray.add(edge.from());
                    }
                    fullPathArray.add(fullPath.get(fullPath.size()-1).to());
                    String output = "Driver " + driverInfoMsg.getSender().getLocalName() + ": path is " + fullPathArray + newLine;
                    if (info.supplyTo.size()>0){
                        output=output+"Driver "+driverInfoMsg.getSender().getLocalName()+" supplies agents: "+info.supplyTo+newLine;
                    }
                    writer.write(output);
                } catch (UnreadableException | IOException | NullPointerException e){
                    myLogger.log(Logger.SEVERE, "Logger agent caught exception", e);
                }


                if (driverInfoCount == numDrivers) {
                    try {
                        writer.close();
                    } catch (IOException e) {
                        myLogger.log(Logger.SEVERE, "Problem closing output file", e);
                    }
                    myAgent.doDelete();
                }
            } else {
                block();
            }
        }
    }

}
