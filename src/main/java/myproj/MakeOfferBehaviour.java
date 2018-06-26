package myproj;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.DataStore;
import jade.core.behaviours.SequentialBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.proto.states.MsgReceiver;
import jade.util.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

public class MakeOfferBehaviour extends SequentialBehaviour {
    private Logger myLogger = Logger.getMyLogger(getClass().getName());

    private int price;
    private int origin;
    private int destination;
    private ArrayList<String> supplyChain;
    private ArrayList<String> supplyTo;
    private Offer offer;

    // Constructor
    public MakeOfferBehaviour(Agent myAgent, int price, int origin, int destination,  ArrayList<String> supplyChain, ArrayList<String> supplyTo){
        super(myAgent);
        this.price = price;
        this.origin = origin;
        this.destination = destination;
        this.supplyChain = supplyChain;
        this.supplyTo = supplyTo;
        this.offer = new Offer(price, origin);
    }

    public MakeOfferBehaviour(Agent myAgent, int price, int location){
        this(myAgent, price, location, location, new ArrayList<String>(), new ArrayList<String>());
    }

    public MakeOfferBehaviour(Agent myAgent, int price, int origin, int destination){
        this(myAgent, price, origin, destination, new ArrayList<String>(), new ArrayList<String>());
    }

    public MakeOfferBehaviour(Agent myAgent, int price, int location, ArrayList<String> supplyChain, ArrayList<String> supplyTo){
        this(myAgent, price, location, location, supplyChain, supplyTo);
    }

