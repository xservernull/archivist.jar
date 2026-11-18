package assets.archivist.binary;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The admin panel: the green terminal window the player opens with the admin panel item. It runs in its own
 * JVM, started by the game ({@code AdminPanelClient}) with the loopback port of the game as the only argument.
 *
 * <p>The game and the panel talk in text lines (UTF-8), one message per line. Game to panel:
 * <ul>
 *   <li>{@code D:<id>} a dimension that exists, in the order of the index</li>
 *   <li>{@code V:<id>} a dimension the player may enter (shown as ONLINE; the others show as ????)</li>
 *   <li>{@code LOG:<text>} a maintenance log entry, {@code HINT:<text>} an entry clue</li>
 *   <li>{@code T:<key>=<text>} a translated window text (keys: {@link #DEFAULTS}); the game sends them before
 *       {@code READY}, in the language of the Archivist menu. A key that is not sent keeps its English text.
 *       Command words ({@code index}, {@code logs}, {@code hints}, {@code help}, {@code exit}, {@code thepit}) are
 *       never translated.</li>
 *   <li>{@code READY} the start data is complete: the panel prints its boot text. After it, {@code V}, {@code LOG}
 *       and {@code HINT} are live updates and are announced in the terminal.</li>
 * </ul>
 * Panel to game: {@code GO:<id>} when the player picks a dimension, or {@code GO:thepit} for the hidden command
 * {@code thepit} (the library hint; not in the index or in help); the panel closes right after. The game
 * checks the id again, so this window is never trusted. When the game closes the connection, the panel closes.
 *
 * <p>The corruption stage (1 to 4) is not sent: the panel works it out from how many dimensions are open.
 * From stage 3 the screen flickers and glitches, at stage 4 commands fail at random and the abort button
 * stops working (it only locks the session).
 */
public class AdminPanel extends JPanel {
    private static final Color BG = Color.BLACK;
    private static final Color TEXT = new Color(80, 180, 80);
    private static final Color BRIGHT = new Color(140, 255, 140);
    private static final Color CURSOR = new Color(140, 255, 140, 200);
    private static final Color BUTTON_OFF = new Color(120, 20, 20);
    private static final Color BUTTON_ON = new Color(220, 40, 40);
    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 13);
    private static final Font BOLD = MONO.deriveFont(Font.BOLD);

    private static final String PROMPT = "a:\\> ";
    private static final int LINE_HEIGHT = 18, MARGIN = 12, MAX_LINES = 40, TICK_MS = 66;
    private static final int DIMS_PER_STAGE = 4;
    /** Keys of the stage 4 glitch lines; null = an empty line. */
    private static final String[] GLITCHES = {
            "glitch.0", null, "glitch.2", "glitch.3", "glitch.4", "glitch.5"};
    private static final int HELP_COLUMN = 23;

    /** Every window text with its English default. Same keys as in {@code archivist_lang/<code>.json}. */
    private static final Map<String, String> DEFAULTS = new HashMap<>();

    static {
        d("title", "<c.o.i administrative terminal>");
        d("boot.help", "type 'logs' for maintenance entries. type 'hints', they are your only hints on how to enter a dim.");
        d("boot.synced", "last synced [%s]");
        d("boot.incoming", "[ incoming — unread entries from primary administrator ]");
        d("boot.index", "type 'index' to list sectors. SOME DIMS CAN ONLY BE EXITED THROUGH INDEX. type 'exit' to abort.");
        d("boot.warning", "w a r n i n g  —  s a v e  f i l e  i n t e g r i t y  r e d u c e d");
        d("boot.logs", "type 'logs' to view maintenance entries.");
        d("boot.hints", "type 'hints' to view entry clues.");
        d("new_log", "[ NEW ENTRY ]");
        d("new_hint", "[ NEW OBJECTIVE UNLOCKED ]");
        d("tip_logs", "type 'logs' to view all maintenance entries.");
        d("tip_hints", "type 'hints' to view all entry clues.");
        d("go.resolving", "resolving pipeline sync parameters...");
        d("go.transit", "initiating transit sequence...");
        d("go.watching", "h e  i s  w a t c h i n g");
        d("pit", "sector seam located. tearing it open...");
        d("glitch.0", "e r r o r  —  s e g m e n t  f a u l t");
        d("glitch.2", "i told you to stop.");
        d("glitch.3", "d a t a  c o r r u p t e d");
        d("glitch.4", "he is reading this.");
        d("glitch.5", "s t o p  u s i n g  t h i s");
        d("logs.title", "[ maintenance log — primary administrator ]");
        d("logs.none", "no log entries available.");
        d("hints.title", "[ H: next dimension hints ]");
        d("hints.none", "no hints cached yet.");
        d("denied", "sector not indexed. access denied.");
        d("unknown.1", "unlinked dimension keys cached in memory:");
        d("unknown.2", "use 'index' to retrieve valid dimension ids.");
        d("index.fetch", "fetching local sector manifests from register...");
        d("index.row", "[sector_%02d]  %s  status: %s");
        d("status.online", "ONLINE");
        d("status.offline", "?????");
        d("help.title", "available commands:");
        d("help.index", "list all sector keys");
        d("help.dim", "transit to target sector");
        d("help.logs", "view maintenance entries");
        d("help.hints", "view next-dimension hints");
        d("help.exit", "abort session");
        d("exit.locked", "d e n i e d  —  s e s s i o n  l o c k e d");
        d("exit.abort", "aborting session. connection severed. relaunching safetyprotocol.bat...");
        d("abort", "ABORT");
    }

    private static void d(String key, String text) { DEFAULTS.put("archivist.panel." + key, text); }

    private record Line(String text, boolean bright) {}

    private final List<Line> lines = new ArrayList<>();
    private final StringBuilder input = new StringBuilder();
    private final List<String> dims = new ArrayList<>();
    private final Set<String> open = new HashSet<>();
    private final List<String> logs = new ArrayList<>();
    private final List<String> hints = new ArrayList<>();
    private final Map<String, String> texts = new HashMap<>();
    private final Random random = new Random();
    private volatile PrintWriter link;
    private boolean ready, closing, blank;
    private int tick, sweepY;

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : -1;
        SwingUtilities.invokeLater(() -> {
            AdminPanel panel = new AdminPanel();
            JFrame frame = new JFrame("adminpanel");
            frame.setUndecorated(true);
            frame.setAlwaysOnTop(true);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(panel);
            frame.setSize(900, 520);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            panel.start(port);
        });
    }

    AdminPanel() {
        setBackground(BG);
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (closing) return;
                if (e.getKeyCode() == KeyEvent.VK_ENTER) command(input.toString());
                else if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE && input.length() > 0) input.setLength(input.length() - 1);
            }

            @Override public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                if (!closing && c >= ' ' && c != 127) input.append(c);
            }
        });
        MouseAdapter mouse = new MouseAdapter() {
            private Point grab;

            @Override public void mousePressed(MouseEvent e) { grab = e.getPoint(); }

            @Override public void mouseDragged(MouseEvent e) {
                Window window = SwingUtilities.getWindowAncestor(AdminPanel.this);
                if (window == null || grab == null) return;
                Point at = window.getLocation();
                window.setLocation(at.x + e.getX() - grab.x, at.y + e.getY() - grab.y);
            }

            @Override public void mouseReleased(MouseEvent e) {
                if (!closing && abortButton().contains(e.getPoint())) exit();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    /** Hides the mouse cursor, starts the animation and the link to the game (a negative port: no game, empty terminal). */
    private void start(int port) {
        Cursor none = Toolkit.getDefaultToolkit().createCustomCursor(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), new Point(), "none");
        setCursor(none);
        new Timer(TICK_MS, e -> tick()).start();
        requestFocusInWindow();
        if (port < 0) receive("READY");
        else connect(port);
    }

    // ---- link to the game ----

    private void connect(int port) {
        Thread thread = new Thread(() -> {
            try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                link = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                String line;
                while ((line = in.readLine()) != null) {
                    String message = line;
                    SwingUtilities.invokeLater(() -> receive(message));
                }
            } catch (IOException e) {
                System.out.println("[adminpanel] link failed: " + e.getMessage());
            }
            System.exit(0);
        }, "adminpanel-link");
        thread.setDaemon(true);
        thread.start();
    }

    /** One message from the game (see the class comment). */
    void receive(String message) {
        int colon = message.indexOf(':');
        String key = colon < 0 ? message : message.substring(0, colon);
        String value = colon < 0 ? "" : message.substring(colon + 1).trim();
        switch (key) {
            case "T" -> {
                int eq = value.indexOf('=');
                if (eq > 0) texts.put(value.substring(0, eq).trim(), value.substring(eq + 1).trim());
            }
            case "D" -> dims.add(value);
            case "V" -> open.add(value);
            case "LOG" -> entry(logs, value, "new_log", "tip_logs");
            case "HINT" -> entry(hints, value, "new_hint", "tip_hints");
            case "READY" -> {
                ready = true;
                boot();
            }
            default -> { }
        }
    }

    /** The text of a window key in the language the game sent, else English, else the key. {@code args} fill the %s slots. */
    private String t(String key, Object... args) {
        String full = "archivist.panel." + key;
        String text = texts.getOrDefault(full, DEFAULTS.getOrDefault(full, full));
        if (args.length == 0) return text;
        try {
            return String.format(text, args);
        } catch (RuntimeException e) {
            return text;
        }
    }

    private void entry(List<String> list, String text, String titleKey, String tipKey) {
        if (text.isEmpty()) return;
        list.add(text);
        if (!ready) return;
        say(t(titleKey));
        print("> " + text);
        print(t(tipKey));
    }

    private void go(String dim) {
        closing = true;
        print(t("go.resolving"));
        print(t("go.transit"));
        if (stage() >= 4) print(t("go.watching"));
        send(dim);
    }

    /** The hidden command from the library hint: the game opens a pit beside the player. */
    private void pit() {
        closing = true;
        print(t("pit"));
        send("thepit");
    }

    private void send(String id) {
        later(800, () -> {
            PrintWriter out = link;
            if (out != null) out.println("GO:" + id);
            later(300, () -> System.exit(0));
        });
    }

    // ---- terminal ----

    private int stage() {
        return 1 + Math.min(3, open.size() / DIMS_PER_STAGE);
    }

    private void boot() {
        lines.clear();
        say(t("title"));
        print(t("boot.help"));
        print(t("boot.synced", new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())));
        if (stage() >= 4) {
            print("");
            say(t("boot.incoming"));
            logs.forEach(log -> print("> " + log));
            print("");
        }
        print(t("boot.index"));
        if (stage() >= 3) print(t("boot.warning"));
        if (!logs.isEmpty()) print(t("boot.logs"));
        if (!hints.isEmpty()) print(t("boot.hints"));
    }

    void command(String typed) {
        String cmd = typed.trim().toLowerCase();
        input.setLength(0);
        say(PROMPT + cmd);
        if (cmd.isEmpty()) return;
        if (stage() >= 4 && random.nextDouble() < 0.25) {
            String glitch = GLITCHES[random.nextInt(GLITCHES.length)];
            print(glitch == null ? "" : t(glitch));
            return;
        }
        switch (cmd) {
            case "index" -> index();
            case "logs" -> list(t("logs.title"), logs, t("logs.none"), "");
            case "hints" -> list(t("hints.title"), hints, t("hints.none"), "> ");
            case "help" -> help();
            case "exit" -> exit();
            case "thepit" -> pit();
            default -> {
                if (open.contains(cmd)) {
                    go(cmd);
                } else if (dims.contains(cmd)) {
                    print(t("denied"));
                } else {
                    print(t("unknown.1"));
                    print(t("unknown.2"));
                }
            }
        }
    }

    private void index() {
        print(t("index.fetch"));
        for (int i = 0; i < dims.size(); i++) {
            boolean online = open.contains(dims.get(i));
            print(t("index.row", i, String.format("%-30s", online ? dims.get(i) : "????"), online ? t("status.online") : t("status.offline")));
        }
    }

    private void list(String title, List<String> entries, String none, String prefix) {
        if (entries.isEmpty()) {
            print(none);
            return;
        }
        say(title);
        entries.forEach(entry -> print(prefix + entry));
    }

    private void help() {
        print(t("help.title"));
        helpLine("index", "help.index");
        helpLine("<dimension_id>", "help.dim");
        helpLine("logs", "help.logs");
        helpLine("hints", "help.hints");
        helpLine("exit", "help.exit");
    }

    private void helpLine(String command, String key) {
        print(String.format("  %-" + HELP_COLUMN + "s%s", command, t(key)));
    }

    private void exit() {
        closing = true;
        if (stage() >= 4) {
            print(t("exit.locked"));
            later(2000, () -> System.exit(0));
        } else {
            print(t("exit.abort"));
            later(1000, () -> System.exit(0));
        }
    }

    private void print(String text) { add(text, false); }

    private void say(String text) { add(text, true); }

    private void add(String text, boolean bright) {
        lines.add(new Line(text, bright));
        if (lines.size() > MAX_LINES) lines.remove(0);
        repaint();
    }

    private static void later(int ms, Runnable action) {
        Timer timer = new Timer(ms, e -> action.run());
        timer.setRepeats(false);
        timer.start();
    }

    private void tick() {
        tick++;
        sweepY = (sweepY + 2) % Math.max(1, getHeight());
        blank = stage() >= 3 && random.nextDouble() < 0.02;
        repaint();
    }

    // ---- drawing ----

    private Rectangle abortButton() {
        return new Rectangle(getWidth() - 26, getHeight() - 24, 14, 14);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics;
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setColor(BG);
        g.fillRect(0, 0, getWidth(), getHeight());
        if (blank) return;

        List<Line> wrapped = new ArrayList<>();
        for (Line line : lines) {
            for (String piece : wrap(g.getFontMetrics(line.bright() ? BOLD : MONO), line.text(), getWidth() - 2 * MARGIN)) {
                wrapped.add(new Line(piece, line.bright()));
            }
        }
        int rows = Math.max(1, (getHeight() - 20) / LINE_HEIGHT);
        int first = Math.max(0, wrapped.size() - (rows - 1));
        int row = 0;
        for (Line line : wrapped.subList(first, wrapped.size())) {
            String text = stage() >= 3 && random.nextDouble() < 0.01 ? corrupt(line.text()) : line.text();
            g.setFont(line.bright() ? BOLD : MONO);
            g.setColor(line.bright() ? BRIGHT : TEXT);
            g.drawString(text, MARGIN, baseline(row++));
        }

        String prompt = PROMPT + input;
        g.setFont(BOLD);
        g.setColor(BRIGHT);
        g.drawString(prompt, MARGIN, baseline(row));
        if ((tick / 8) % 2 == 0) {
            g.setColor(CURSOR);
            g.fillRect(MARGIN + g.getFontMetrics().stringWidth(prompt), baseline(row) - 12, 8, 14);
        }
        drawButton(g);
        drawScanlines(g);
        drawVignette(g);
    }

    private static int baseline(int row) {
        return 10 + (row + 1) * LINE_HEIGHT;
    }

    private static List<String> wrap(FontMetrics metrics, String text, int width) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ", -1)) {
            String next = current.isEmpty() ? word : current + " " + word;
            if (current.isEmpty() || metrics.stringWidth(next) <= width) {
                current = new StringBuilder(next);
            } else {
                result.add(current.toString());
                current = new StringBuilder(word);
            }
        }
        result.add(current.toString());
        return result;
    }

    private String corrupt(String text) {
        if (text.isEmpty()) return text;
        char[] chars = text.toCharArray();
        chars[random.nextInt(chars.length)] = (char) ('!' + random.nextInt(30));
        return new String(chars);
    }

    private void drawButton(Graphics2D g) {
        Rectangle b = abortButton();
        Color fill = stage() >= 4 ? BUTTON_ON : BUTTON_OFF;
        g.setColor(fill);
        g.fillOval(b.x, b.y, b.width, b.height);
        g.setColor(fill.brighter());
        g.drawOval(b.x, b.y, b.width, b.height);
        if (stage() >= 4) {
            Font small = new Font(Font.MONOSPACED, Font.PLAIN, 9);
            String label = t("abort");
            g.setFont(small);
            g.setColor(new Color(255, 80, 80, 180));
            g.drawString(label, b.x + 5 - g.getFontMetrics(small).stringWidth(label), b.y + 11);
        }
    }

    private void drawScanlines(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, stage() >= 3 ? 55 : 35));
        for (int y = 0; y < getHeight(); y += 3) g.fillRect(0, y, getWidth(), 1);
        if (stage() >= 3) {
            g.setColor(new Color(0, 255, 80, 12));
            g.fillRect(0, sweepY, getWidth(), 3);
        }
    }

    private void drawVignette(Graphics2D g) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float radius = Math.max(1f, (float) Math.sqrt(cx * cx + cy * cy));
        g.setPaint(new RadialGradientPaint(cx, cy, radius, new float[]{0.4f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, stage() >= 3 ? 200 : 160)}));
        g.fillRect(0, 0, getWidth(), getHeight());
    }
}