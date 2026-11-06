package philosopherProblem;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import javax.swing.JOptionPane;

/**
 * Main Class for the Dining Philosophers Problem implemented with JADE Agents.
 * Features:
 * 1. Genericity: Dynamically configures for any N philosophers (N >= 2).
 * 2. Environment Agent: Acts as arbiter, DF Yellow Pages server, and GUI host.
 * 3. Dynamic Discovery: Philosophers search the DF to locate the environment.
 * 4. Anti-Deadlock Protocol: Asymmetric fork ordering + Release-on-Fail backoff jitter.
 * 5. Zero Active Waiting: Uses block() and WakerBehaviour for event-driven execution.
 * 6. Visual Interface: Real-time Swing state monitor and live ACL message console.
 */
public class Main {
    public static void main(String[] args) {
        // Prompt for Genericity parameter N
        String input = JOptionPane.showInputDialog(
            null, 
            "Enter number of Philosophers (N >= 2):", 
            "Dining Philosophers Configuration", 
            JOptionPane.QUESTION_MESSAGE
        );

        int numPhilosophers = 5;
        if (input != null && !input.trim().isEmpty()) {
            try {
                numPhilosophers = Integer.parseInt(input.trim());
                if (numPhilosophers < 2) numPhilosophers = 5;
            } catch (NumberFormatException e) {
                numPhilosophers = 5;
            }
        }

        Runtime rt = Runtime.instance();
        Profile p = new ProfileImpl();
        p.setParameter(Profile.MAIN_HOST, "localhost");
        p.setParameter(Profile.GUI, "true"); // Enables JADE RMA management console

        AgentContainer mainContainer = rt.createMainContainer(p);

        try {
            // 1. Start Environment Agent
            Object[] envArgs = { String.valueOf(numPhilosophers) };
            AgentController envController = mainContainer.createNewAgent(
                "environment", 
                "philosopherProblem.EnvironmentAgent", 
                envArgs
            );
            envController.start();

            // Give the environment a second to register in DF before philosophers try to discover it
            Thread.sleep(500);

            // 2. Spawn N Philosopher Agents dynamically
            for (int i = 0; i < numPhilosophers; i++) {
                Object[] philArgs = {
                    String.valueOf(i),
                    String.valueOf(numPhilosophers)
                };
                
                AgentController philController = mainContainer.createNewAgent(
                    "Philosopher_" + i, 
                    "philosopherProblem.PhilosopherAgent", 
                    philArgs
                );
                philController.start();
            }

            System.out.println("System successfully deployed with " + numPhilosophers + " dining philosophers!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}