    public void onStart(){
        AID[] driverAgents = findAllDrivers();
        UUID uuid = UUID.randomUUID();
        final String conversationId = uuid.toString();
        ACLMessage req = new ACLMessage(ACLMessage.PROPOSE);
        req.setConversationId(conversationId);
        req.setOntology("request-delivery");
        try {
            req.setContentObject(offer);
        } catch (IOException e) {
            myLogger.log(Logger.SEVERE, "Agent " + myAgent.getLocalName() + " can't set content of propose message", e);
        }
        for (AID driverAgent : driverAgents) {
            if(!driverAgent.getName().equals(myAgent.getName())) req.addReceiver(driverAgent);
        }
        // send
        myAgent.send(req);
        myLogger.log(Logger.INFO, "Agent "+ myAgent.getLocalName() +" requests delivery for price of " + price);
        // Prepare the template to get proposals
        MessageTemplate mt = MessageTemplate.MatchConversationId(conversationId);
        DataStore s = new DataStore();
        Object msgKey = new Object();
        // wait for proposals
        myAgent.addBehaviour(new WaitForResponse(myAgent, mt, s, msgKey));
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
            myLogger.log(Logger.SEVERE, "Problem finding available drivers", fe);
        }
        return driverAgents;
    }

    // send logger info message, that driver decided where to get item from
    private void sendReadyMessageToLogger(String message){
        AID loggerAID = new AID( "logger_agent", AID.ISLOCALNAME );
        ACLMessage readyMessage = new ACLMessage(ACLMessage.INFORM);
        readyMessage.addReceiver(loggerAID);
        readyMessage.setReplyWith("agent-is-ready");
        readyMessage.setContent(message);
        myAgent.send(readyMessage);
    }

     class WaitForResponse extends MsgReceiver {
        public WaitForResponse(Agent myAgent, MessageTemplate mt, DataStore s, Object msgKey){
            super(myAgent, mt, java.lang.System.currentTimeMillis()+8000, s, msgKey);
        }

        @Override
        protected void handleMessage(ACLMessage response) {
//            myLogger.log(Logger.INFO, "Agent "+myAgent.getLocalName()+" received message " + msg);
            if (response == null){
                myLogger.log(Logger.INFO, "Agent "+myAgent.getLocalName()+" did not receive replies for the offer "+offer.toString());
                double priceStraight = price+1+CityMap.getShortestDist(origin, destination);
                double priceWithStorage = CityMap.getShortestDist(origin, CityMap.getStorageVertex())+CityMap.getShortestDist(CityMap.getStorageVertex(),destination);
                // this condition is needed for driver agents
                // for buyer agents it is always true, because priceStraight is 0
                if (priceStraight < priceWithStorage) {
                    myAgent.addBehaviour(new MakeOfferBehaviour(myAgent, price+1, origin, destination, supplyChain, supplyTo));
                } else {
                    sendReadyMessageToLogger("Agent "+myAgent.getLocalName() + " receives item from noone, because he visits storage");
                }
            } else {
                if (response.getPerformative() == ACLMessage.ACCEPT_PROPOSAL){
                    myLogger.log(Logger.INFO, "Great! Someone accepted offer from "+myAgent.getLocalName());
                    // check for cycles
                    try {
                        ArrayList<String> checkSuppliers = new ArrayList<String>((ArrayList<String>) response.getContentObject());
                        boolean isCycle = false;

                        for (String supplier:checkSuppliers) {
                            if(supplyTo.contains(supplier) || supplier.equals(myAgent.getLocalName())) {
                                isCycle=true;
                                break;
                            }
                        }
                        // don't accept is cycle takes place
                        if(!isCycle) {
                            // need to confirm, that proposal is accepted
                            ACLMessage confirm = response.createReply();
                            confirm.setPerformative(ACLMessage.CONFIRM);

                            // update info in receivers
                            ACLMessage updateRecievers = new ACLMessage(ACLMessage.INFORM);

                            try {
                                confirm.setContentObject(supplyTo);
                                myAgent.send(confirm);
                                supplyChain.addAll(checkSuppliers);
                                supplyChain.add(response.getSender().getLocalName());
                                myAgent.addBehaviour(new UpdatesRequestsServer(myAgent, response.getSender()));
                                sendReadyMessageToLogger("Agent "+myAgent.getLocalName()+" receives item from "+ response.getSender().getLocalName()+" at location "+origin+" for the price of "+price);

                                updateRecievers.setContentObject(supplyChain);
                                for (String a: supplyTo) {
                                    AID agentAID = new AID( a, AID.ISLOCALNAME );
                                    updateRecievers.addReceiver(agentAID); }

                                myAgent.send(updateRecievers);
                            } catch (IOException e) {
                                myLogger.log(Logger.SEVERE, "Cannot parse object", e);
                            }
                        } else {
                            double priceStraight = price+1+CityMap.getShortestDist(origin, destination);
                            double priceWithStorage = CityMap.getShortestDist(origin, CityMap.getStorageVertex())+CityMap.getShortestDist(CityMap.getStorageVertex(),destination);
                            if (priceStraight < priceWithStorage) {
                                myAgent.addBehaviour(new MakeOfferBehaviour(myAgent, price+1, origin, destination, supplyChain, supplyTo));
                            } else {
                                sendReadyMessageToLogger("Agent "+myAgent.getLocalName() + " receives item from noone, because he visits storage");
                            }
                        }

                    } catch (UnreadableException e) {
                        myLogger.log(Logger.SEVERE, "Agent " + myAgent.getLocalName() + " has problems accepting proposal", e);
                    }

                } else myLogger.log(Logger.INFO, "Something went wrong, received message is "+response);
            }
        }
    }

    // update information about supply chain for your receivers
    class UpdatesRequestsServer extends CyclicBehaviour {
        private AID supplier;

        public UpdatesRequestsServer(Agent myAgent, AID supplier){
            super(myAgent);
            this.supplier = supplier;
        }

        @Override
        public void action() {
            MessageTemplate propMT = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchSender(supplier));
            ACLMessage updateMessage = myAgent.receive(propMT);
            if (updateMessage != null) {
                try {
                    ArrayList<String> updateSuppliers = (ArrayList<String>) updateMessage.getContentObject();
                    supplyChain.addAll(updateSuppliers);
                } catch (UnreadableException e) {
                    myLogger.log(Logger.SEVERE, "Agent " + myAgent.getLocalName() + " can't read content of update message", e);
                }
            } else {
                block();
            }
        }
    }
}
