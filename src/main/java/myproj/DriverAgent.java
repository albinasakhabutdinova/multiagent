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

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;

public class DriverAgent extends Agent {
    private Logger myLogger = Logger.getMyLogger(getClass().getName());

    private int origin;
    private int destination;
    private ArrayList<Integer> pathPoints;
    private double totalPrice;
    private ArrayList<String> supplyChain;
    private ArrayList<String> supplyTo;

    // agent initialization
    @Override
    protected void setup() {
        myLogger.log(Logger.INFO,"DriverAgent " +getLocalName() + " is setting up");
        Object[] args = getArguments();
        if (args != null && args.length == 2) {
            this.origin = Integer.valueOf((String)args[0]);
            this.destination = Integer.valueOf((String)args[1]);;
        } else {
            myLogger.log(Logger.WARNING, "Not enough arguments for driver agent");
        }
        // Registration with the DF
        ServiceDescription sd = new ServiceDescription();
        sd.setType("driver-agent");
        sd.setName(getName());
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        dfd.addServices(sd);

        // register the description with the DF
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            myLogger.log(Logger.SEVERE, "Agent "+getLocalName()+" - Cannot register with DF", e);
            doDelete();
        }

        supplyChain = new ArrayList<String>();
        supplyTo = new ArrayList<String>();
        pathPoints = new ArrayList<Integer>();
        pathPoints.add(CityMap.getStorageVertex());
        pathPoints.add(destination);

        totalPrice = CityMap.getShortestDist(origin, CityMap.getStorageVertex()) + CityMap.getShortestDist(CityMap.getStorageVertex(), destination);
        // make requests
        // check if driver needs to visit storage anyway
        double priceStraight = CityMap.getShortestDist(origin, destination);
        double priceWithStorage = CityMap.getShortestDist(origin, CityMap.getStorageVertex())+CityMap.getShortestDist(CityMap.getStorageVertex(),destination);
        if (priceStraight < priceWithStorage) {
            MakeOfferBehaviour makeOfferBehaviour = new MakeOfferBehaviour(this, 0, this.origin, supplyChain, supplyTo);
            addBehaviour(makeOfferBehaviour);
        } else {
            // send logger info message, that driver decided where to get item from
            sendReadyMessageToLogger();
        }

