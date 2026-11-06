package philosopherProblem;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Environment Agent acting as central resource arbiter, DF service provider, 
 * and visual message/state monitor.
 */
public class EnvironmentAgent extends Agent {
    private int numPhilosophers;
    private boolean[] forkStatus; // false = available, true = held, indexed by fork id
    private String[] philStates; // current state label per philosopher, indexed by philosopher id
    private int[] mealsCount; // meals eaten per philosopher, indexed by philosopher id
    private EnvironmentGUI gui; // null until built on the Swing EDT in setup()

    @Override
    protected void setup() {
        Object[] args = getArguments();
        numPhilosophers = (args != null && args.length >= 1) ? Integer.parseInt((String) args[0]) : 5;

        forkStatus = new boolean[numPhilosophers];
        philStates = new String[numPhilosophers];
        mealsCount = new int[numPhilosophers];

        for (int i = 0; i < numPhilosophers; i++) {
            philStates[i] = "THINKING";
        }

        // Initialize Swing GUI
        SwingUtilities.invokeLater(() -> {
            gui = new EnvironmentGUI(numPhilosophers);
            gui.setVisible(true);
        });

        // Advertise this agent so philosophers can locate it via DF.search instead of a hardcoded name
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("dining-environment-service");
        sd.setName("Fork-Arbitrator");
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
            System.out.println("[Environment] Successfully registered in DF Yellow Pages.");
        } catch (FIPAException e) {
            e.printStackTrace();
        }

