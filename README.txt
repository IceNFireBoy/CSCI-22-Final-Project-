================================================================
  LUMEN ARCHITECT
  CSCI 22 — Computer Science II  |  Finals Project
================================================================

AUTHORS
-------
  [YOUR FULL NAME]     — [YOUR ID NUMBER]
  [PARTNER FULL NAME]  — [PARTNER ID NUMBER]   (if applicable)

----------------------------------------------------------------
DESCRIPTION
----------------------------------------------------------------
Lumen Architect is a two-player asymmetric network game in which
one player, the Wanderer, navigates a dark platform world from
the inside using a keyboard, while the other player, the
Apprentice, observes and manipulates the world from the outside
using hand gestures captured by a webcam. The Wanderer's goal is
to locate and destroy all four hidden Cores before time runs out,
while the Apprentice uses OpenCV-powered gesture recognition to
place platforms, deploy hazards, and trigger environmental traps
to prevent the Wanderer from reaching them. Victory is decided by
whether the Wanderer destroys every Core within the allotted time,
or the Apprentice manages to exhaust the Wanderer's health or run
out the clock with at least one Core still intact.

----------------------------------------------------------------
REQUIREMENTS
----------------------------------------------------------------
  - Java JDK 17 (must be on your system PATH)
  - OpenCV 4.6.0  (jar and native libraries in lib\)
  - A webcam connected to the Apprentice's machine
  - Both machines on the same local network (or localhost for
    single-machine testing)

  Expected directory layout:
    lib\
      opencv-460.jar
      native\
        opencv_java460.dll   (Windows)
    out\          (created automatically by compile.bat)
    src\          (all Java source files)

----------------------------------------------------------------
STEP-BY-STEP RUN INSTRUCTIONS
----------------------------------------------------------------
  STEP 1 — Compile the project
  ----------------------------
  Open a Command Prompt in the project root folder and run:

      compile.bat

  Wait for the message "Compilation successful." before
  proceeding. If you see "Compilation failed", check that
  JDK 17 is installed and that lib\opencv-460.jar is present.

  STEP 2 — Start the server  (start this FIRST)
  ----------------------------------------------
  In a new Command Prompt window, run:

      run_server.bat

  The server will begin listening for client connections.
  Leave this window open for the duration of the session.

  STEP 3 — Connect the FIRST client  (this becomes the WANDERER)
  ---------------------------------------------------------------
  In another Command Prompt window, run:

      run_client.bat [server-host] [port]

  Example (same machine):
      run_client.bat localhost 5000

  The first client to connect receives the WANDERER role.
  A keyboard-controlled window will open.

  STEP 4 — Connect the SECOND client  (this becomes the APPRENTICE)
  -----------------------------------------------------------------
  In a third Command Prompt window, run:

      run_client.bat [server-host] [port]

  The second client to connect receives the APPRENTICE role.
  A webcam-overlay window will open; ensure the webcam is
  plugged in before launching.

  STEP 5 — Play
  -------------
  Once both clients are connected the server will broadcast a
  role confirmation and the match will begin automatically.
  The Wanderer acts first; the Apprentice may begin placing
  elements as soon as the preparation phase ends.

  To stop a session, close all client windows first, then
  press Ctrl+C in the server window.

----------------------------------------------------------------
WANDERER CONTROLS  (keyboard — first client)
----------------------------------------------------------------
  Movement
    A                   Move left
    D                   Move right
    SPACE               Jump

  Combat
    J                   Melee attack  (requires MELEE fragment)
    Hold K              Charge attack  (release to fire;
                        requires PROJECTILE fragment)
    L or LEFT SHIFT     Dodge roll  (requires DODGE fragment)

  Special Abilities
    E                   Light pulse  (stuns nearby DarkCrawlers;
                        requires PULSE ability)
    LEFT SHIFT          Shadow dash  (phase through short gap;
                        requires SHADOW_DASH fragment)

  System
    ESC                 Pause / unpause

----------------------------------------------------------------
APPRENTICE CONTROLS  (webcam gestures — second client)
----------------------------------------------------------------
  OPEN PALM     (all five fingers spread)
                Place a standard BRICK platform at the pointed
                location on the game canvas.

  CLOSED FIST   (all fingers curled)
                Activate or deactivate Architect-Override mode,
                bypassing budget costs for a limited time.

  POINTER       (index finger only extended)
                Select / target a specific tile or entity on
                the canvas for the next action.

  PEACE         (index + middle fingers in V shape)
                Place a SPRING platform that launches the
                Wanderer upward on contact.

  SPOCK         (Vulcan salute — index+middle split from ring+pinky)
                Activate a special barrier or shield effect
                reserved for advanced play.

  TWO OPEN      (index + middle spread wide)
                Place a SLIDE platform that causes the Wanderer
                to skid across its surface.

  FLAT          (four fingers together, thumb tucked)
                Place a WALL segment along the horizontal axis
                of the gesture's position.

  FLICK         (rapid single-direction hand motion)
                Redirect or launch a nearby DarkCrawler in the
                direction of the flick.

  NOTE: All placements consume block budget. Monitor the budget
  counter in the top-right corner of the Apprentice window.
  Budget refills partially at the start of each act.

----------------------------------------------------------------
SOURCES CITED
----------------------------------------------------------------
  Students: fill in this section with every external resource
  (websites, textbooks, tutorials, documentation, code
  snippets) that you consulted while developing this project.
  Failure to cite sources is a violation of academic integrity
  policy.

  Format each entry as:
    [#]  Author / Organisation. "Title." URL or bibliographic
         info. Date accessed.

  Example entries (replace with your actual sources):

  [1]  Oracle Corporation. "Java SE 17 API Specification."
       https://docs.oracle.com/en/java/docs/
       Accessed: [date]

  [2]  OpenCV Team. "OpenCV 4.6.0 Java Tutorials."
       https://docs.opencv.org/4.6.0/
       Accessed: [date]

  [3]  [Author]. "[Title of textbook or article]."
       [Publisher / URL], [Year].
       Accessed: [date]

  [4]  [Add additional entries as needed]

================================================================
  End of README
================================================================
