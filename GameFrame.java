
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

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.LineBorder;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class GameFrame extends JFrame {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /** The fixed width of the game window in pixels. */
    private static final int WINDOW_WIDTH = 1024;

    /** The fixed height of the game window in pixels. */
    private static final int WINDOW_HEIGHT = 768;

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /**
     * The primary rendering surface that occupies the full window. All game
     * world drawing and gesture-overlay rendering is delegated to this component.
     */
    private GameCanvas gameCanvas;

    /**
     * Text field for the player to enter the server IP address. Positioned as a
     * real Swing component over the canvas so it is fully interactive.
     */
    private JTextField ipField;

    /**
     * The button the player clicks to connect to the server.
     * Does NOT start the game directly — only initiates the network handshake.
     */
    private JButton connectButton;

    /**
     * Separate button that bypasses the server entirely and starts an offline
     * single-player test session as the Wanderer. Kept visually distinct from
     * the CONNECT button so the two paths cannot be confused.
     */
    private JButton playOfflineBtn;

    /** Pause menu button: view the fragment library. */
    private JButton viewFragmentsBtn;

    /** Pause menu button: resume the game. */
    private JButton resumeBtn;

    /** Pause menu button: quit the game. */
    private JButton quitBtn;

    /** The layered pane used as the content pane. */
    private JLayeredPane layeredPane;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs and configures the main game window. Sets the title to
     * {@code "Lumen Architect"}, fixes the size to 1024×768, prevents resizing,
     * sets the close operation to {@link JFrame#EXIT_ON_CLOSE}, and calls
     * {@link #initUI()} to build and lay out all child components.
     */
    public GameFrame() {
        super("Lumen Architect");
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setResizable(false);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        initUI();
    }

    // -------------------------------------------------------------------------
    // UI initialisation
    // -------------------------------------------------------------------------

    /**
     * Constructs all child UI components using absolute positioning (null layout)
     * so the IP field and connect button sit on top of the GameCanvas. Both
     * components are added directly to the content pane.
     */
    private void initUI() {
        // Use a JLayeredPane as the content pane so Swing components added at
        // PALETTE_LAYER always paint on top of the GameCanvas at DEFAULT_LAYER.
        layeredPane = new JLayeredPane();
        layeredPane.setPreferredSize(new java.awt.Dimension(WINDOW_WIDTH, WINDOW_HEIGHT));

        // --- Game canvas (full window, bottom layer) ---
        gameCanvas = new GameCanvas();
        gameCanvas.setBounds(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
        layeredPane.add(gameCanvas, JLayeredPane.DEFAULT_LAYER);

        // --- IP address field (top layer) ---
        // Anonymous subclass paints the placeholder text as a translucent overlay
        // so it disappears the moment the field gains focus or contains typed text.
        ipField = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !isFocusOwner()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(new Color(0xC9, 0xA8, 0x4C, 120));
                    g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
                    FontMetrics fm = g2.getFontMetrics();
                    int x = getInsets().left + 4;
                    int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                    g2.drawString("Enter server IP...", x, y);
                    g2.dispose();
                }
            }
        };
        ipField.setText("");
        ipField.setBounds(354, 500, 316, 44);
        ipField.setBackground(new Color(0x0E, 0x0E, 0x18));
        ipField.setForeground(new Color(0xC9, 0xA8, 0x4C));
        ipField.setCaretColor(new Color(0xC9, 0xA8, 0x4C));
        ipField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        ipField.setBorder(new LineBorder(new Color(0xC9, 0xA8, 0x4C), 1));
        ipField.setOpaque(true);
        layeredPane.add(ipField, JLayeredPane.PALETTE_LAYER);

        // --- Connect button (top layer) ---
        connectButton = new JButton("CONNECT");
        connectButton.setBounds(431, 560, 162, 44);
        connectButton.setBackground(new Color(0xC9, 0xA8, 0x4C));
        connectButton.setForeground(new Color(0x0E, 0x0E, 0x18));
        connectButton.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        connectButton.setFocusPainted(false);
        connectButton.setBorderPainted(false);
        connectButton.setOpaque(true);
        connectButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        layeredPane.add(connectButton, JLayeredPane.PALETTE_LAYER);

        // Hover highlight
        connectButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                connectButton.setBackground(new Color(0xD4, 0xB8, 0x5C));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                connectButton.setBackground(new Color(0xC9, 0xA8, 0x4C));
            }
        });

        // ---------------------------------------------------------------
        // CONNECT button — network path ONLY.
        // Runs connectToServer() on a background thread so the EDT stays
        // responsive while the 5-second socket timeout is in progress.
        // ---------------------------------------------------------------
        connectButton.addActionListener(e -> {
            String host = ipField.getText().trim();
            if (host.isEmpty()) host = "localhost";

            // Disable button so it cannot be double-clicked while connecting
            connectButton.setEnabled(false);
            gameCanvas.setConnectionStatus("Connecting to " + host + "...");
            gameCanvas.repaint();

            final String finalHost = host;
            new Thread(() -> {
                boolean success = GameStarter.getInstance().connectToServer(finalHost, 9876);
                if (success) {
                    SwingUtilities.invokeLater(() -> {
                        gameCanvas.setConnectionStatus(
                                "Connected as " + GameSession.getInstance().role
                                + " \u2014 waiting for other player...");
                        ipField.setVisible(false);
                        connectButton.setVisible(false);
                        playOfflineBtn.setVisible(false);
                        gameCanvas.repaint();
                    });
                    // startGameLoop() is called by the NetworkIO thread once bothConnected is true
                    GameStarter.getInstance().startNetworkThread();
                } else {
                    SwingUtilities.invokeLater(() -> {
                        gameCanvas.setConnectionStatus(
                                "Connection failed \u2014 is the server running?");
                        connectButton.setEnabled(true);
                        gameCanvas.repaint();
                    });
                }
            }, "ConnectThread").start();
        });

        // ---------------------------------------------------------------
        // PLAY OFFLINE button — starts single-player test without server.
        // All offline / calibration logic lives here; CONNECT is untouched.
        // ---------------------------------------------------------------
        playOfflineBtn = new JButton("PLAY OFFLINE");
        playOfflineBtn.setBounds(363, 620, 298, 40);
        playOfflineBtn.setBackground(new Color(0x28, 0x28, 0x3A));
        playOfflineBtn.setForeground(new Color(0xC9, 0xA8, 0x4C));
        playOfflineBtn.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        playOfflineBtn.setFocusPainted(false);
        playOfflineBtn.setBorderPainted(true);
        playOfflineBtn.setBorder(new LineBorder(new Color(0xC9, 0xA8, 0x4C, 100), 1));
        playOfflineBtn.setOpaque(true);
        playOfflineBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        layeredPane.add(playOfflineBtn, JLayeredPane.PALETTE_LAYER);

        playOfflineBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                playOfflineBtn.setBackground(new Color(0x38, 0x38, 0x50));
            }
            @Override public void mouseExited(MouseEvent e) {
                playOfflineBtn.setBackground(new Color(0x28, 0x28, 0x3A));
            }
        });

        playOfflineBtn.addActionListener(e -> {
            GameSession.getInstance().setRole("WANDERER");
            GameSession.getInstance().setOffline(true);
            ipField.setVisible(false);
            connectButton.setVisible(false);
            playOfflineBtn.setVisible(false);
            // Gesture system removed — always go straight to game
            GameStarter.getInstance().startOfflineGame();
        });

        // --- Pause menu buttons (layer 200, hidden by default) ---
        Color pauseBg = new Color(0x1A, 0x12, 0x20);
        Color pauseFg = new Color(0xC9, 0xA8, 0x4C);
        Font pauseFont = new Font("Serif", Font.PLAIN, 16);

        viewFragmentsBtn = createPauseButton("View Fragments", pauseBg, pauseFg, pauseFont);
        viewFragmentsBtn.setBounds(412, 320, 200, 44);
        layeredPane.add(viewFragmentsBtn, Integer.valueOf(200));

        resumeBtn = createPauseButton("Resume", pauseBg, pauseFg, pauseFont);
        resumeBtn.setBounds(412, 380, 200, 44);
        layeredPane.add(resumeBtn, Integer.valueOf(200));

        quitBtn = createPauseButton("Quit", pauseBg, pauseFg, pauseFont);
        quitBtn.setBounds(412, 440, 200, 44);
        layeredPane.add(quitBtn, Integer.valueOf(200));

        // Wire actions with diagnostic logging
        viewFragmentsBtn.addActionListener(e -> {
            System.out.println("View Fragments clicked");
            GameCanvas gc = getGameCanvas();
            if (gc != null) {
                gc.setShowFragmentLibrary(!gc.isShowingFragmentLibrary());
                gc.repaint();
            }
        });

        resumeBtn.addActionListener(e -> {
            System.out.println("Resume button clicked");
            hidePauseButtons();
            GameStarter gs = GameStarter.getInstance();
            if (gs != null) gs.togglePause();
        });

        quitBtn.addActionListener(e -> {
            System.out.println("Quit to main menu");
            GameStarter.getInstance().stopGame();
            hidePauseButtons();
            GameStarter.getInstance().resetToMenu();
            ipField.setVisible(true);
            connectButton.setVisible(true);
            connectButton.setEnabled(true);
            playOfflineBtn.setVisible(true);
            gameCanvas.setConnectionStatus("");
            gameCanvas.setPhase(LevelState.GamePhase.MENU);
            gameCanvas.repaint();
        });

        // Initially hidden
        hidePauseButtons();

        setContentPane(layeredPane);
    }

    /**
     * Creates a styled pause menu JButton.
     */
    private JButton createPauseButton(String text, Color bg, Color fg, Font font) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFont(font);
        btn.setBorder(new LineBorder(new Color(0xC9, 0xA8, 0x4C), 1));
        btn.setFocusPainted(false);
        btn.setFocusable(false);
        btn.setOpaque(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /**
     * Shows the three pause menu buttons.
     */
    public void showPauseButtons() {
        viewFragmentsBtn.setVisible(true);
        resumeBtn.setVisible(true);
        quitBtn.setVisible(true);
    }

    /**
     * Hides the three pause menu buttons.
     */
    public void hidePauseButtons() {
        viewFragmentsBtn.setVisible(false);
        resumeBtn.setVisible(false);
        quitBtn.setVisible(false);
    }

    /**
     * Returns the View Fragments button for external F-key wiring.
     */
    public JButton getViewFragmentsBtn() {
        return viewFragmentsBtn;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link GameCanvas} rendering surface contained within this frame.
     *
     * @return the game canvas; never {@code null} after construction
     */
    public GameCanvas getGameCanvas() {
        return gameCanvas;
    }

    /**
     * Returns the connect button overlay control.
     *
     * @return the connect button; never {@code null} after construction
     */
    public JButton getConnectButton() {
        return connectButton;
    }

    /**
     * Returns the IP address text field overlay control.
     *
     * @return the IP field; never {@code null} after construction
     */
    public JTextField getIpField() {
        return ipField;
    }
}
