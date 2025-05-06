# Project: Multiplayer ASCII Space Invaders

This project is a networked multiplayer version of the classic Space Invaders game, rendered using ASCII characters. It features a client-server architecture allowing multiple players to join a game session.

## Table of Contents
1.  [Overview](#overview)
2.  [Technologies Used](#technologies-used)
3.  [Project Structure](#project-structure)
4.  [Client-Side Architecture](#client-side-architecture)
    *   [GamePanel.java](#gamepaneljava)
    *   [Client-Side Threading](#client-side-threading)
5.  [Server-Side Architecture](#server-side-architecture)
    *   [GameServer.java](#gameserverjava)
    *   [ClientHandler.java](#clienthandlerjava)
    *   [Server-Side Threading](#server-side-threading)
6.  [Network Communication](#network-communication)
    *   [Socket Type](#socket-type)
    *   [Data Serialization](#data-serialization)
    *   [Common Data Structures](#common-data-structures)
7.  [Game Logic Flow](#game-logic-flow)
8.  [Key Classes and Their Purpose](#key-classes-and-their-purpose)
9.  [Diagrams](#diagrams)
    *   [Component Diagram](#component-diagram)
    *   [Simplified Class Diagram](#simplified-class-diagram)
    *   [Client-Server Interaction Sequence Diagram](#client-server-interaction-sequence-diagram)
    *   [Server-Side Threading Model](#server-side-threading-model)
    *   [Client-Side Threading Model](#client-side-threading-model)

## Overview

The game allows multiple clients to connect to a central server. Players control ships, shoot at descending invaders, and try to achieve the highest score. The game state is managed by the server and synchronized across all connected clients.

## Technologies Used

*   **Java:** The core programming language for both client and server.
*   **Java Swing:** Used for the client-side graphical user interface (GUI), specifically `JPanel` for rendering the game.
*   **Java Sockets (java.net.Socket, java.net.ServerSocket):** For network communication between the client and server using TCP/IP.
*   **Java Object Serialization (java.io.ObjectInputStream, java.io.ObjectOutputStream):** For sending and receiving game state objects and player actions over the network.
*   **Java Concurrency Utilities (java.util.concurrent):**
    *   `ExecutorService` (specifically `Executors.newCachedThreadPool()` on the server) for managing client handler threads.
    *   `Thread` for the client's server listener and the server's main game loop.
    *   `ConcurrentHashMap` and `CopyOnWriteArrayList` for thread-safe data structures.
    *   `volatile` keyword for ensuring visibility of shared variables across threads.

## Project Structure

The project is organized into three main packages within the `src` directory:

*   `client/`: Contains classes specific to the client application (e.g., UI, client-side network handling).
    *   `GamePanel.java`: The main UI component, handles rendering and user input.
    *   `GameWindow.java`: The main window (`JFrame`) that hosts the `GamePanel`.
    *   `NetworkClient.java`: (Currently seems unused, networking is in `GamePanel`).
    *   `InputHandler.java`: (Currently seems unused, input handling is in `GamePanel`).
*   `server/`: Contains classes specific to the server application (e.g., connection handling, game state management).
    *   `GameServer.java`: The main server class, manages client connections and the game loop.
    *   `ClientHandler.java`: Handles communication with a single connected client.
*   `common/`: Contains classes shared between the client and server, primarily for network communication data structures.
    *   `Constants.java`: Defines shared game constants (e.g., window size, speeds, points).
    *   `GameStateUpdate.java`: Represents the game state sent from server to clients.
    *   `PlayerAction.java`: Represents actions sent from client to server.
    *   `PlayerState.java`: Represents the state of a single player.

## Client-Side Architecture

### `GamePanel.java`

This is the heart of the client application. It extends `JPanel` and is responsible for:

*   **Rendering:** Drawing the game elements (players, invaders, projectiles, barriers, UI text) using ASCII characters. It uses `Graphics` and `FontMetrics` to position elements.
*   **User Input:** Handling keyboard events (via a `KeyAdapter`) to control the player's ship (move left, move right, shoot) and UI button actions.
*   **Network Connection:**
    *   Establishing a `Socket` connection to the server.
    *   Using `ObjectOutputStream` to send `PlayerAction` objects to the server.
    *   Using `ObjectInputStream` to receive `GameStateUpdate` objects from the server.
*   **Local State Management:** Maintaining a local copy of the game state (`latestGameState`, `currentPlayers`) based on updates received from the server. This state is used for rendering.
*   **UI Components:** Managing Swing UI elements like text fields for IP/port, connection buttons, and game control buttons.

### Client-Side Threading

The `GamePanel` utilizes a dedicated thread for handling communication with the server:

*   **`ServerListener` (inner class implementing `Runnable`):**
    *   An instance of `ServerListener` runs in a separate thread (`serverListenerThread`).
    *   Its primary role is to continuously listen for incoming objects from the server via the `ObjectInputStream`.
    *   When an `Integer` is first received, it's interpreted as the `myPlayerId` assigned by the server.
    *   Subsequently, it expects `GameStateUpdate` objects. Upon receiving one, it updates the `GamePanel`'s `latestGameState` and other relevant local variables (like `currentPlayers`, score, lives).
    *   This non-blocking approach ensures that the main UI thread (Event Dispatch Thread - EDT) remains responsive and doesn't freeze while waiting for server messages. Game rendering happens on the EDT via `repaint()`, which calls `paintComponent`.

## Server-Side Architecture

### `GameServer.java`

This is the central authority of the game. Its responsibilities include:

*   **Listening for Connections:** Uses a `ServerSocket` to accept incoming client connections on a specified port.
*   **Client Management:**
    *   For each new client connection, it creates a `Socket` and instantiates a `ClientHandler` object.
    *   It assigns a unique `playerId` to each connected client.
*   **Game State Management:**
    *   Maintains the authoritative version of the game state: player positions and states (`Map<Integer, ServerPlayer>`), invader states (`List<ServerInvader>`), projectile states, barrier states, current level, and game status (running, paused, game over).
    *   Uses thread-safe collections like `ConcurrentHashMap` and `CopyOnWriteArrayList` to manage game objects due to concurrent access from the game loop and client handlers.
*   **Game Logic Loop:**
    *   Runs a main `gameLoop` in a separate thread. This loop periodically:
        1.  Updates the game state (moving invaders, projectiles, checking player actions).
        2.  Checks for collisions (player shots vs. invaders, invader shots vs. players/barriers).
        3.  Checks for game conditions (level complete, all players dead).
        4.  Creates a `GameStateUpdate` object reflecting the current state.
        5.  Broadcasts this `GameStateUpdate` to all connected clients via their respective `ClientHandler`s.
*   **Player Action Handling:**
    *   Receives `PlayerAction` objects from clients (via `ClientHandler`s).
    *   Processes these actions to update the server's authoritative game state (e.g., updating a player's movement flags, creating projectiles).
*   **Server State Machine:** Manages different server states like `WAITING` (for first player), `LOBBY` (players connected, waiting for start), `RUNNING`, `PAUSED`, and `GAME_OVER`.

### `ClientHandler.java`

Each instance of `ClientHandler` is responsible for the communication flow with a single connected client.

*   **Dedicated Thread:** Each `ClientHandler` runs in its own thread, managed by an `ExecutorService` (a cached thread pool) in `GameServer`. This allows the server to handle multiple clients concurrently without blocking.
*   **Communication Streams:** Uses `ObjectInputStream` to read `PlayerAction` objects sent by its associated client and `ObjectOutputStream` to send `GameStateUpdate` objects (and the initial `playerId`) to its client.
*   **Action Forwarding:** When a `PlayerAction` is received, it's passed to the `GameServer`'s `handlePlayerAction` method for processing.
*   **State Broadcasting:** Has a `sendGameState(GameStateUpdate gameState)` method, which is called by `GameServer` to push the latest game state to the specific client it handles. It uses `out.reset()` before flushing the stream to ensure that if mutable objects within `GameStateUpdate` are modified, their latest state is serialized and sent, rather than a cached version.
*   **Connection Lifecycle:** Manages the lifecycle of the client connection, including sending an initial `playerId` to the client upon connection and cleaning up resources upon disconnection.

### Server-Side Threading

The server employs a multi-threaded design for scalability and responsiveness:

1.  **Main Server Thread:** (Implicitly, the thread running `GameServer.start()`)
    *   Initializes the `ServerSocket`.
    *   Enters a loop to `accept()` client connections.
    *   For each connection, it creates a `ClientHandler` and submits it to an `ExecutorService`.
2.  **Client Handler Threads:**
    *   Managed by an `ExecutorService` (e.g., `Executors.newCachedThreadPool()`).
    *   Each `ClientHandler` runs in its own thread, dedicated to reading actions from and sending updates to a single client. This isolates client communication and prevents one slow client from affecting others.
3.  **Game Logic Thread:**
    *   A dedicated thread runs the `gameLoop()` method in `GameServer`.
    *   This thread is responsible for all game state updates, collision detection, and initiating the broadcast of `GameStateUpdate`s.
    *   Synchronization (`synchronized` blocks and concurrent collections) is used to ensure thread-safe access to shared game state data between the game loop and client handler threads (when processing player actions or retrieving state for broadcasting).

## Network Communication

### Socket Type

*   The project uses **TCP/IP sockets** for reliable, ordered communication.
    *   `java.net.ServerSocket` on the server to listen for and accept connections.
    *   `java.net.Socket` on the client to connect to the server, and on the server as the endpoint for a specific client connection.

### Data Serialization

*   **Java Object Serialization** is used to transmit complex Java objects over the network.
    *   Classes intended for network transfer (e.g., `GameStateUpdate`, `PlayerAction`, `PlayerState`) implement the `java.io.Serializable` interface.
    *   `java.io.ObjectOutputStream` is used to serialize these objects into a byte stream and send them.
    *   `java.io.ObjectInputStream` is used to receive the byte stream and deserialize it back into Java objects.
    *   **Why this was chosen:** Java Object Serialization is convenient for sending entire object graphs without manually converting them to and from a byte format. It simplifies the process of sending complex game state and action data.
    *   **Potential Problem:** Java Object Serialization can be verbose and has security implications if not handled carefully (though less critical in this trusted environment). It can also be sensitive to class versioning if client and server class definitions diverge significantly. For extremely high-performance or cross-language applications, other serialization formats (like Protocol Buffers, JSON, or Avro) might be preferred.

### Common Data Structures

These classes, located in the `common` package, define the contract for data exchanged between client and server. They all implement `java.io.Serializable`.

*   **`GameStateUpdate.java`:**
    *   **Purpose:** Encapsulates all the information a client needs to render the current state of the game.
    *   **Contents:** Lists of positions for invaders, player projectiles, invader projectiles; a map of player IDs to their `PlayerState`; list of barrier states (position and health); current game level; and boolean flags for game over and paused states.
    *   **Solves:** The problem of efficiently transmitting the entire dynamic game world from the server to all clients in a single, structured object.
*   **`PlayerAction.java`:**
    *   **Purpose:** Represents an action or command sent from a client to the server.
    *   **Contents:** An `ActionType` enum (e.g., `MOVE_LEFT_START`, `SHOOT`, `START_GAME`) and the `playerId` of the client performing the action.
    *   **Solves:** The problem of clients communicating their intentions or inputs to the server in a standardized way.
*   **`PlayerState.java`:**
    *   **Purpose:** Holds the specific state information for a single player.
    *   **Contents:** Player's unique ID, x/y coordinates, current score, remaining lives, and an `alive` status flag.
    *   **Solves:** The problem of representing individual player data, both within the server's game state and as part of the `GameStateUpdate` sent to clients.
*   **`Constants.java`:**
    *   **Purpose:** Centralizes various game parameters and settings.
    *   **Contents:** Game dimensions, speeds, points, sprite definitions (ASCII art), etc.
    *   **Solves:** The problem of "magic numbers" by providing named constants, making the code more readable and maintainable. Ensures consistency between client and server logic where these values are used.

## Game Logic Flow

1.  **Server Startup:** The `GameServer` starts, initializes its `ServerSocket`, and begins listening for client connections. A separate game loop thread is also started.
2.  **Client Connection:**
    *   A client (`GamePanel`) attempts to connect to the server's IP and port.
    *   If successful, the server accepts the connection, creates a `ClientHandler` for this client, assigns a unique `playerId`, and sends this ID back to the client.
    *   The client's `ServerListener` thread receives this ID.
3.  **Game Start:**
    *   Initially, the server might be in a `WAITING` or `LOBBY` state.
    *   A client can send a `START_GAME` `PlayerAction`.
    *   The server, upon receiving this, transitions to the `RUNNING` state, sets up the initial game level (invaders, barriers, player positions), and the game loop begins active updates.
4.  **Gameplay Loop (Client):**
    *   The `GamePanel`'s `ServerListener` thread continuously receives `GameStateUpdate` objects from the server.
    *   It updates the local game state variables.
    *   The Swing EDT calls `paintComponent` on `GamePanel`, which renders the game based on this latest local state.
    *   User input (keystrokes, button clicks) generates `PlayerAction` objects (e.g., `MOVE_LEFT_START`, `SHOOT`) which are sent to the server via the `ObjectOutputStream`.
5.  **Gameplay Loop (Server):**
    *   The `GameServer`'s main `gameLoop` thread:
        *   Processes any `PlayerAction`s received from clients (e.g., updating player movement flags, creating projectiles).
        *   Updates positions of invaders and projectiles.
        *   Performs collision detection.
        *   Updates scores, lives, and game conditions (level progression, game over).
        *   Constructs a new `GameStateUpdate` object.
        *   Broadcasts this `GameStateUpdate` to all connected clients via their respective `ClientHandler`s.
6.  **Game End/Pause:**
    *   The game can be paused via a `TOGGLE_PAUSE` action. The server then sends `GameStateUpdate`s with the `isPaused` flag set.
    *   The game ends when conditions are met (e.g., invaders reach the bottom, all players lose their lives). The server sets the `isGameOver` flag in `GameStateUpdate`.
7.  **Client Disconnection:** If a client disconnects (or an error occurs), its `ClientHandler` detects this, cleans up, and informs the `GameServer` to remove the client.

## Key Classes and Their Purpose

*   **`client.GamePanel`**: The main class for the client. It handles UI rendering (ASCII game view), user input (keyboard/buttons), and network communication with the server (sending actions, receiving game state). It uses a separate `ServerListener` thread to handle incoming server messages asynchronously.
    *   **Problem Solved**: Provides the user interface, captures player input, and keeps the visual representation of the game synchronized with the server's state. Manages the client-side of the network connection.
*   **`client.GamePanel.ServerListener` (inner class)**: A `Runnable` executed on a dedicated thread within the client. Its sole purpose is to listen for and process objects (`GameStateUpdate`, initial `playerId`) sent by the server.
    *   **Problem Solved**: Prevents the client's UI from freezing while waiting for network data from the server, ensuring a responsive user experience.
*   **`server.GameServer`**: The core of the server application. It listens for client connections, manages multiple `ClientHandler`s, runs the main game logic loop, maintains the authoritative game state, and broadcasts updates.
    *   **Problem Solved**: Centralizes game control, manages all connected players, enforces game rules, and ensures a consistent game world for all participants.
*   **`server.ClientHandler`**: A `Runnable` executed on a dedicated thread for each connected client. It handles all direct communication with that specific client (receiving `PlayerAction`s, sending `GameStateUpdate`s).
    *   **Problem Solved**: Allows the server to handle multiple clients concurrently and independently. Isolates network I/O for each client.
*   **`common.GameStateUpdate`**: A serializable data object that encapsulates the entire state of the game at a particular moment (player positions, scores, invader locations, projectile locations, etc.).
    *   **Problem Solved**: Provides a standardized way to transmit the complete game world from the server to all clients for rendering and local display.
*   **`common.PlayerAction`**: A serializable data object representing an action taken by a player (e.g., move, shoot, start game).
    *   **Problem Solved**: Provides a standardized format for clients to communicate their inputs and commands to the server.
*   **`common.PlayerState`**: A serializable data object representing the state of an individual player (ID, position, score, lives).
    *   **Problem Solved**: Encapsulates all relevant information about a single player for easy transmission and state management.
*   **`common.Constants`**: A utility class holding static final values for game parameters (e.g., speeds, dimensions, points).
    *   **Problem Solved**: Promotes code readability, maintainability, and consistency by centralizing configurable game values.

## Diagrams

Below are several diagrams to help visualize the project structure and interactions. These are represented using Mermaid.js syntax and should render on platforms like GitHub.

### Component Diagram

This diagram shows the high-level components and their dependencies.

![](https://i.imgur.com/qcU18ra.png)

### Simplified Class Diagram

This diagram outlines the key classes and their relationships.

![](https://i.imgur.com/iCCPNja.png)

### Client-Server Interaction Sequence Diagram

This diagram illustrates the typical communication flow between a client and the server.

![](https://i.imgur.com/07Wm3QM.png)

### Server-Side Threading Model

This diagram shows how different threads operate on the server side.

```mermaid
graph TD
    subgraph ServerProcess [Java Server Process]
        MainServerThread[Main Server Thread (ServerSocket Accept Loop)]
        GameLogicThread[Game Logic Thread (GameServer.gameLoop)]
        ExecutorService[Client Handler Thread Pool (ExecutorService)]

        subgraph ClientConnections [Individual Client Connections]
            ClientHandlerThread1[ClientHandler Thread 1]
            ClientHandlerThread2[ClientHandler Thread 2]
            ClientHandlerThreadN[ClientHandler Thread N...]
        end

        SharedGameState[Shared Game State (ConcurrentHashMap, CopyOnWriteArrayList etc.)]
    end

    MainServerThread -- Creates & Submits --> ExecutorService
    ExecutorService -- Spawns --> ClientHandlerThread1
    ExecutorService -- Spawns --> ClientHandlerThread2
    ExecutorService -- Spawns --> ClientHandlerThreadN

    ClientHandlerThread1 -- Reads/Writes --> SharedGameState
    ClientHandlerThread2 -- Reads/Writes --> SharedGameState
    ClientHandlerThreadN -- Reads/Writes --> SharedGameState
    GameLogicThread -- Reads/Writes --> SharedGameState

    GameLogicThread -- Initiates Broadcast via --> ClientHandlerThread1
    GameLogicThread -- Initiates Broadcast via --> ClientHandlerThread2
    GameLogicThread -- Initiates Broadcast via --> ClientHandlerThreadN

    ClientHandlerThread1 -- Receives PlayerAction --> GameLogicThread
    ClientHandlerThread2 -- Receives PlayerAction --> GameLogicThread
    ClientHandlerThreadN -- Receives PlayerAction --> GameLogicThread


    style ServerProcess fill:#ffe0e0,stroke:#333,stroke-width:2px
    style SharedGameState fill:#e0e0ff,stroke:#333,stroke-width:1px,color:#000
```
**Description:**
*   **Main Server Thread:** Accepts new client connections and hands them off to the `ExecutorService`.
*   **ExecutorService:** Manages a pool of threads, creating a new `ClientHandler` thread for each connected client.
*   **ClientHandler Threads:** Each thread handles I/O (reading `PlayerAction`s, writing `GameStateUpdate`s) for a single client. They interact with the `SharedGameState` (e.g., to enqueue actions).
*   **Game Logic Thread:** Runs the main `gameLoop`. It updates the `SharedGameState` based on game rules and player actions, and then triggers the `ClientHandler` threads to broadcast the new `GameStateUpdate`.
*   **Shared Game State:** Data structures (like `ConcurrentHashMap`, `CopyOnWriteArrayList`) that are accessed by multiple threads. Synchronization mechanisms are crucial here.

### Client-Side Threading Model

This diagram illustrates the threading model on the client side.

```mermaid
graph TD
    subgraph ClientProcess [Java Client Process]
        EDT[Event Dispatch Thread (Swing UI Thread)]
        ServerListenerThread[ServerListener Thread]
        LocalGameState[Local Game State (e.g., latestGameState in GamePanel)]
    end

    EDT -- Handles --> UserInput[User Input (Keyboard, Mouse)]
    UserInput -- Triggers Action --> EDT
    EDT -- Creates & Sends --> PlayerActionToServer[PlayerAction to Server]

    ServerListenerThread -- Receives --> GameStateFromServer[GameStateUpdate from Server]
    GameStateFromServer -- Updates --> LocalGameState
    LocalGameState -- Used by --> EDT # For Rendering
    EDT -- Calls --> GamePanelPaint[GamePanel.paintComponent()]


    EDT -- Starts --> ServerListenerThread

    style ClientProcess fill:#e0ffee,stroke:#333,stroke-width:2px
    style LocalGameState fill:#e0e0ff,stroke:#333,stroke-width:1px,color:#000
```
**Description:**
*   **Event Dispatch Thread (EDT):** The main thread for Swing UI operations. It handles user input, creates `PlayerAction` objects to send to the server, and is responsible for rendering the game by calling `paintComponent()` on the `GamePanel`.
*   **ServerListener Thread:** A dedicated thread that continuously listens for incoming messages (primarily `GameStateUpdate` objects) from the server.
*   **Local Game State:** Data structures within `GamePanel` (like `latestGameState`) that hold the client's copy of the game state. The `ServerListenerThread` updates this state, and the `EDT` reads from it for rendering. This separation prevents the UI from freezing while waiting for network data.

This project demonstrates a robust client-server architecture for a real-time multiplayer game, leveraging Java's networking and concurrency features effectively.
