# External Sources Cited — Lumen Architect (CSCI 22 Final Project)

This document lists authoritative citable sources for techniques used in the project that go beyond the CSCI 22 course modules. Sources are grouped by category; numbering is continuous from [1].

A handful of techniques in the original prompt are custom design choices specific to this project (per-platform-type collision response, light-gated collision, per-entity sprite fallback, the bespoke session-snapshot reconnect protocol, and the mixed string-prefix-over-ObjectStream protocol). After a good-faith search no single authoritative source teaches those exactly; they are noted explicitly under their category and skipped per the prompt's instructions.

---

## Category A — Networking Architecture & Optimization

**A1. Server-authoritative simulation.**

[1] Gambetta, Gabriel. "Fast-Paced Multiplayer (Part I): Client-Server Game Architecture." https://www.gabrielgambetta.com/client-server-game-architecture.html. Date accessed: 2026-05-12.

**A2. ObjectOutputStream.reset() to prevent handle-table memory bloat.**

[2] Oracle. "ObjectOutputStream (Java Platform SE 8) — Javadoc." https://docs.oracle.com/javase/8/docs/api/java/io/ObjectOutputStream.html. Date accessed: 2026-05-12.

[3] SEI CERT. "SER10-J. Avoid memory and resource leaks during serialization." SEI CERT Oracle Coding Standard for Java. https://wiki.sei.cmu.edu/confluence/display/java/SER10-J.+Avoid+memory+and+resource+leaks+during+serialization. Date accessed: 2026-05-12.

**A3. LinkedBlockingQueue as a thread-safe producer/consumer handoff.**

[4] Oracle. "LinkedBlockingQueue (Java Platform SE 8) — Javadoc." https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/LinkedBlockingQueue.html. Date accessed: 2026-05-12.

[5] Baeldung. "Guide to java.util.concurrent.BlockingQueue." https://www.baeldung.com/java-blocking-queue. Date accessed: 2026-05-12.

**A4. Per-tick rate throttling (sending some packets less often than the simulation tick).**

[6] Valve Developer Community. "Source Multiplayer Networking." https://developer.valvesoftware.com/wiki/Source_Multiplayer_Networking. Date accessed: 2026-05-12.

**A5. Per-attack-type cooldown timers gated by milliseconds.**

[7] GameDev.net Forums. "Cooldown timer." https://www.gamedev.net/forums/topic/509878-cooldown-timer/. Date accessed: 2026-05-12.

**A6. Session-snapshot reconnect window with replay protocol.**

The full server-snapshot-on-disconnect / hold-session-open / replay-to-reconnecting-client flow is custom to this project. The closest authoritative discussion of the underlying idea (server takes world snapshots and replays them to clients) is the Valve Source Networking page cited above as [6]. No single tutorial teaches the exact 90-second reconnect window + SNAPSHOT_BEGIN / SNAPSHOT_BLOCK / SNAPSHOT_END message protocol — skipped per the prompt's instructions.

**A7. Exponential-backoff reconnect on the client side.**

[8] Baeldung. "Better Retries with Exponential Backoff and Jitter." https://www.baeldung.com/resilience4j-backoff-jitter. Date accessed: 2026-05-12.

**A8. Mixed protocol: typed packet classes for high-volume state plus a string-prefix dispatch layer for one-off events.**

This particular hybrid is a custom architectural choice. The two ingredients (Java ObjectStreams for typed packets, and DataInput/DataOutputStream for string-prefix framing) are each taught individually in Module 4c, but no single external source teaches the specific pattern of layering both together. Skipped per the prompt's instructions.

---

## Category B — Threading Idioms Beyond the Basics

**B1. Fixed-timestep game loop with System.nanoTime() scheduling.**

[9] Fiedler, Glenn. "Fix Your Timestep!" Gaffer On Games. https://gafferongames.com/post/fix_your_timestep/. Date accessed: 2026-05-12.

**B2. The `volatile` keyword for cross-thread visibility of stop/state flags.**

[10] Oracle. "Atomic Access (The Java Tutorials > Essential Java Classes > Concurrency)." https://docs.oracle.com/javase/tutorial/essential/concurrency/atomic.html. Date accessed: 2026-05-12.

