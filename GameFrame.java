/**
 * Top-level application window that hosts the GameCanvas.
 *
 * @author Marxus Antonio L. Magisa (253602) & Antonio Sebastian B. Pasia (254505)
 * @version May 12, 2026
 *
 * I have not discussed the Java language code in my program
 * with anyone other than my instructor or the teaching assistants
 * assigned to this course.
 *
 * I have not used Java language code obtained from another student,
 * or any other unauthorized source, either modified or unmodified.
 * If any Java language code or documentation used in my program
 * was obtained from another source, such as a textbook or website,
 * that has been clearly noted with a proper citation in the comments
 * of my program.
 */
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
public class GameFrame extends JFrame {

    private static final int WINDOW_WIDTH  = 1024;

    private static final int WINDOW_HEIGHT = 768;

    private GameCanvas gameCanvas;

    private JTextField ipField;

    private JButton connectButton;

    private JButton viewFragmentsBtn;

    private JButton resumeBtn;

    private JButton quitBtn;

    private JLayeredPane layeredPane;

    public GameFrame() {
        super("Lumen Architect");
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setResizable(false);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        initUI();
    }

    private void initUI() {

        layeredPane = new JLayeredPane();
        layeredPane.setPreferredSize(new java.awt.Dimension(WINDOW_WIDTH, WINDOW_HEIGHT));

        gameCanvas = new GameCanvas();
        gameCanvas.setBounds(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
        layeredPane.add(gameCanvas, JLayeredPane.DEFAULT_LAYER);

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

        connectButton.addActionListener(e -> {
            String host = ipField.getText().trim();
            if (host.isEmpty()) host = "localhost";

            connectButton.setEnabled(false);
            gameCanvas.setConnectionStatus("Connecting to " + host + "...");
            gameCanvas.repaint();

            final String finalHost = host;
            new Thread(() -> {
                boolean success = GameStarter.getInstance().connectToServer(finalHost, 9876);
                if (success) {
                    SwingUtilities.invokeLater(() -> {
                        String role = GameSession.getInstance().role;

                        boolean isReconnect = role != null && !"LOBBY".equals(role);
                        gameCanvas.setConnectionStatus(isReconnect
                                ? "Reconnecting as " + role + " — restoring session..."
                                : "Connected as " + role + " — waiting for other player...");
                        ipField.setVisible(false);
                        connectButton.setVisible(false);
                        gameCanvas.repaint();
                    });

                    GameStarter.getInstance().startNetworkThread();
                } else {
                    SwingUtilities.invokeLater(() -> {
                        gameCanvas.setConnectionStatus(
                                "Connection failed — is the server running?");
                        connectButton.setEnabled(true);
                        gameCanvas.repaint();
                    });
                }
            }, "ConnectThread").start();

        });

        Color pauseBg   = new Color(0x1A, 0x12, 0x20);
        Color pauseFg   = new Color(0xC9, 0xA8, 0x4C);
        Font  pauseFont = new Font("Serif", Font.PLAIN, 16);

        viewFragmentsBtn = createPauseButton("View Fragments", pauseBg, pauseFg, pauseFont);
        viewFragmentsBtn.setBounds(412, 320, 200, 44);
        layeredPane.add(viewFragmentsBtn, Integer.valueOf(200));

        resumeBtn = createPauseButton("Resume", pauseBg, pauseFg, pauseFont);
        resumeBtn.setBounds(412, 380, 200, 44);
        layeredPane.add(resumeBtn, Integer.valueOf(200));

        quitBtn = createPauseButton("Quit", pauseBg, pauseFg, pauseFont);
        quitBtn.setBounds(412, 440, 200, 44);
        layeredPane.add(quitBtn, Integer.valueOf(200));

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
            gameCanvas.setConnectionStatus("");
            gameCanvas.setPhase(LevelState.GamePhase.MENU);
            gameCanvas.repaint();
        });

        hidePauseButtons();

        setContentPane(layeredPane);
    }

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

    public void showPauseButtons() {
        viewFragmentsBtn.setVisible(true);
        resumeBtn.setVisible(true);
        quitBtn.setVisible(true);
    }

    public void hidePauseButtons() {
        viewFragmentsBtn.setVisible(false);
        resumeBtn.setVisible(false);
        quitBtn.setVisible(false);
    }

    public JButton getViewFragmentsBtn() {
        return viewFragmentsBtn;
    }

    public GameCanvas getGameCanvas() {
        return gameCanvas;
    }

    public JButton getConnectButton() {
        return connectButton;
    }

    public JTextField getIpField() {
        return ipField;
    }
}
