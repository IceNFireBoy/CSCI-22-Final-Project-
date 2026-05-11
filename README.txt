================================================================
  LUMEN ARCHITECT
  CSCI 22 - Introduction to Programming II  |  Final Project
================================================================

AUTHORS
-------
  [YOUR FULL NAME]     - [YOUR ID NUMBER]
  [PARTNER FULL NAME]  - [PARTNER ID NUMBER]   (if applicable)

----------------------------------------------------------------
DESCRIPTION
----------------------------------------------------------------
Lumen Architect is a two-player asymmetric multiplayer network
game played across two machines on the same local network (or
two terminals on one machine for testing).

  - The WANDERER explores a dark side-scrolling platform world
    using the keyboard. Their goal is to traverse three Acts of
    increasing difficulty, collect lore fragments that unlock
    new abilities, and finally enter the Boss arena to destroy
    all four Cores before time runs out or their health drops
    to zero.

  - The APPRENTICE plays from above using the mouse. They place
    and remove platforms, control the radius and intensity of
    the light source that the Wanderer relies on to see, and
    during the Boss fight they choose between five different
    boss attack patterns to challenge the Wanderer.

The two roles cooperate against the level design and the boss
itself: the Apprentice cannot win without the Wanderer reaching
the cores, and the Wanderer cannot survive without the
Apprentice's light and platforms.

The game runs at 60 frames per second with a server-authoritative
network model: GameServer is the single source of truth for all
shared state (Core health, victory conditions, fragment
collection, boss attack cooldowns) and broadcasts state packets
to both clients every 16 ms.

----------------------------------------------------------------
REQUIREMENTS
----------------------------------------------------------------
  - Java JDK 17 (must be on your system PATH)
  - No external libraries; no JARs to install
  - Both machines on the same local network (or use localhost
    for single-machine testing)

  Expected directory layout:
    *.java               (all source files at project root)
    resources/sprites/   (PNG sprite assets, organised by entity)
    README.txt           (this file)
    Manual-LumenArchitect.pdf  (the player-facing game manual)

----------------------------------------------------------------
HOW TO COMPILE
----------------------------------------------------------------
  Open a Command Prompt / Terminal in the project root folder
  and run:

      javac -Xlint:none *.java

  This compiles all .java files in place. No build tool, no IDE,
  no .class subdirectory is required. The -Xlint:none flag
  silences benign style warnings; the program does not depend
  on it.

----------------------------------------------------------------
HOW TO RUN
----------------------------------------------------------------
  STEP 1 - Start the server (always start this FIRST)
  -----------------------------------------------------
  In the project root, run:

      java GameServer

  The server listens on port 9876 and waits for two clients to
  connect. The server window prints "Waiting for clients..."
  followed by per-client connection messages.

  STEP 2 - Start the first client
  -------------------------------
  In a second terminal (same machine OK for testing), run:

      java GameStarter

  When the client window appears, type the server's IP address
  in the "Enter server IP..." text field and click CONNECT.
  Use "localhost" or "127.0.0.1" if the server is on the same
  machine. Then in the lobby screen, click the WANDERER card
  and click READY.

  STEP 3 - Start the second client
  --------------------------------
  In a third terminal (or on a second machine on the same LAN),
  run:

      java GameStarter

  Connect to the same server IP, then in the lobby click the
  APPRENTICE card and click READY.

  Once both players are READY, the game starts automatically.

  STEP 4 - Quit
  -------------
  Each client window has a Quit button on the in-game pause
  menu (press ESC). The server window can be closed with
  Ctrl+C in its terminal.

