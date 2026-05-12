
/**
 * Serves as the top-level Swing window for the Lumen Architect client, housing the
 * {@link GameCanvas} rendering surface and any overlay UI controls such as the start
 * button. It configures the window title, enforces the fixed 1024×768 resolution, and
 * wires up the anonymous {@link java.awt.event.ActionListener} that signals the game
 * loop to begin when the player is ready.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */

// =========================================================================
// Citations - CSCI 22 Course Materials Applied
// =========================================================================
// Module 2a "Event Handling"     - addActionListener via lambda for the
//                                  CONNECT button and all three pause-
//                                  menu buttons; addMouseListener with
//                                  an anonymous MouseAdapter for hover
//                                  highlighting. Direct application of
//                                  the event-handling module's listener
//                                  patterns.
// Module 1d "Inner / Anonymous"  - anonymous JTextField subclass that
//                                  overrides paintComponent for
//                                  placeholder text; anonymous
//                                  MouseAdapter for hover. Both are the
//                                  exact "anonymous-class-as-listener"
//                                  pattern shown in 1d.
// Module 3a "Graphics"           - the placeholder JTextField's
//                                  paintComponent override casts
//                                  Graphics to Graphics2D, sets a
//                                  translucent colour, and uses
//                                  FontMetrics to centre text - the
//                                  Graphics module's paintComponent
//                                  pattern applied to a Swing component.
// Module 1a "Modifiers"          - private static final WINDOW_WIDTH /
//                                  WINDOW_HEIGHT constants matching
//                                  GameCanvas.CANVAS_WIDTH /
//                                  CANVAS_HEIGHT.
// =========================================================================

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
public class GameFrame extends JFrame { // Extends JFrame: inherits all standard top-level window behaviour

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

     
    private static final int WINDOW_WIDTH  = 1024; // Matches CANVAS_W in GameCanvas and all rendering constants; must not be changed at runtime

     
    private static final int WINDOW_HEIGHT = 768;  // Matches CANVAS_H in GameCanvas and all rendering constants

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /**
     * The primary rendering surface that occupies the full window. All game
     * world drawing and gesture-overlay rendering is delegated to this component.
     */
    private GameCanvas gameCanvas; // Created in initUI(); wired to GameStarter and InputRouter for game logic

    /**
     * Text field for the player to enter the server IP address. Positioned as a
     * real Swing component over the canvas so it is fully interactive.
     */
    private JTextField ipField; // Overlaid on the canvas at (354,500,316,44); hidden after a successful connection