        // Event-driven message processing with block() for 0% CPU usage
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = myAgent.receive();
                if (msg != null) {
                    parseMessage(msg);
                } else {
                    block();
                }
            }
        });
    }

    // Dispatches on the ':'-delimited protocol: REQUEST_FORK:<id>, RELEASE_FORK:<id>, STATUS:<id>:<state>:<meals>
    private void parseMessage(ACLMessage msg) {
        String content = msg.getContent();
        AID sender = msg.getSender();
        int performative = msg.getPerformative();
        
        // Map performative integer to string name for the readable GUI log
        String perfStr = "UNKNOWN";
        if (performative == ACLMessage.REQUEST) perfStr = "REQUEST";
        else if (performative == ACLMessage.AGREE) perfStr = "AGREE";
        else if (performative == ACLMessage.REFUSE) perfStr = "REFUSE";
        else if (performative == ACLMessage.INFORM) perfStr = "INFORM";
        else if (performative == ACLMessage.PROPOSE) perfStr = "PROPOSE";
        else if (performative == ACLMessage.CFP) perfStr = "CFP";

        // Every ACL message routed through this agent (forks, status, peer chat) is echoed to the log console
        if (gui != null) {
            String receiverName = msg.getAllReceiver().hasNext() ? ((AID) msg.getAllReceiver().next()).getLocalName() : "Environment";
            gui.logMessage("[" + sender.getLocalName() + " ➔ " + receiverName + "] (" + perfStr + "): " + content);
        }

        String[] parts = content.split(":");
        String command = parts[0];

        if (command.equals("REQUEST_FORK")) {
            int forkId = Integer.parseInt(parts[1]);
            handleForkRequest(forkId, sender);
        } else if (command.equals("RELEASE_FORK")) {
            int forkId = Integer.parseInt(parts[1]);
            handleForkRelease(forkId);
        } else if (command.equals("STATUS")) {
            int philId = Integer.parseInt(parts[1]);
            philStates[philId] = parts[2];
            mealsCount[philId] = Integer.parseInt(parts[3]);
            if (gui != null) {
                gui.updatePanel(philStates, mealsCount, forkStatus);
            }
        }
    }

    private synchronized void handleForkRequest(int forkId, AID sender) {
        ACLMessage reply = new ACLMessage();
        reply.addReceiver(sender);

        if (!forkStatus[forkId]) {
            forkStatus[forkId] = true;
            reply.setPerformative(ACLMessage.AGREE);
            reply.setContent("FORK_GRANTED:" + forkId);
        } else {
            reply.setPerformative(ACLMessage.REFUSE);
            reply.setContent("FORK_BUSY:" + forkId);
        }
        send(reply);
        if (gui != null) {
            gui.updatePanel(philStates, mealsCount, forkStatus);
        }
    }

    private synchronized void handleForkRelease(int forkId) {
        forkStatus[forkId] = false;
        if (gui != null) {
            gui.updatePanel(philStates, mealsCount, forkStatus);
        }
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this); // clean up the DF entry so stale lookups don't resolve to a dead agent
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        if (gui != null) gui.dispose();
    }

    // Swing GUI Class
    private class EnvironmentGUI extends JFrame {
        private JLabel[] statusLabels;
        private JLabel[] forkLabels;
        private JTextArea messageLogArea; // scrolling console of every ACL message seen by parseMessage

        public EnvironmentGUI(int n) {
            setTitle("JADE Dining Philosophers - DF Discovery & Message Monitor");
            setSize(850, 650);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout(10, 10));

            JPanel topPanel = new JPanel(new GridLayout(n + 1, 2, 8, 8));
            topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            statusLabels = new JLabel[n];
            forkLabels = new JLabel[n];

            topPanel.add(new JLabel("<html><b>Philosopher States</b></html>", JLabel.CENTER));
            topPanel.add(new JLabel("<html><b>Fork Status</b></html>", JLabel.CENTER));

            for (int i = 0; i < n; i++) {
                statusLabels[i] = new JLabel("Philosopher " + i + ": THINKING", JLabel.CENTER);
                statusLabels[i].setOpaque(true);
                statusLabels[i].setBackground(new Color(220, 240, 255));

                forkLabels[i] = new JLabel("Fork " + i + ": AVAILABLE", JLabel.CENTER);
                forkLabels[i].setOpaque(true);
                forkLabels[i].setBackground(new Color(230, 255, 230));

                topPanel.add(statusLabels[i]);
                topPanel.add(forkLabels[i]);
            }
            add(topPanel, BorderLayout.CENTER);

            JPanel logPanel = new JPanel(new BorderLayout());
            logPanel.setBorder(BorderFactory.createTitledBorder("Live Agent ACL Message Log (DF Discovered & Peer Chat)"));
            
            messageLogArea = new JTextArea();
            messageLogArea.setEditable(false);
            messageLogArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
            messageLogArea.setBackground(new Color(30, 30, 30));
            messageLogArea.setForeground(new Color(0, 255, 128));

            JScrollPane scrollPane = new JScrollPane(messageLogArea);
            logPanel.add(scrollPane, BorderLayout.CENTER);
            logPanel.setPreferredSize(new Dimension(850, 250));
            add(logPanel, BorderLayout.SOUTH);
        }

        // Marshals onto the EDT since callers run on the JADE agent thread; auto-scrolls to the newest line
        public void logMessage(String text) {
            SwingUtilities.invokeLater(() -> {
                String timestamp = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
                messageLogArea.append("[" + timestamp + "] " + text + "\n");
                messageLogArea.setCaretPosition(messageLogArea.getDocument().getLength());
            });
        }

        public void updatePanel(String[] states, int[] meals, boolean[] forks) {
            SwingUtilities.invokeLater(() -> {
                for (int i = 0; i < states.length; i++) {
                    statusLabels[i].setText("Philosopher " + i + ": " + states[i] + " (Meals: " + meals[i] + ")");
                    if (states[i].equals("EATING")) statusLabels[i].setBackground(new Color(255, 200, 200));
                    else if (states[i].contains("WAITING") || states[i].contains("Hungry")) 
                        statusLabels[i].setBackground(new Color(255, 255, 200));
                    else statusLabels[i].setBackground(new Color(220, 240, 255));
                }
                for (int i = 0; i < forks.length; i++) {
                    if (forks[i]) {
                        forkLabels[i].setText("Fork " + i + ": HELD");
                        forkLabels[i].setBackground(new Color(255, 220, 220));
                    } else {
                        forkLabels[i].setText("Fork " + i + ": AVAILABLE");
                        forkLabels[i].setBackground(new Color(230, 255, 230));
                    }
                }
            });
        }
    }
}