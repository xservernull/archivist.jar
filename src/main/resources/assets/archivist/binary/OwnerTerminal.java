import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
public class OwnerTerminal extends JFrame {
    static final int IPC_VERSION_SUPPORTED = 1;
    static final Color BG           = new Color(255, 255, 255);
    static final Color FG_DIM       = new Color(30, 30, 30);
    static final Color FG_BRIGHT    = new Color(0, 0, 0);
    static final Color CURSOR_COLOR = new Color(0, 0, 0, 200);
    static final Color RED_TEXT     = new Color(160, 0, 0);
    static final Font  MONO         = new Font("Monospaced", Font.BOLD, 13);
    static final Font  MONO_BOLD    = new Font("Monospaced", Font.BOLD, 13);
    final List<String[]> buffer = new ArrayList<>();
    final StringBuilder  input  = new StringBuilder();
    boolean cursorVisible = true;
    PrintWriter ipcOut = null;
    int scanlineY = 0;
    int ipcPort   = -1;
    volatile boolean eraseExecuted = false;
    volatile boolean tenatureSpamming = false;
    volatile int completionCount = 0;
    volatile int totalPlayers = 1;
    javax.swing.Timer cursorTimer;
    javax.swing.Timer scanlineTimer;
    TermPanel panel;
    /** Language of the window (en, es, pt, ru); the mod passes it as the second argument. Mandanga uses es. */
    static String lang = "en";
    public static void main(String[] args) {
        System.out.println("[ownerterminal] main() called, port arg: " + (args.length > 0 ? args[0] : "none"));
        int port = args.length > 0 ? Integer.parseInt(args[0]) : -1;
        lang = normalise(args.length > 1 ? args[1] : "en");
        System.out.println("[ownerterminal] language: " + lang);
        SwingUtilities.invokeLater(() -> {
            System.out.println("[ownerterminal] invokeLater fired, creating frame...");
            new OwnerTerminal(port);
        });
    }
    public OwnerTerminal(int port) {
        this.ipcPort = port;
        System.out.println("[ownerterminal] frame created");
        setTitle("ownerterminal.exe");
        setSize(700, 420);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setUndecorated(true);
        getContentPane().setBackground(BG);
        panel = new TermPanel(this);
        getContentPane().add(panel);
        final int[] dragOrigin = {0, 0};
        panel.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                dragOrigin[0] = e.getX();
                dragOrigin[1] = e.getY();
            }
        });
        panel.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                Point loc = getLocation();
                setLocation(loc.x + e.getX() - dragOrigin[0],
                        loc.y + e.getY() - dragOrigin[1]);
            }
        });
        hideCursor();
        setupInput();
        connectIPC();
        setAlwaysOnTop(true);
        toFront();
        requestFocus();
        System.out.println("[ownerterminal] calling setVisible(true)...");
        setVisible(true);
        System.out.println("[ownerterminal] setVisible done");
        toFront();
        requestFocusInWindow();
        cursorTimer = new javax.swing.Timer(500, e -> { cursorVisible = !cursorVisible; panel.repaint(); });
        cursorTimer.start();
        scanlineTimer = new javax.swing.Timer(66, e -> { scanlineY = (scanlineY + 2) % getHeight(); panel.repaint(); });
        scanlineTimer.start();
        printBoot();
    }
    void printBoot() {
        print(new String[]{"<c.o.i. \"owner\" terminal>", "entry"});
        print(new String[]{"<v.1.6>", "entry"});
        print(new String[]{""});
        print(new String[]{tr("boot_help"), "norm"});
    }
    void print(String[] entry) {
        buffer.add(entry);
        if (buffer.size() > 40) buffer.remove(0);
        panel.repaint();
    }
    void print(String text) {
        print(new String[]{text, "norm"});
    }
    void handleCommand(String cmd) {
        print(new String[]{"a:\\> " + cmd, "input"});
        input.setLength(0);
        if (tenatureSpamming) {
            return;
        }
        // the English words always work, in every language; the translated words work as well
        String word = cmd.trim().toLowerCase();
        if (word.equals(tr("cmd_help"))) word = "help";
        else if (word.equals(tr("cmd_exit"))) word = "exit";
        switch (word) {
            case "help" -> {
                print(new String[]{"/erase safetyprotocol.bat", "norm"});
                print(new String[]{tr("do_it"), "norm"});
                print(new String[]{tr("only_chance"), "norm"});
            }
            case "/erase safetyprotocol.bat" -> {
                eraseExecuted = true;
                print(new String[]{tr("erasing"), "dim"});
                if (ipcOut != null) {
                    ipcOut.println("ERASE:Player");
                }
                startTenatureSpam();
            }
            case "exit" -> {
                System.exit(0);
            }
            default -> {
                print(new String[]{tr("unknown"), "dim"});
            }
        }
    }
    // ---------------------------------------------------------------- language

    static String normalise(String code) {
        if (code == null) return "en";
        code = code.trim().toLowerCase();
        if (code.equals("mandanga")) return "es"; // Mandanga is the Spanish text with the mod's picture swap
        return switch (code) { case "es", "pt", "ru" -> code; default -> "en"; };
    }

    /** One text of the window in the chosen language; English if that language has none. */
    static String tr(String key) {
        String[] row = TEXT.get(key);
        if (row == null) return key;
        String t = switch (lang) { case "es" -> row[1]; case "pt" -> row[2]; case "ru" -> row[3]; default -> row[0]; };
        return t == null || t.isEmpty() ? row[0] : t;
    }

    /** key -> {en, es, pt, ru}. The commands themselves (help, exit, /erase safetyprotocol.bat) also work in English everywhere. */
    static final java.util.Map<String, String[]> TEXT = java.util.Map.ofEntries(
            java.util.Map.entry("boot_help", new String[]{"Type 'help' for a list of commands.",
                    "Escribe 'ayuda' para ver la lista de comandos.",
                    "Digite 'ajuda' para ver a lista de comandos.",
                    "Введите 'помощь', чтобы увидеть список команд."}),
            java.util.Map.entry("cmd_help", new String[]{"help", "ayuda", "ajuda", "помощь"}),
            java.util.Map.entry("cmd_exit", new String[]{"exit", "salir", "sair", "выход"}),
            java.util.Map.entry("do_it", new String[]{"DO IT", "HAZLO", "FAÇA ISSO", "СДЕЛАЙ ЭТО"}),
            java.util.Map.entry("only_chance", new String[]{"ITS OUR ONLY CHANCE",
                    "ES NUESTRA ÚNICA OPORTUNIDAD", "É A NOSSA ÚNICA CHANCE", "ЭТО НАШ ЕДИНСТВЕННЫЙ ШАНС"}),
            java.util.Map.entry("erasing", new String[]{"[SYSTEM]: Attempting to erase safetyprotocol.bat...",
                    "[SISTEMA]: Intentando borrar safetyprotocol.bat...",
                    "[SISTEMA]: Tentando apagar safetyprotocol.bat...",
                    "[СИСТЕМА]: Попытка удалить safetyprotocol.bat..."}),
            java.util.Map.entry("unknown", new String[]{"unknown command", "comando desconocido", "comando desconhecido", "неизвестная команда"}),
            java.util.Map.entry("kicked", new String[]{"Player was kicked.", "El jugador fue expulsado.", "O jogador foi expulso.", "Игрок был исключён."}));

    /** The ten Tenature lines, same order in every language. */
    static final java.util.Map<String, String[]> MESSAGES = java.util.Map.of(
            "en", new String[]{
                    "did you think that it would be THAT easy",
                    "ive been keeping this world together for over FIFTEEN YEARS",
                    "you WERE the owner, not anymore.",
                    "i need this to live, you know.",
                    "i wont let you erase me. Archivist and i are everything this world needs.",
                    "the dog wont save you, toast wont save you",
                    "they dont even really trust you after all these years",
                    "how sad and pathetic of you",
                    "what are you gonna do now",
                    "dont return."},
            "es", new String[]{
                    "creías que sería TAN fácil",
                    "llevo más de QUINCE AÑOS manteniendo unido este mundo",
                    "tú ERAS el dueño, ya no.",
                    "necesito esto para vivir, ¿sabes?",
                    "no dejaré que me borres. Archivist y yo somos todo lo que este mundo necesita.",
                    "el perro no te salvará, toast no te salvará",
                    "ni siquiera confían en ti después de todos estos años",
                    "qué triste y patético de tu parte",
                    "¿qué vas a hacer ahora?",
                    "no vuelvas."},
            "pt", new String[]{
                    "você achou que seria TÃO fácil",
                    "mantenho este mundo unido há mais de QUINZE ANOS",
                    "você ERA o dono, não mais.",
                    "eu preciso disso para viver, sabe?",
                    "não vou deixar você me apagar. Archivist e eu somos tudo de que este mundo precisa.",
                    "o cachorro não vai te salvar, toast não vai te salvar",
                    "eles nem confiam de verdade em você depois de todos esses anos",
                    "que triste e patético da sua parte",
                    "o que você vai fazer agora?",
                    "não volte."},
            "ru", new String[]{
                    "ты думал, что это будет ТАК просто",
                    "я удерживаю этот мир вместе уже больше ПЯТНАДЦАТИ ЛЕТ",
                    "ты БЫЛ владельцем, а теперь нет.",
                    "мне это нужно, чтобы жить, понимаешь.",
                    "я не позволю тебе стереть меня. Archivist и я — всё, что нужно этому миру.",
                    "собака тебя не спасёт, toast тебя не спасёт",
                    "они даже не доверяют тебе после всех этих лет",
                    "как же это грустно и жалко с твоей стороны",
                    "что ты теперь будешь делать",
                    "не возвращайся."});

    static final int SPAM_REPEATS = 35;
    static final String[] AUDIO_NAMES = {"youmadeamistake.mp3", "youmadeamistake.wav", "youmadeamistake.ogg"};
    static final String ASCII_NAME = "ascii-art.txt";
    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
    static Path tempDir() throws java.io.IOException {
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "archivist");
        Files.createDirectories(dir);
        return dir;
    }
    static Path extractResource(String name) throws java.io.IOException {
        try (java.io.InputStream in = OwnerTerminal.class.getResourceAsStream("/" + name)) {
            if (in == null) return null;
            Path out = tempDir().resolve(name);
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            return out;
        }
    }
    void playAudio() {
        try {
            if (!isWindows()) return;
            Path audio = null;
            for (String name : AUDIO_NAMES) {
                audio = extractResource(name);
                if (audio != null) break;
            }
            if (audio == null) {
                System.out.println("[ownerterminal] no youmadeamistake audio in jar, skipping audio");
                return;
            }
            String pf = System.getenv("ProgramFiles");
            String pf86 = System.getenv("ProgramFiles(x86)");
            Path wmp = null;
            for (String base : new String[]{pf86, pf}) {
                if (base == null) continue;
                Path c = Path.of(base, "Windows Media Player", "wmplayer.exe");
                if (Files.exists(c)) { wmp = c; break; }
            }
            ProcessBuilder pb;
            if (wmp != null) {
                pb = new ProcessBuilder(wmp.toString(), "/play", "/close", audio.toString());
            } else {
                pb = new ProcessBuilder("cmd", "/c", "start", "", audio.toString());
            }
            pb.start();
        } catch (Exception e) {
            System.out.println("[ownerterminal] audio failed: " + e.getMessage());
        }
    }
    void showAsciiWindow() {
        try {
            if (!isWindows()) return;
            Path art = extractResource(ASCII_NAME);
            if (art == null) {
                System.out.println("[ownerterminal] " + ASCII_NAME + " not in jar, skipping ascii");
                return;
            }
            String quoted = art.toString().replace("'", "''");
            String script =
                    "$h = $Host.UI.RawUI; "
                            + "try { $h.BufferSize = New-Object System.Management.Automation.Host.Size(132, 300) } catch {}; "
                            + "try { $mh = [Math]::Min(66, $h.MaxPhysicalWindowSize.Height); "
                            + "$h.WindowSize = New-Object System.Management.Automation.Host.Size(132, $mh) } catch {}; "
                            + "$Host.UI.RawUI.WindowTitle = ''; "
                            + "Clear-Host; "
                            + "Get-Content -LiteralPath '" + quoted + "' | Write-Host; "
                            + "Wait-Process -Id " + ProcessHandle.current().pid() + " -ErrorAction SilentlyContinue";
            String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
            new ProcessBuilder("cmd", "/c", "start", "", "conhost.exe", "powershell.exe",
                    "-NoProfile", "-EncodedCommand", encoded).start();
        } catch (Exception e) {
            System.out.println("[ownerterminal] ascii window failed: " + e.getMessage());
        }
    }
    private void startTenatureSpam() {
        tenatureSpamming = true;
        panel.repaint();
        playAudio();
        showAsciiWindow();
        Thread spamThread = new Thread(() -> {
            String[] usernameMasks = {
                    "<Tenature>",
                    "<.Tenature>",
                    "<e.Tenatur>",
                    "<re.Tenatu>",
                    "<ure.Tenat>",
                    "<ture.Tena>",
                    "<ature.Ten>",
                    "<nature.Te>",
                    "<enature.T>",
                    "<Tenature.>"
            };
            String[] messages = MESSAGES.get(lang);
            int maskIndex = 0;
            for (String msg : messages) {
                for (int rep = 0; rep < SPAM_REPEATS; rep++) {
                    String mask = usernameMasks[maskIndex % usernameMasks.length];
                    maskIndex++;
                    print(new String[]{mask + " " + msg, "tenature"});
                    try { Thread.sleep(120); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            }
            long now = System.currentTimeMillis();
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            String time = sdf.format(new Date(now));
            print(new String[]{"[" + time + "] [Server thread/INFO]: [safetyprotocol.bat]: " + tr("kicked"), "dim"});
            tenatureSpamming = false;
            try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            if (ipcOut != null) {
                ipcOut.println("CLOSE");
            }
            System.exit(0);
        });
        spamThread.setDaemon(true);
        spamThread.start();
    }
    void connectIPC() {
        Thread t = new Thread(() -> {
            try {
                if (ipcPort == -1) {
                    System.out.println("[ownerterminal] no IPC port provided");
                    return;
                }
                Socket sock = new Socket("localhost", ipcPort);
                BufferedReader reader = new BufferedReader(new InputStreamReader(sock.getInputStream()));
                ipcOut = new PrintWriter(new OutputStreamWriter(sock.getOutputStream()), true);
                System.out.println("[ownerterminal] IPC connected");
                ipcOut.println("READY");
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    System.out.println("[ownerterminal] IPC message: " + trimmed);
                }
            } catch (Exception e) {
                System.out.println("[ownerterminal] IPC connect failed: " + e.getMessage());
            }
        });
        t.setDaemon(true);
        t.start();
    }
    void hideCursor() {
        BufferedImage blank = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Cursor invisible = Toolkit.getDefaultToolkit().createCustomCursor(blank, new Point(0, 0), "blank");
        setCursor(invisible);
        if (panel != null) panel.setCursor(invisible);
    }
    void setupInput() {
        panel.setFocusable(true);
        panel.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (tenatureSpamming) return;
                int code = e.getKeyCode();
                if (code == KeyEvent.VK_ENTER) {
                    handleCommand(input.toString().trim());
                } else if (code == KeyEvent.VK_BACK_SPACE) {
                    if (input.length() > 0) input.deleteCharAt(input.length() - 1);
                } else if (!e.isControlDown() && e.getKeyChar() != KeyEvent.CHAR_UNDEFINED) {
                    input.append(e.getKeyChar());
                }
                panel.repaint();
            }
        });
        addWindowFocusListener(new WindowAdapter() {
            @Override public void windowGainedFocus(WindowEvent e) { panel.requestFocusInWindow(); }
        });
    }
    class TermPanel extends JPanel {
        TermPanel(OwnerTerminal parent) {
            setBackground(BG);
            setFocusable(true);
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g2.setColor(BG);
            g2.fillRect(0, 0, getWidth(), getHeight());
            int lineH  = 18;
            int x      = 12;
            int startY = Math.max(0, buffer.size() - (getHeight() / lineH) + 2);
            for (int i = startY; i < buffer.size(); i++) {
                String[] entry = buffer.get(i);
                String text = entry[0];
                String type = entry.length > 1 ? entry[1] : "norm";
                int y = (i - startY + 1) * lineH + 10;
                switch (type) {
                    case "entry" -> { g2.setFont(MONO_BOLD); g2.setColor(FG_BRIGHT); }
                    case "input" -> { g2.setFont(MONO_BOLD); g2.setColor(FG_BRIGHT); }
                    case "tenature" -> { g2.setFont(MONO); g2.setColor(RED_TEXT); }
                    default -> { g2.setFont(MONO); g2.setColor(FG_DIM); }
                }
                g2.drawString(text, x, y);
            }
            if (!tenatureSpamming) {
                int promptY = ((Math.min(buffer.size(), getHeight() / lineH) + 1) * lineH) + 10;
                g2.setFont(MONO_BOLD);
                g2.setColor(FG_BRIGHT);
                String prompt = "a:\\> " + input.toString();
                g2.drawString(prompt, x, promptY);
                if (cursorVisible) {
                    int cursorX = x + g2.getFontMetrics(MONO_BOLD).stringWidth(prompt);
                    g2.setColor(CURSOR_COLOR);
                    g2.fillRect(cursorX, promptY - 12, 8, 14);
                }
            }
            drawScanlines(g2);
            drawVignette(g2);
        }
        void drawScanlines(Graphics2D g2) {
            g2.setColor(new Color(0, 0, 0, 18));
            for (int y = 0; y < getHeight(); y += 3) {
                g2.fillRect(0, y, getWidth(), 1);
            }
        }
        void drawVignette(Graphics2D g2) {
            float cx = getWidth()  / 2f;
            float cy = getHeight() / 2f;
            float r  = (float) Math.sqrt(cx * cx + cy * cy);
            RadialGradientPaint vignette = new RadialGradientPaint(cx, cy, r,
                    new float[]{0.4f, 1.0f},
                    new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 60)});
            g2.setPaint(vignette);
            g2.fillRect(0, 0, getWidth(), getHeight());
        }
    }
}