    /**
     * The button the player clicks to connect to the server.
     * Does NOT start the game directly — only initiates the network handshake.
     */
    private JButton connectButton; // Triggers the background ConnectThread; disabled while connection is in progress

     
    private JButton viewFragmentsBtn; // Toggles the fragment library overlay in GameCanvas when clicked

     
    private JButton resumeBtn; // Calls GameStarter#togglePause() to resume the game loop when clicked

     
    private JButton quitBtn; // Stops the game, resets to menu, and restores the connect UI when clicked

     
    private JLayeredPane layeredPane; // Replaces the default content pane; allows overlay components to paint above GameCanvas

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs and configures the main game window. Sets the title to
     * {@code "Lumen Architect"}, fixes the size to 1024×768, prevents resizing,
     * sets the close operation to {@link JFrame#EXIT_ON_CLOSE}, and calls
     * {@link #initUI()} to build and lay out all child components.
     *
     * <p>Architecture role: Instantiated once by the {@code main()} method (or the
     * launcher). After construction the window is not yet visible; the caller must
     * call {@link #setVisible(boolean)} to display it.</p>
     */
    public GameFrame() {
        super("Lumen Architect");                      // Set the window title bar text
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);          // Lock the window to the fixed game resolution
        setResizable(false);                           // Prevent the player from resizing: the game uses absolute coordinates throughout
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // Terminate the JVM when the window is closed; no background threads need cleanup
        initUI();                                      // Build and add all child components; must be called on the EDT
    }

    // -------------------------------------------------------------------------
    // UI initialisation
    // -------------------------------------------------------------------------

    /**
     * Constructs all child UI components using absolute positioning (null layout)
     * so the IP field and connect button sit on top of the GameCanvas. Both
     * components are added directly to the content pane.
     *
     * <p>Architecture role: Called once from the constructor on the Event Dispatch
     * Thread. Uses {@link JLayeredPane} so the {@link GameCanvas} at
     * {@code DEFAULT_LAYER} is always painted first, and overlay controls at
     * {@code PALETTE_LAYER} (or integer layer 200 for pause buttons) are always
     * painted on top. All bounds are set as absolute pixel coordinates matched to
     * the 1024×768 canvas.</p>
     */
    private void initUI() {
        // Use a JLayeredPane as the content pane so Swing components added at
        // PALETTE_LAYER always paint on top of the GameCanvas at DEFAULT_LAYER.
        layeredPane = new JLayeredPane();                                              // JLayeredPane: enables Z-order stacking of overlapping components
        layeredPane.setPreferredSize(new java.awt.Dimension(WINDOW_WIDTH, WINDOW_HEIGHT)); // Set preferred size so pack() would size correctly if used

        // --- Game canvas (full window, bottom layer) ---
        gameCanvas = new GameCanvas();                                                 // Create the main rendering surface
        gameCanvas.setBounds(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);                     // Fill the entire window; absolute position (0,0)
        layeredPane.add(gameCanvas, JLayeredPane.DEFAULT_LAYER);                      // Add at DEFAULT_LAYER (0): always painted first, below all overlays

        // --- IP address field (top layer) ---
        // Anonymous subclass paints the placeholder text as a translucent overlay
        // so it disappears the moment the field gains focus or contains typed text.
        ipField = new JTextField() {          // Anonymous JTextField subclass: overrides paintComponent for placeholder rendering
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);      // Delegate to JTextField for standard background, selection, and text painting
                if (getText().isEmpty() && !isFocusOwner()) { // Only draw placeholder when empty AND the field does not have keyboard focus
                    Graphics2D g2 = (Graphics2D) g.create();  // Create a derived context so dispose() does not affect the original context
                    g2.setColor(new Color(0xC9, 0xA8, 0x4C, 120)); // Gold with alpha=120: translucent placeholder consistent with the UI palette
                    g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14)); // Monospaced 14pt: matches the field's own text font
                    FontMetrics fm = g2.getFontMetrics();      // Measure the placeholder string height for vertical centring
                    int x = getInsets().left + 4;              // Indent 4 px from the left inset to align with typed text
                    int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent(); // Centre the baseline vertically in the field
                    g2.drawString("Enter server IP...", x, y); // Draw the placeholder hint text
                    g2.dispose();                               // Release the derived context
                }
            }
        };
        ipField.setText("");                                       // Start empty; placeholder visible until the player types
        ipField.setBounds(354, 500, 316, 44);                     // Centred horizontally on a 1024-px canvas (512-158=354); y=500 puts it below the game title
        ipField.setBackground(new Color(0x0E, 0x0E, 0x18));      // Near-black background matching the game's dark UI theme
        ipField.setForeground(new Color(0xC9, 0xA8, 0x4C));      // Gold text: consistent with all interactive UI elements
        ipField.setCaretColor(new Color(0xC9, 0xA8, 0x4C));      // Gold cursor: visible against the dark background
        ipField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14)); // Monospaced for IP address alignment (e.g. "192.168.1.1")
        ipField.setBorder(new LineBorder(new Color(0xC9, 0xA8, 0x4C), 1)); // 1 px gold border frames the field without being obtrusive
        ipField.setOpaque(true);                                   // Ensure the background colour is painted; JTextField may be transparent by default on some L&Fs
        layeredPane.add(ipField, JLayeredPane.PALETTE_LAYER);     // Add at PALETTE_LAYER: above DEFAULT_LAYER so it paints over the game canvas

        // --- Connect button (top layer) ---
        connectButton = new JButton("CONNECT");                    // Label: uppercase monospaced to match the game's title aesthetic
        connectButton.setBounds(431, 560, 162, 44);               // Centred below the IP field (512-81=431); y=560 keeps it close to ipField
        connectButton.setBackground(new Color(0xC9, 0xA8, 0x4C)); // Gold background: primary call-to-action colour
        connectButton.setForeground(new Color(0x0E, 0x0E, 0x18)); // Near-black text: high contrast against the gold background
        connectButton.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14)); // Bold monospaced: emphatic and consistent with the IP field font
        connectButton.setFocusPainted(false);                      // Remove the Swing focus ring: cleaner look in the game's custom UI style
        connectButton.setBorderPainted(false);                     // Remove default border: colour fill provides sufficient visual definition
        connectButton.setOpaque(true);                             // Ensure background colour is painted; required when borderPainted is false
        connectButton.setCursor(new Cursor(Cursor.HAND_CURSOR));   // Hand cursor: signals that the button is clickable
        layeredPane.add(connectButton, JLayeredPane.PALETTE_LAYER); // Add at PALETTE_LAYER so the button overlays the canvas

        // Hover highlight
        connectButton.addMouseListener(new MouseAdapter() { // MouseAdapter: only overrides the two enter/exit methods needed
            @Override
            public void mouseEntered(MouseEvent e) {
                connectButton.setBackground(new Color(0xD4, 0xB8, 0x5C)); // Slightly lighter gold on hover: subtle visual feedback
            }
            @Override
            public void mouseExited(MouseEvent e) {
                connectButton.setBackground(new Color(0xC9, 0xA8, 0x4C)); // Restore original gold on exit: hover effect is temporary
            }
        });

        // ---------------------------------------------------------------
        // CONNECT button — network path ONLY.
        // Runs connectToServer() on a background thread so the EDT stays
        // responsive while the 5-second socket timeout is in progress.
        // ---------------------------------------------------------------
        connectButton.addActionListener(e -> {
            String host = ipField.getText().trim(); // Read and trim the IP address; remove accidental leading/trailing spaces
            if (host.isEmpty()) host = "localhost"; // Default to localhost for easy local testing without typing

            // Disable button so it cannot be double-clicked while connecting
            connectButton.setEnabled(false);                                // Prevent re-click during the connection attempt
            gameCanvas.setConnectionStatus("Connecting to " + host + "..."); // Show feedback so the player knows a connection is in progress
            gameCanvas.repaint();                                             // Force the status message to appear immediately

            final String finalHost = host; // Effectively-final copy for use inside the lambda (Java closure requirement)
            new Thread(() -> {              // Spawn a background thread to avoid blocking the EDT during socket operations
                boolean success = GameStarter.getInstance().connectToServer(finalHost, 9876); // Attempt TCP connection on port 9876; may block up to 5 s
                if (success) {
                    SwingUtilities.invokeLater(() -> {                          // All Swing mutations must happen on the EDT
                        String role = GameSession.getInstance().role;           // Read the role assigned by the server (WANDERER, APPRENTICE, or LOBBY)
                        // Reconnecting clients are handed a real role ("WANDERER"
                        // or "APPRENTICE") on the first packet; fresh clients
                        // receive "LOBBY". Pick a status message that reflects
                        // the actual flow so the UI never shows the misleading
                        // "waiting for other player..." text during reconnect.
                        boolean isReconnect = role != null && !"LOBBY".equals(role); // True if server assigned a real role (reconnect scenario)
                        gameCanvas.setConnectionStatus(isReconnect
                                ? "Reconnecting as " + role + " — restoring session..."   // Em dash: reconnect path shows session restoration message
                                : "Connected as " + role + " — waiting for other player..."); // Em dash: fresh connect waits for the second player
                        ipField.setVisible(false);                            // Hide the IP field: no longer needed once connected
                        connectButton.setVisible(false);                      // Hide the connect button: game UI takes over from here
                        gameCanvas.repaint();                                  // Repaint to show the updated status message and hidden controls
                    });
                    // startGameLoop() is called by the NetworkIO thread once bothConnected is true
                    GameStarter.getInstance().startNetworkThread(); // Start the NetworkIO thread to receive server packets
                } else {
                    SwingUtilities.invokeLater(() -> {                          // Connection failed: update UI on EDT
                        gameCanvas.setConnectionStatus(
                                "Connection failed — is the server running?"); // Em dash: inform the player of the failure and hint at the cause
                        connectButton.setEnabled(true);                         // Re-enable the button so the player can retry
                        gameCanvas.repaint();                                    // Repaint to show the error message
                    });
                }
            }, "ConnectThread").start(); // Name the thread "ConnectThread" for easier identification in thread dumps

        });

        // --- Pause menu buttons (layer 200, hidden by default) ---
        Color pauseBg   = new Color(0x1A, 0x12, 0x20); // Dark purple-black background: dark-themed pause panel consistent with the game aesthetic
        Color pauseFg   = new Color(0xC9, 0xA8, 0x4C); // Gold foreground: matches all other interactive UI text
        Font  pauseFont = new Font("Serif", Font.PLAIN, 16); // Serif 16pt: more elegant than monospaced for pause menu labels

        viewFragmentsBtn = createPauseButton("View Fragments", pauseBg, pauseFg, pauseFont); // Create styled "View Fragments" button
        viewFragmentsBtn.setBounds(412, 320, 200, 44);           // Centred at x=512-100=412; top pause-menu button
        layeredPane.add(viewFragmentsBtn, Integer.valueOf(200)); // Layer 200: above PALETTE_LAYER (100); drawn on top of everything during pause

        resumeBtn = createPauseButton("Resume", pauseBg, pauseFg, pauseFont); // Create styled "Resume" button
        resumeBtn.setBounds(412, 380, 200, 44);                  // Below viewFragmentsBtn with 60 px vertical gap (380-320=60)
        layeredPane.add(resumeBtn, Integer.valueOf(200));        // Same layer 200 so all pause buttons are consistently on top

        quitBtn = createPauseButton("Quit", pauseBg, pauseFg, pauseFont); // Create styled "Quit" button
        quitBtn.setBounds(412, 440, 200, 44);                    // Below resumeBtn with 60 px vertical gap
        layeredPane.add(quitBtn, Integer.valueOf(200));          // Layer 200: matches other pause buttons

        // Wire actions with diagnostic logging
        viewFragmentsBtn.addActionListener(e -> {
            System.out.println("View Fragments clicked");                       // Debug: confirm the button click reached the listener
            GameCanvas gc = getGameCanvas();                                     // Get the canvas reference; should never be null after initUI
            if (gc != null) {
                gc.setShowFragmentLibrary(!gc.isShowingFragmentLibrary());       // Toggle: if library is open, close it; if closed, open it
                gc.repaint();                                                     // Repaint immediately so the archive overlay appears/disappears without a frame delay
            }
        });

        resumeBtn.addActionListener(e -> {
            System.out.println("Resume button clicked");  // Debug: confirm the button click reached the listener
            hidePauseButtons();                            // Hide the pause menu immediately so the game world is visible
            GameStarter gs = GameStarter.getInstance();    // Get the game starter singleton to toggle the pause state
            if (gs != null) gs.togglePause();             // Resume the game loop; togglePause() also sends GAME_RESUME to the server
        });

        quitBtn.addActionListener(e -> {
            System.out.println("Quit to main menu");                // Debug: confirm the button click reached the listener
            GameStarter.getInstance().stopGame();                   // Stop the game loop and disconnect from the server
            hidePauseButtons();                                      // Hide the pause menu before resetting to the menu screen
            GameStarter.getInstance().resetToMenu();                // Reset all game state so a new connection can be made
            ipField.setVisible(true);                               // Show the IP field again for the player to reconnect
            connectButton.setVisible(true);                         // Show the connect button again
            connectButton.setEnabled(true);                         // Re-enable the button (it may have been disabled during an active connection)
            gameCanvas.setConnectionStatus("");                      // Clear the status message from the previous session
            gameCanvas.setPhase(LevelState.GamePhase.MENU);         // Tell the canvas to render the main menu phase
            gameCanvas.repaint();                                    // Repaint to show the restored connect UI
        });

        // Initially hidden
        hidePauseButtons(); // Hide all three pause buttons at startup; they are only shown when the game is paused

        setContentPane(layeredPane); // Replace the default JFrame content pane with the layered pane so Z-ordering applies
    }

    /**
     * Creates a styled pause menu {@link JButton} with the given text, background,
     * foreground, and font. All pause buttons share the same gold border, no focus
     * ring, and a hand cursor.
     *
     * <p>Architecture role: Shared factory method to keep the three pause-button
     * creations in {@link #initUI()} DRY. The caller is responsible for setting
     * bounds and adding the button to the layered pane.</p>
     *
     * @param text the button label text
     * @param bg   the background colour
     * @param fg   the foreground (text) colour
     * @param font the text font
     * @return a fully styled but un-bounded {@link JButton}
     */
    private JButton createPauseButton(String text, Color bg, Color fg, Font font) {
        JButton btn = new JButton(text);                               // Create button with the given label
        btn.setBackground(bg);                                         // Apply the pause-panel background colour
        btn.setForeground(fg);                                         // Apply the gold text colour
        btn.setFont(font);                                             // Apply the serif pause font
        btn.setBorder(new LineBorder(new Color(0xC9, 0xA8, 0x4C), 1)); // 1 px gold border frames the button
        btn.setFocusPainted(false);                                    // No focus ring: pause menu is purely mouse-driven
        btn.setFocusable(false);                                       // Remove from the focus traversal order: TAB should not reach pause buttons
        btn.setOpaque(true);                                           // Ensure background colour is painted
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));                 // Hand cursor: indicates the button is clickable
        return btn; // Return the styled button; caller sets bounds and adds it to the layered pane
    }

    /**
     * Shows all three pause menu buttons. Called by {@link GameStarter} when the
     * game is paused (ESC key or network pause event).
     *
     * <p>Architecture role: Centralises the visibility change so callers do not
     * need to know which buttons exist. The buttons were added at layer 200 in
     * {@link #initUI()} so they paint on top of the game canvas and all other
     * overlays.</p>
     */
    public void showPauseButtons() {
        viewFragmentsBtn.setVisible(true); // Make "View Fragments" button visible
        resumeBtn.setVisible(true);        // Make "Resume" button visible
        quitBtn.setVisible(true);          // Make "Quit" button visible
    }

    /**
     * Hides all three pause menu buttons. Called by {@link GameStarter} when the
     * game is resumed, and by the quit button's own action listener.
     *
     * <p>Architecture role: Mirror of {@link #showPauseButtons()}; keeps visibility
     * management symmetrical.</p>
     */
    public void hidePauseButtons() {
        viewFragmentsBtn.setVisible(false); // Hide "View Fragments" button
        resumeBtn.setVisible(false);        // Hide "Resume" button
        quitBtn.setVisible(false);          // Hide "Quit" button
    }

    /**
     * Returns the View Fragments button for external F-key wiring.
     *
     * <p>Architecture role: Allows {@link InputRouter} or {@link GameStarter} to
     * programmatically click or query the button (e.g., to trigger the fragment
     * library from the keyboard F shortcut) without exposing all pause-menu buttons
     * individually.</p>
     *
     * @return the View Fragments button; never {@code null} after construction
     */
    public JButton getViewFragmentsBtn() {
        return viewFragmentsBtn; // Return the button reference; caller may add listeners or invoke doClick()
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link GameCanvas} rendering surface contained within this frame.
     *
     * <p>Architecture role: Used by {@link GameStarter} and {@link InputRouter} to
     * obtain the canvas reference for registering input listeners, querying level
     * state, and setting connection status messages.</p>
     *
     * @return the game canvas; never {@code null} after construction
     */
    public GameCanvas getGameCanvas() {
        return gameCanvas; // Return the primary rendering surface created in initUI()
    }

    /**
     * Returns the connect button overlay control.
     *
     * <p>Architecture role: Exposed so {@link GameStarter} can hide or disable the
     * button programmatically if the network state changes outside the button's own
     * action listener (e.g., during a forced reconnect).</p>
     *
     * @return the connect button; never {@code null} after construction
     */
    public JButton getConnectButton() {
        return connectButton; // Return the CONNECT button created in initUI()
    }

    /**
     * Returns the IP address text field overlay control.
     *
     * <p>Architecture role: Exposed so {@link GameStarter} can read or clear the
     * IP field text after a disconnect, allowing the player to enter a new address
     * without restarting the application.</p>
     *
     * @return the IP field; never {@code null} after construction
     */
    public JTextField getIpField() {
        return ipField; // Return the IP address text field created in initUI()
    }
}