**B3. CopyOnWriteArrayList for an entity list iterated and mutated concurrently.**

[11] Oracle. "CopyOnWriteArrayList (Java Platform SE 8) — Javadoc." https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CopyOnWriteArrayList.html. Date accessed: 2026-05-12.

**B4. SwingUtilities.invokeLater for marshalling state changes onto the Event Dispatch Thread.**

[12] Oracle. "The Event Dispatch Thread (The Java Tutorials > Creating a GUI With Swing > Concurrency in Swing)." https://docs.oracle.com/javase/tutorial/uiswing/concurrency/dispatch.html. Date accessed: 2026-05-12.

---

## Category C — Graphics Composition & Rendering

**C1. RadialGradientPaint for the smooth-falloff light radius.**

[13] Oracle. "RadialGradientPaint (Java Platform SE 8) — Javadoc." https://docs.oracle.com/javase/8/docs/api/java/awt/RadialGradientPaint.html. Date accessed: 2026-05-12.

**C2. AlphaComposite.DST_OUT for cutting a transparent hole through the darkness mask.**

[14] Oracle. "Compositing Graphics (The Java Tutorials > 2D Graphics > Advanced Topics in Java 2D)." https://docs.oracle.com/javase/tutorial/2d/advanced/compositing.html. Date accessed: 2026-05-12.

[15] Oracle. "AlphaComposite (Java Platform SE 8) — Javadoc." https://docs.oracle.com/javase/8/docs/api/java/awt/AlphaComposite.html. Date accessed: 2026-05-12.

**C3. Geometric subtraction via java.awt.geom.Area (Constructive Area Geometry).**

[16] Oracle. "Area (Java Platform SE 8) — Javadoc." https://docs.oracle.com/javase/8/docs/api/java/awt/geom/Area.html. Date accessed: 2026-05-12.

**C4. Camera world-to-screen transform via Graphics2D.translate.**

[17] Oracle. "Graphics2D (Java Platform SE 7) — Javadoc, translate(int x, int y)." https://docs.oracle.com/javase/7/docs/api/java/awt/Graphics2D.html. Date accessed: 2026-05-12.

[18] Delorme, Frederic. "Game with JDK [9]: Camera, Action!" Medium. https://medium.com/@McGivrer/game-with-jdk-9-camera-action-66e2ea9af7f6. Date accessed: 2026-05-12.

**C5. RenderingHints.KEY_ANTIALIASING for smoothing shapes and text.**

[19] Oracle. "Controlling Rendering Quality (The Java Tutorials > 2D Graphics > Advanced Topics in Java 2D)." https://docs.oracle.com/javase/tutorial/2d/advanced/quality.html. Date accessed: 2026-05-12.

**C6. RescaleOp on BufferedImage for sprite tinting / damage flash.**

