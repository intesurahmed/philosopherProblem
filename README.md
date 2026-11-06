# JADE Dining Philosophers

This example implements the Dining Philosophers problem using JADE agents.
The application starts one environment agent and a configurable number of
philosopher agents in a JADE main container.

## Features

- Configurable number of philosophers (`N >= 2`).
- Central environment agent that arbitrates fork access.
- Dynamic environment discovery through the JADE Directory Facilitator (DF).
- Asymmetric fork acquisition and release-on-failure backoff to avoid
  deadlock and reduce livelock.
- Event-driven agent behavior using `block()` and `WakerBehaviour`.
- Swing monitoring window showing philosopher states, meal counts, fork
  ownership, and ACL messages.
- Informational broadcasts and neighbor-to-neighbor messages between
  philosophers.

## Source files

- `Main.java` starts the JADE runtime, environment agent, and philosopher
  agents.
- `EnvironmentAgent.java` registers the dining service, manages forks, and
  hosts the monitoring GUI.
- `PhilosopherAgent.java` discovers the environment, requests and releases
  forks, eats, thinks, and communicates with neighboring philosophers.

## Requirements

See [`requirements.txt`](requirements.txt). The repository includes
`JADE-all-4.6.0/jade/lib/jade.jar`; no separate JADE download is needed when
using the repository layout.

The application opens a Swing GUI, so it must be run in a graphical desktop
session. A headless server requires a virtual display or a different UI.

## Compile and run

From the `test` directory:

```bash
mkdir -p out
javac \
  -cp "../JADE-all-4.6.0/jade/lib/jade.jar" \
  -d out \
  src/philosopherProblem/*.java

java \
  -cp "out:../JADE-all-4.6.0/jade/lib/jade.jar" \
  philosopherProblem.Main
```

On Windows, replace the classpath separator `:` with `;`.

When the program starts, enter the desired number of philosophers in the
dialog. Canceling the dialog, entering an invalid value, or entering a value
less than 2 uses the default of 5 philosophers.

## Runtime protocol

The environment agent advertises the service type
`dining-environment-service`. Philosophers find it through DF search and send
messages using these content formats:

- `REQUEST_FORK:<fork-id>`
- `RELEASE_FORK:<fork-id>`
- `STATUS:<philosopher-id>:<state>:<meals-eaten>`

The environment replies with `FORK_GRANTED:<fork-id>` or
`FORK_BUSY:<fork-id>` using the corresponding ACL performatives.

## Stopping the application

Close the monitoring window to stop the Swing interface and terminate the
application.