----------------------------------------------------------------
WANDERER CONTROLS  (keyboard - first client)
----------------------------------------------------------------
  Movement
    A or LEFT          Move left
    D or RIGHT         Move right
    SPACE or W         Jump

  Combat
    J                  Melee attack         (requires MELEE fragment)
    Hold K, release    Charge projectile    (requires PROJECTILE fragment)
    L                  Dodge roll           (requires DODGE fragment)

  Special abilities
    E                  Light pulse / level-ready signal
    SHIFT              Shadow dash          (requires SHADOW_DASH fragment)
    SHIFT (hold 2 s)   Radiant Collapse     (BOSS phase only;
                                             full-arena reveal)

  System
    ESC                Pause / unpause menu
    F                  Open / close the Fragment Library overlay
    TAB                (Apprentice only) cycle block type

----------------------------------------------------------------
APPRENTICE CONTROLS  (mouse - second client)
----------------------------------------------------------------
  Mouse motion          Move the light source
  Left click            Place a block of the currently selected
                        type at the cursor position
  Right click           Remove the block under the cursor
  Mouse scroll wheel    Adjust the light radius (smaller / larger)
  TAB                   Cycle through available block types
                        (BRICK, SLIDE, SPRING, WALL, CRUMBLE,
                        INVISIBLE, MIMIC depending on level)
  E                     Signal level-ready (after Wanderer
                        completes objectives)
  Q                     Radius burst (BOSS phase)
  T                     Toggle the light on / off

  All placements consume the per-level Block Budget. Watch the
  budget counter on the HUD; it refills at the start of each Act.

----------------------------------------------------------------
GAME STRUCTURE
----------------------------------------------------------------
  Act 1            Tutorial - basic traversal, first lore fragments
  Act 2            Wall-cling traversal, breakable walls, altars
  Act 3            Shadow-dash and Iron-fragment puzzles
  Boss arena       Top-down combat against the four Cores

  Lore fragments scattered across the Acts unlock new abilities:
    MELEE, PROJECTILE, DODGE, WALL_CLING, SHADOW_DASH,
    EMBER, IRON, RADIANT_COLLAPSE, VEIL, ECHO, TETHER,
    SHADOW_STEP

----------------------------------------------------------------
SOURCES CITED
----------------------------------------------------------------
  Course materials (CSCI 22, Second Semester 2025-2026):

  [1]  Module 1a - Modifiers (CSCI 22 lecture PDF)
  [2]  Module 1b - Interfaces (CSCI 22 lecture PDF)
  [3]  Module 1c - Abstract Classes (CSCI 22 lecture PDF)
  [4]  Module 1d - Inner and Anonymous Classes (CSCI 22 lecture PDF)
  [5]  Module 2a - Event Handling (CSCI 22 lecture PDF)
  [6]  Module 2b - Debugging (CSCI 22 lecture PDF)
  [7]  Module 2c - UML Diagrams (CSCI 22 lecture PDF)
  [8]  Module 3a - Graphics in Java (CSCI 22 lecture PDF)
  [9]  Module 3b - More Graphics in Java (CSCI 22 lecture PDF)
  [10] Module 3c - Collision (CSCI 22 lecture PDF)
  [11] Module 4a - Threads (CSCI 22 lecture PDF)
  [12] Module 4b - Key Bindings (CSCI 22 lecture PDF)
  [13] Module 4c - Networking in Java (CSCI 22 lecture PDF)

  YouTube tutorials (graphics):

  [14] "Java Graphics Programming Tutorial - Shapes, Paths,
        Curves, and Transformations."
        https://youtu.be/zCiMlbu1-aQ
        Accessed: [DATE]

  [15] "Animation Introduction (Java Graphics)."
        https://youtu.be/pdtEB3R4MZI
        Accessed: [DATE]

  External technical sources (TO BE FILLED IN):
        See the Cowork research session for citations covering
        the implementation choices that go beyond the course
        modules - networking optimisation patterns, threading
        idioms, advanced graphics composition, collision
        response, and game-architecture techniques.

  [Add additional entries as needed before submission. Failure
   to cite any source will be interpreted as academic dishonesty
   per the project specification.]

================================================================
  End of README
================================================================