        // listen for offer requests
        addBehaviour(new OfferRequestsServer());
        // listen for logger
        addBehaviour(new InfoRequestServer());
    }

    // agent finished planning
    protected void leave() {
        try {
            DFService.deregister( this );
            doDelete();
        }
        catch (Exception e) {
            myLogger.log(Logger.SEVERE, "Saw exception while terminating", e);
        }
    }

    // agent terminating
    @Override
    protected void takeDown() {
        // Printout a dismissal message
        myLogger.log(Logger.INFO, "Driver agent "+getName()+" terminating.");
    }

    class OfferRequestsServer extends CyclicBehaviour {

        @Override
        public void action() {
            MessageTemplate propMT = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
            ACLMessage proposeMessage = myAgent.receive(propMT);
            if (proposeMessage != null) {
                try {
                    Offer offer = (Offer) proposeMessage.getContentObject();
                    String conversationId = proposeMessage.getConversationId();
                    myLogger.log(Logger.INFO, "Agent " + myAgent.getLocalName() + " received offer " + offer.toString() + " from " + proposeMessage.getSender().getLocalName());
                    // check if the offer is good enough
                    int buyerLoc = offer.getLocation();
                    PathResult myPath  = CityMap.findShortPath(origin, destination, buyerLoc, new ArrayList<Integer>(pathPoints));
                    boolean accept = offer.getPrice()+totalPrice >= myPath.getTotalPrice();
                    if (supplyTo.contains(proposeMessage.getSender().getLocalName())) accept=false;
                    // accept or not
                    if (accept) {
                        ACLMessage reply = proposeMessage.createReply();
                        reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                        try {
                            reply.setContentObject(supplyChain);
                            myLogger.log(Logger.INFO, "Agent " + myAgent.getLocalName() + " accepted offer " + offer.toString() + " from " + proposeMessage.getSender().getLocalName());
                            myAgent.send(reply);
                            myAgent.addBehaviour(new AcceptOfferServer(buyerLoc, conversationId));
                        } catch (IOException e) {
                            myLogger.log(Logger.SEVERE, "Agent " + myAgent.getLocalName() + " can't set content of ACCEPT_PROPOSAL message", e);
                        }
                    }
                } catch (UnreadableException e) {
                    myLogger.log(Logger.SEVERE, "Agent " + myAgent.getLocalName() + " can't read content of offer message", e);
                }
            } else {
                block();
            }
        }
    }

    // wait for confirmation message from buyer by conversationId
    class AcceptOfferServer extends Behaviour {
        private int buyerLocation;
        private String conversationId;
        private boolean isDone = false;

        public AcceptOfferServer(int location, String conversationId) {
            this.buyerLocation = location;
            this.conversationId = conversationId;
        }

        @Override
        public void action() {
            MessageTemplate confMT = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.CONFIRM),
                    MessageTemplate.MatchConversationId(conversationId));
            ACLMessage confirmMessage = myAgent.receive(confMT);
            if (confirmMessage != null) {
                myLogger.log(Logger.INFO, "Agent "+myAgent.getLocalName()+" received confirmation message from "+confirmMessage.getSender().getLocalName());
                // if confirmed, add location to path
                PathResult myPath  = CityMap.findShortPath(origin, destination, buyerLocation, new ArrayList<Integer>(pathPoints));
                totalPrice = myPath.getTotalPrice();
                pathPoints = myPath.getPath();
                supplyTo.add(confirmMessage.getSender().getLocalName());
                isDone = true;
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return isDone;
        }
    }

    class InfoRequestServer extends CyclicBehaviour{

        @Override
        public void action() {
            MessageTemplate reqMT = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                    MessageTemplate.MatchOntology("request-drivers-path"));
            ACLMessage requestMessage = myAgent.receive(reqMT);
            if (requestMessage != null){
                // need to inform logger that offer is accepted
                ACLMessage infoMessage = requestMessage.createReply();
                infoMessage.setPerformative(ACLMessage.INFORM);
                infoMessage.setReplyWith("driver-information");
                pathPoints.add(0, origin);
                if(supplyChain.size()>0) pathPoints.remove(1);
                DriverInfo info = new DriverInfo(pathPoints, supplyTo);
                try {
                    infoMessage.setContentObject(info);
                    myAgent.send(infoMessage);
                } catch (IOException e) {
                    myLogger.log(Logger.SEVERE, "Agent " + myAgent.getLocalName() + " can't set content of info message", e);
                }
                myLogger.log(Logger.INFO, "Driver " + getLocalName() + " decided with plan");
                myAgent.doDelete();
            }

        }
    }

    // send logger info message, that driver decided where to get item from
    private void sendReadyMessageToLogger(){
        AID loggerAID = new AID( "logger_agent", AID.ISLOCALNAME );
        ACLMessage readyMessage = new ACLMessage(ACLMessage.INFORM);
        readyMessage.addReceiver(loggerAID);
        readyMessage.setReplyWith("agent-is-ready");
        readyMessage.setContent("Agent "+getLocalName() + " receives item from noone, because he visits storage");
        send(readyMessage);
    }

    class DriverInfo implements Serializable {
        public ArrayList<Integer> pathPoints;
        public ArrayList<String> supplyTo;

        public DriverInfo(ArrayList<Integer> pathPoints, ArrayList<String> supplyTo){
            this.pathPoints = new ArrayList<Integer>(pathPoints);
            this.supplyTo = new ArrayList<String>(supplyTo);
        }
    }
}
