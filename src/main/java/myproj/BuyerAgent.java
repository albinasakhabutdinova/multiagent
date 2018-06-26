package myproj;

import jade.core.AID;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.util.Logger;

public class BuyerAgent extends Agent {

    private Logger myLogger = Logger.getMyLogger(getClass().getName());
    private int location;
    private String supplierName = "noone";

    @Override
    protected void setup() {
        myLogger.log(Logger.INFO,"BuyerAgent " +getLocalName() + " is setting up");
        Object[] args = getArguments();
        if (args != null && args.length >0) {
            this.location = Integer.valueOf((String)args[0]);
        } else {
            myLogger.log(Logger.WARNING, "Not enough arguments for agent " + getLocalName());
            doDelete();
            return;
        }
        // Registration with the DF
        ServiceDescription sd = new ServiceDescription();
        sd.setType("buyer-agent");
        sd.setName(getName());
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName( getAID() );
        dfd.addServices( sd );

        // register the description with the DF
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            myLogger.log(Logger.SEVERE, "Agent "+getLocalName()+" - Cannot register with DF", e);
            doDelete();
        }

        if(this.location == CityMap.getStorageVertex()) {
            myLogger.log(Logger.INFO, "The buyer agent " + getLocalName() + " is already located at the storage, so the agent got its purchase");
            // need to inform logger that agent is already at storage point
            AID loggerAID = new AID( "logger_agent", AID.ISLOCALNAME );
            ACLMessage readyMessage = new ACLMessage(ACLMessage.INFORM);
            readyMessage.addReceiver(loggerAID);
            readyMessage.setReplyWith("agent-is-ready");
            String contentMessage = "Agent "+getLocalName()+" receives item from noone, agent is already located in storage";
            readyMessage.setContent(contentMessage);
            send(readyMessage);
            leave();
            return;
        }

        MakeOfferBehaviour makeOfferBehaviour = new MakeOfferBehaviour(this, 0, location);
        addBehaviour(makeOfferBehaviour);
        myLogger.log(Logger.INFO, "Agent " + getLocalName() + " initialized");
    }

    // agent terminating
    @Override
    protected void takeDown() {
        // Printout a dismissal message
        myLogger.log(Logger.INFO, "Buyer agent "+getName()+" terminating.");
    }


    protected void leave() {
        try {
            DFService.deregister( this );
            doDelete();
        }
        catch (Exception e) {
            myLogger.log(Logger.SEVERE, "Saw exception while terminating", e);
        }
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }
}