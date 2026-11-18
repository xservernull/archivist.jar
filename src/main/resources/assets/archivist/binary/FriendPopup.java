import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;
public class FriendPopup extends JFrame {
    private static final int    WINDOW_W          = 220;
    private static final int    WINDOW_H          = 240;
    private static final int    TITLEBAR_H        = 22;
    private static final int    IMAGE_PADDING     = 12;
    private static final int    CLOSE_BTN_SIZE    = 16;
    private static final int    FIRST_SPAWN_MS    = 600;
    private static final int    MIN_SPAWN_MS      = 80;
    private static final int    RAMP_STEPS        = 18;
    private static final int    MAX_WINDOWS       = 120;
    private static final Color  TITLEBAR_COLOR    = new Color(0x0A246A);
    private static final Color  TITLEBAR_ACTIVE   = new Color(0x1659C7);
    private static final Color  TITLEBAR_TEXT     = Color.WHITE;
    private static final Color  BG_COLOR          = new Color(0xECE9D8);
    private static final Color  BORDER_COLOR      = new Color(0x404040);
    private static final Color  CLOSE_BG          = new Color(0xD64545);
    private static final Color  CLOSE_HOVER_BG    = new Color(0xFF2222);
    private static final Font   TITLE_FONT        = new Font("Dialog", Font.BOLD, 11);
    private static final Font   MSG_FONT          = new Font("Dialog", Font.PLAIN, 11);
    private static final String[] TITLES = {
            "friend.exe", "look at me", "IMG_0001.png", "hello :)",
            "new message", "photo.jpg", "u there?", "come find me",
            "System Message", "open me", "friend_found.jpg", "hi"
    };
    private static final String[] MESSAGES = {
            "youareanidiot", "i found you", "hello there", "don't close this",
            "we are friends", "look at my face", "i am here", "you cannot hide"
    };
    private static final List<BufferedImage> TEXTURES   = new ArrayList<>();
    private static final Random              RNG        = new Random();
    private static volatile int              windowCount = 0;
    private static volatile int              generation  = 0;
    private final BufferedImage texture;
    private final String        title;
    private final String        message;
    private       boolean       closeHovered = false;
    public static void main(String[] args) {
        loadTextures();
        SwingUtilities.invokeLater(FriendPopup::spawnWindow);
    }
    private static void loadTextures() {
        String[] names = {"friend1.png", "friend2.png", "friend3.png", "friend4.png"};
        for (String name : names) {
            try (InputStream is = FriendPopup.class.getResourceAsStream("/textures/" + name)) {
                if (is != null) TEXTURES.add(ImageIO.read(is));
            } catch (IOException ignored) {}
        }
        if (TEXTURES.isEmpty()) {
            for (String path : new String[]{"/textures/friend.png", "/friend.png"}) {
                try (InputStream is = FriendPopup.class.getResourceAsStream(path)) {
                    if (is != null) { TEXTURES.add(ImageIO.read(is)); break; }
                } catch (IOException ignored) {}
            }
        }
        if (TEXTURES.isEmpty()) {
            BufferedImage fallback = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = fallback.createGraphics();
            g.setColor(Color.MAGENTA); g.fillRect(0, 0, 64, 64); g.dispose();
            TEXTURES.add(fallback);
        }
    }
    private static BufferedImage randomTexture() {
        return TEXTURES.get(RNG.nextInt(TEXTURES.size()));
    }
    private static void spawnWindow() {
        if (windowCount >= MAX_WINDOWS) return;
        windowCount++;
        generation++;
        new FriendPopup();
        int delay = Math.max(MIN_SPAWN_MS,
                FIRST_SPAWN_MS - (generation * (FIRST_SPAWN_MS - MIN_SPAWN_MS) / RAMP_STEPS));
        int children = (generation > RAMP_STEPS / 2) ? 2 : 1;
        for (int i = 0; i < children; i++) {
            final int d = delay + i * 40;
            Timer t = new Timer(d, e -> {
                if (windowCount < MAX_WINDOWS) spawnWindow();
            });
            t.setRepeats(false);
            t.start();
        }
    }
    public FriendPopup() {
        this.texture = randomTexture();
        this.title   = TITLES[RNG.nextInt(TITLES.length)];
        this.message = MESSAGES[RNG.nextInt(MESSAGES.length)];
        setUndecorated(true);
        setSize(WINDOW_W, WINDOW_H);
        setAlwaysOnTop(true);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Rectangle screen = ge.getMaximumWindowBounds();
        int x = screen.x + RNG.nextInt(Math.max(1, screen.width  - WINDOW_W));
        int y = screen.y + RNG.nextInt(Math.max(1, screen.height - WINDOW_H));
        setLocation(x, y);
        JPanel panel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                int w = getWidth(), h = getHeight();
                g2.setColor(BORDER_COLOR);
                g2.drawRect(0, 0, w - 1, h - 1);
                GradientPaint grad = new GradientPaint(
                        0, 0, TITLEBAR_ACTIVE,
                        0, TITLEBAR_H, TITLEBAR_COLOR);
                g2.setPaint(grad);
                g2.fillRect(1, 1, w - 2, TITLEBAR_H - 1);
                g2.setFont(TITLE_FONT);
                g2.setColor(TITLEBAR_TEXT);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                g2.drawString(title, 6, TITLEBAR_H - 6);
                int cx = w - CLOSE_BTN_SIZE - 4;
                int cy = (TITLEBAR_H - CLOSE_BTN_SIZE) / 2;
                g2.setColor(closeHovered ? CLOSE_HOVER_BG : CLOSE_BG);
                g2.fillRect(cx, cy, CLOSE_BTN_SIZE, CLOSE_BTN_SIZE);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Dialog", Font.BOLD, 10));
                g2.drawString("✕", cx + 3, cy + CLOSE_BTN_SIZE - 3);
                g2.setColor(BG_COLOR);
                g2.fillRect(1, TITLEBAR_H, w - 2, h - TITLEBAR_H - 1);
                int imgAreaW = w - IMAGE_PADDING * 2;
                int imgAreaH = h - TITLEBAR_H - IMAGE_PADDING * 2 - 20;
                int drawW = imgAreaW, drawH = imgAreaH;
                float aspect = (float) texture.getWidth() / texture.getHeight();
                if (drawW > drawH * aspect) drawW = (int)(drawH * aspect);
                else drawH = (int)(drawW / aspect);
                int imgX = IMAGE_PADDING + (imgAreaW - drawW) / 2;
                int imgY = TITLEBAR_H + IMAGE_PADDING;
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.drawImage(texture, imgX, imgY, drawW, drawH, null);
                g2.setFont(MSG_FONT);
                g2.setColor(Color.BLACK);
                FontMetrics fm = g2.getFontMetrics();
                int msgX = (w - fm.stringWidth(message)) / 2;
                int msgY = h - 8;
                g2.drawString(message, msgX, msgY);
            }
        };
        panel.setPreferredSize(new Dimension(WINDOW_W, WINDOW_H));
        final int[] drag = {0, 0};
        panel.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { drag[0] = e.getX(); drag[1] = e.getY(); }
            @Override public void mouseReleased(MouseEvent e) {
                int cx = getWidth() - CLOSE_BTN_SIZE - 4;
                int cy = (TITLEBAR_H - CLOSE_BTN_SIZE) / 2;
                if (e.getX() >= cx && e.getX() <= cx + CLOSE_BTN_SIZE
                        && e.getY() >= cy && e.getY() <= cy + CLOSE_BTN_SIZE) {
                    dispose();
                    windowCount = Math.max(0, windowCount - 1);
                }
            }
            @Override public void mouseEntered(MouseEvent e) {}
        });
        panel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                Point loc = getLocation();
                setLocation(loc.x + e.getX() - drag[0], loc.y + e.getY() - drag[1]);
            }
            @Override public void mouseMoved(MouseEvent e) {
                int cx = getWidth() - CLOSE_BTN_SIZE - 4;
                int cy = (TITLEBAR_H - CLOSE_BTN_SIZE) / 2;
                boolean over = e.getX() >= cx && e.getX() <= cx + CLOSE_BTN_SIZE
                        && e.getY() >= cy && e.getY() <= cy + CLOSE_BTN_SIZE;
                if (over != closeHovered) { closeHovered = over; panel.repaint(); }
            }
        });
        add(panel);
        pack();
        setVisible(true);
        toFront();
    }
}