[20] Knudsen, Jonathan. "Using Java 2D's Image-Processing Model — RescaleOp." InformIT (excerpted from *Java 2D Graphics*, O'Reilly). https://www.informit.com/articles/article.aspx?p=1013851&seqNum=7. Date accessed: 2026-05-12.

**C7. Half-resolution off-screen BufferedImage rendered then composited at full resolution (a manual extension of the off-screen buffer pattern).**

[21] Oracle. "Double Buffering and Page Flipping (The Java Tutorials > Bonus > Full-Screen Exclusive Mode API)." https://docs.oracle.com/javase/tutorial/extra/fullscreen/doublebuf.html. Date accessed: 2026-05-12.

**C8. Polygon line-of-sight shadow projection from platforms away from the light source.**

[22] Patel, Amit. "2D Visibility." Red Blob Games. https://www.redblobgames.com/articles/visibility/. Date accessed: 2026-05-12.

---

## Category D — Collision Response

**D1. Push-out resolution along the separating axis — X then Y so the player slides along walls.**

[23] Strugar, Daniel. "Basic 2D Platformer Physics, Part 6: Object vs. Object Collision Response." Envato Tuts+. https://gamedevelopment.tutsplus.com/basic-platformer-physics-part-6-object-vs-object-collision-response--cms-27604t. Date accessed: 2026-05-12.

**D2. One-way platform collision (top-only, jump-through allowed).**

[24] GameMaker. "How to Create Jump-Through Platforms in GameMaker." https://gamemaker.io/en/tutorials/platformer-jump-through. Date accessed: 2026-05-12.

**D3. Per-platform-type collision response (SLIDE, SPRING, CRUMBLE, MIMIC, INVISIBLE, PHANTOM all with different post-collision behaviors).**

This is a custom application of the Strategy pattern to platform behaviors in this project. The general idea of giving each platform type its own collision response is widely used but no single tutorial teaches this specific six-behavior set. Skipped per the prompt's instructions.

**D4. Light-gated collision — a platform that is only solid while illuminated.**

This is a custom design choice specific to Lumen Architect, tying the platform's `lit` flag to its participation in the collision pass. After a good-faith search no external source teaches this exact mechanic. Skipped per the prompt's instructions.

---

## Category E — Game Architecture Patterns

**E1. Entity list with abstract update(deltaMs) + render(g) — the polymorphic-loop pattern.**

[25] Nystrom, Robert. "Update Method (Sequencing Patterns)." *Game Programming Patterns*. https://gameprogrammingpatterns.com/update-method.html. Date accessed: 2026-05-12.

**E2. Edge-triggered vs level-triggered input flag pattern.**

[26] Number Analytics. "Mastering Input Handling in Games." https://www.numberanalytics.com/blog/ultimate-guide-input-handling-game-development. Date accessed: 2026-05-12.

**E3. Finite state machine for player abilities (Radiant Collapse IDLE → CHARGING → ACTIVE → COOLDOWN → IDLE).**

[27] Nystrom, Robert. "State (Design Patterns Revisited)." *Game Programming Patterns*. https://gameprogrammingpatterns.com/state.html. Date accessed: 2026-05-12.

**E4. Per-entity sprite override pattern with hand-drawn Graphics2D fallback.**

The fallback approach (try the PNG; on missing path or missing file, run the entity's `drawFallback(g)` method) is a custom architectural choice. No single external source teaches this exact pattern. Skipped per the prompt's instructions.

**E5. Active-flag soft-delete on entity list to avoid ConcurrentModificationException.**

[28] Baeldung. "Avoiding the ConcurrentModificationException in Java." https://www.baeldung.com/java-concurrentmodificationexception. Date accessed: 2026-05-12.

---

## References (consolidated, in numbered order)

[1] Gambetta, Gabriel. "Fast-Paced Multiplayer (Part I): Client-Server Game Architecture." https://www.gabrielgambetta.com/client-server-game-architecture.html. Date accessed: 2026-05-12.

[2] Oracle. "ObjectOutputStream (Java Platform SE 8) — Javadoc." https://docs.oracle.com/javase/8/docs/api/java/io/ObjectOutputStream.html. Date accessed: 2026-05-12.

[3] SEI CERT. "SER10-J. Avoid memory and resource leaks during serialization." https://wiki.sei.cmu.edu/confluence/display/java/SER10-J.+Avoid+memory+and+resource+leaks+during+serialization. Date accessed: 2026-05-12.

[4] Oracle. "LinkedBlockingQueue (Java Platform SE 8) — Javadoc." https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/LinkedBlockingQueue.html. Date accessed: 2026-05-12.

[5] Baeldung. "Guide to java.util.concurrent.BlockingQueue." https://www.baeldung.com/java-blocking-queue. Date accessed: 2026-05-12.

[6] Valve Developer Community. "Source Multiplayer Networking." https://developer.valvesoftware.com/wiki/Source_Multiplayer_Networking. Date accessed: 2026-05-12.

[7] GameDev.net Forums. "Cooldown timer." https://www.gamedev.net/forums/topic/509878-cooldown-timer/. Date accessed: 2026-05-12.

[8] Baeldung. "Better Retries with Exponential Backoff and Jitter." https://www.baeldung.com/resilience4j-backoff-jitter. Date accessed: 2026-05-12.

[9] Fiedler, Glenn. "Fix Your Timestep!" Gaffer On Games. https://gafferongames.com/post/fix_your_timestep/. Date accessed: 2026-05-12.

[10] Oracle. "Atomic Access (The Java Tutorials > Essential Java Classes > Concurrency)." https://docs.oracle.com/javase/tutorial/essential/concurrency/atomic.html. Date accessed: 2026-05-12.

[11] Oracle. "CopyOnWriteArrayList (Java Platform SE 8) — Javadoc." https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CopyOnWriteArrayList.html. Date accessed: 2026-05-12.

[12] Oracle. "The Event Dispatch Thread (The Java Tutorials > Creating a GUI With Swing > Concurrency in Swing)." https://docs.oracle.com/javase/tutorial/uiswing/concurrency/dispatch.html. Date accessed: 2026-05-12.

[13] Oracle. "RadialGradientPaint (Java Platform SE 8) — Javadoc." https://docs.oracle.com/javase/8/docs/api/java/awt/RadialGradientPaint.html. Date accessed: 2026-05-12.

[14] Oracle. "Compositing Graphics (The Java Tutorials > 2D Graphics > Advanced Topics in Java 2D)." https://docs.oracle.com/javase/tutorial/2d/advanced/compositing.html. Date accessed: 2026-05-12.

[15] Oracle. "AlphaComposite (Java Platform SE 8) — Javadoc." https://docs.oracle.com/javase/8/docs/api/java/awt/AlphaComposite.html. Date accessed: 2026-05-12.

[16] Oracle. "Area (Java Platform SE 8) — Javadoc." https://docs.oracle.com/javase/8/docs/api/java/awt/geom/Area.html. Date accessed: 2026-05-12.

[17] Oracle. "Graphics2D (Java Platform SE 7) — Javadoc, translate(int x, int y)." https://docs.oracle.com/javase/7/docs/api/java/awt/Graphics2D.html. Date accessed: 2026-05-12.

[18] Delorme, Frederic. "Game with JDK [9]: Camera, Action!" Medium. https://medium.com/@McGivrer/game-with-jdk-9-camera-action-66e2ea9af7f6. Date accessed: 2026-05-12.

[19] Oracle. "Controlling Rendering Quality (The Java Tutorials > 2D Graphics > Advanced Topics in Java 2D)." https://docs.oracle.com/javase/tutorial/2d/advanced/quality.html. Date accessed: 2026-05-12.

[20] Knudsen, Jonathan. "Using Java 2D's Image-Processing Model — RescaleOp." InformIT (excerpted from *Java 2D Graphics*, O'Reilly). https://www.informit.com/articles/article.aspx?p=1013851&seqNum=7. Date accessed: 2026-05-12.

[21] Oracle. "Double Buffering and Page Flipping (The Java Tutorials > Bonus > Full-Screen Exclusive Mode API)." https://docs.oracle.com/javase/tutorial/extra/fullscreen/doublebuf.html. Date accessed: 2026-05-12.

[22] Patel, Amit. "2D Visibility." Red Blob Games. https://www.redblobgames.com/articles/visibility/. Date accessed: 2026-05-12.

[23] Strugar, Daniel. "Basic 2D Platformer Physics, Part 6: Object vs. Object Collision Response." Envato Tuts+. https://gamedevelopment.tutsplus.com/basic-platformer-physics-part-6-object-vs-object-collision-response--cms-27604t. Date accessed: 2026-05-12.

[24] GameMaker. "How to Create Jump-Through Platforms in GameMaker." https://gamemaker.io/en/tutorials/platformer-jump-through. Date accessed: 2026-05-12.

[25] Nystrom, Robert. "Update Method (Sequencing Patterns)." *Game Programming Patterns*. https://gameprogrammingpatterns.com/update-method.html. Date accessed: 2026-05-12.

[26] Number Analytics. "Mastering Input Handling in Games." https://www.numberanalytics.com/blog/ultimate-guide-input-handling-game-development. Date accessed: 2026-05-12.

[27] Nystrom, Robert. "State (Design Patterns Revisited)." *Game Programming Patterns*. https://gameprogrammingpatterns.com/state.html. Date accessed: 2026-05-12.

[28] Baeldung. "Avoiding the ConcurrentModificationException in Java." https://www.baeldung.com/java-concurrentmodificationexception. Date accessed: 2026-05-12.
