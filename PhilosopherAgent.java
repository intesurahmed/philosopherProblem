package philosopherProblem;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

public class PhilosopherAgent extends Agent {
    private int id;
    private int totalPhilosophers;
    private String envName;
    
    private String state = "THINKING"; // free-text status also mirrored to the environment for the GUI
    private int mealsEaten = 0;
    
    private int firstFork; // fork requested first, order depends on parity (see setup)
    private int secondFork;
    
    private AID leftNeighbor; // adjacent philosophers, used only for peer chat, not fork arbitration
    private AID rightNeighbor;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length >= 2) {
            this.id = Integer.parseInt((String) args[0]);
            this.totalPhilosophers = Integer.parseInt((String) args[1]);
        }

        this.envName = discoverEnvironment(); // no hardcoded name; located dynamically via the DF

        int left = id;
        int right = (id + 1) % totalPhilosophers;

        // Asymmetric Ordering for deadlock/livelock prevention
        if (id % 2 == 0) {
            this.firstFork = left;
            this.secondFork = right;
        } else {
            this.firstFork = right;
            this.secondFork = left;
        }

        // Immediate neighbors for fork-related conversation
        int leftId = (id - 1 + totalPhilosophers) % totalPhilosophers;
        int rightId = (id + 1) % totalPhilosophers;
        this.leftNeighbor = new AID("Philosopher_" + leftId, AID.ISLOCALNAME);
        this.rightNeighbor = new AID("Philosopher_" + rightId, AID.ISLOCALNAME);

        addBehaviour(new ThinkBehaviour(this, getRandomTime(2000, 4000)));
        addBehaviour(new CommunicativeListener());
    }

    // Looks up the environment agent by advertised service type instead of assuming its local name
    private String discoverEnvironment() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("dining-environment-service");
        template.addServices(sd);

        try {
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0) {
                return result[0].getName().getLocalName();
            }
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        return "environment"; // fallback if DF lookup fails or returns nothing
    }

    private long getRandomTime(long min, long max) {
        return min + (long) (Math.random() * (max - min));
    }

    // Fires once after a random delay, announces thinking to everyone, then transitions to hungry
    private class ThinkBehaviour extends WakerBehaviour {
        public ThinkBehaviour(Agent a, long timeout) { super(a, timeout); }
        @Override
        protected void onWake() {
            state = "THINKING";
            updateEnvironmentState();

            // REQUIREMENT 1: Broadcast thinking status to ALL other philosophers
            broadcastThinkingToAll("I am currently thinking and resting.");

            // Transition to hungry and request fork conversation from NEIGHBORS ONLY
            myAgent.addBehaviour(new WakerBehaviour(myAgent, getRandomTime(1500, 2500)) {
                @Override
                protected void onWake() {
                    state = "HUNGRY (Seeking First Fork)";
                    updateEnvironmentState();

                    // REQUIREMENT 2: Request/discuss fork status with immediate neighbors only
                    sendNeighborForkRequest(leftNeighbor, "Neighbor, I am hungry and need my first fork (" + firstFork + ").");
                    sendNeighborForkRequest(rightNeighbor, "Neighbor, I am hungry and need my first fork (" + firstFork + ").");

                    sendEnvironmentRequest("REQUEST_FORK:" + firstFork);
                }
            });
        }
    }

    // Broadcasts general thinking state to EVERY philosopher in the system
    private void broadcastThinkingToAll(String message) {
        for (int i = 0; i < totalPhilosophers; i++) {
            if (i == id) continue;
            AID peer = new AID("Philosopher_" + i, AID.ISLOCALNAME);
            
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(peer);
            msg.setContent("THINK_BROADCAST: " + message);
            send(msg);

            sendEnvironmentLog("[BROADCAST] " + getLocalName() + " ➔ Philosopher_" + i + ": " + message);
        }
    }

    // Sends fork-related conversation exclusively to an immediate neighbor; purely informational, doesn't affect arbitration
    private void sendNeighborForkRequest(AID neighbor, String message) {
        ACLMessage msg = new ACLMessage(ACLMessage.CFP); // Call For Proposal / Fork coordination
        msg.addReceiver(neighbor);
        msg.setContent("FORK_CONVERSATION: " + message);
        send(msg);

        sendEnvironmentLog("[NEIGHBOR CHAT] " + getLocalName() + " ➔ " + neighbor.getLocalName() + ": " + message);
    }

    private void sendEnvironmentRequest(String content) {
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.addReceiver(new AID(envName, AID.ISLOCALNAME));
        msg.setContent(content);
        send(msg);
    }

    // Routes a plain-text line through the environment so peer chat also shows up in its message console
    private void sendEnvironmentLog(String text) {
        ACLMessage logMsg = new ACLMessage(ACLMessage.INFORM);
        logMsg.addReceiver(new AID(envName, AID.ISLOCALNAME));
        logMsg.setContent(text);
        send(logMsg);
    }

    // Simulates eating for a random duration, then releases both forks and starts thinking again
    private class EatBehaviour extends WakerBehaviour {
        public EatBehaviour(Agent a, long timeout) { super(a, timeout); }
        @Override
        protected void onWake() {
            mealsEaten++;
            state = "THINKING";
            updateEnvironmentState();

            sendEnvironmentRequest("RELEASE_FORK:" + firstFork);
            sendEnvironmentRequest("RELEASE_FORK:" + secondFork);

            // Notify neighbors about finishing via fork conversation
            sendNeighborForkRequest(rightNeighbor, "I finished eating. Forks released. (Meals: " + mealsEaten + ")");

            myAgent.addBehaviour(new ThinkBehaviour(myAgent, getRandomTime(2000, 4000)));
        }
    }

    // Single listener for both environment fork replies and peer chat, since messages now arrive from multiple senders
    private class CommunicativeListener extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = myAgent.receive();

            if (msg != null) {
                String senderName = msg.getSender().getLocalName();
                String content = msg.getContent();
                int performative = msg.getPerformative();

                if (senderName.equals(envName)) {
                    // Handle Environment responses for forks
                    if (performative == ACLMessage.AGREE) {
                        int acquiredFork = Integer.parseInt(content.split(":")[1]);

                        if (acquiredFork == firstFork && state.contains("First")) {
                            state = "WAITING (Seeking Second Fork)";
                            updateEnvironmentState();
                            sendEnvironmentRequest("REQUEST_FORK:" + secondFork);
                        } else if (acquiredFork == secondFork && state.contains("Second")) {
                            state = "EATING";
                            updateEnvironmentState();
                            myAgent.addBehaviour(new EatBehaviour(myAgent, getRandomTime(3000, 5000)));
                        }
                    } else if (performative == ACLMessage.REFUSE) {
                        // ANTI-DEADLOCK: release the fork already held so it can't be starved indefinitely by neighbors
                        if (state.contains("Second")) {
                            sendEnvironmentRequest("RELEASE_FORK:" + firstFork);
                            
                            // Send fork conversation/complaint to immediate neighbor only
                            sendNeighborForkRequest(rightNeighbor, "My second fork is blocked, stepping back to coordinate!");
                        }

                        state = "HUNGRY (Backing off & Retrying)";
                        updateEnvironmentState();

                        // Per-agent id offset prevents philosophers from retrying in lockstep (livelock)
                        long backoffTime = getRandomTime(1000, 2000) + (id * 60L);
                        myAgent.addBehaviour(new WakerBehaviour(myAgent, backoffTime) {
                            @Override
                            protected void onWake() {
                                state = "HUNGRY (Seeking First Fork)";
                                updateEnvironmentState();
                                sendEnvironmentRequest("REQUEST_FORK:" + firstFork);
                            }
                        });
                    }
                } else {
                    // Handle incoming messages from peers
                    if (content.startsWith("THINK_BROADCAST")) {
                        // Received a thinking broadcast from any philosopher; no action needed, just drains the mailbox
                    } else if (content.startsWith("FORK_CONVERSATION")) {
                        // Respond to neighbor regarding fork coordination
                        ACLMessage reply = new ACLMessage(ACLMessage.AGREE);
                        reply.addReceiver(msg.getSender());
                        reply.setContent("FORK_REPLY: Understood, neighbor. Coordinate well!");
                        send(reply);
                        
                        sendEnvironmentLog("[NEIGHBOR CHAT] " + getLocalName() + " ➔ " + senderName + ": Understood, neighbor!");
                    }
                }
            } else {
                block();
            }
        }
    }

    // Pushes state/meal count to the environment so its GUI stays in sync
    private void updateEnvironmentState() {
        ACLMessage statusMsg = new ACLMessage(ACLMessage.INFORM);
        statusMsg.addReceiver(new AID(envName, AID.ISLOCALNAME));
        statusMsg.setContent("STATUS:" + id + ":" + state + ":" + mealsEaten);
        send(statusMsg);
    }
}