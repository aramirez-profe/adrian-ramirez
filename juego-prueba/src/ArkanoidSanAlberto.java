import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.TextAttribute;
import java.util.*;

/**
 * SAN ALBERTO · Arkanoid
 * JDK 21 — sin librerías externas (solo Swing/AWT incluidos en el JDK).
 * Estética de colores mate (planos, sin brillos ni degradados).
 *
 * Compilar : javac ArkanoidSanAlberto.java
 * Ejecutar : java ArkanoidSanAlberto
 */
public class ArkanoidSanAlberto extends JPanel implements ActionListener {

    // ================= Dimensiones =================
    private static final int W = 820;
    private static final int H = 640;
    private static final int HEADER = 78;

    // ================= Paleta mate =================
    private static final Color BG        = new Color(43, 42, 46);
    private static final Color HEADER_BG = new Color(54, 52, 58);
    private static final Color TEXT      = new Color(230, 225, 214);
    private static final Color TEXT_DIM  = new Color(150, 146, 138);
    private static final Color ACCENT    = new Color(199, 148, 136);
    private static final Color PADDLE_C  = new Color(205, 197, 184);
    private static final Color BALL_C    = new Color(238, 232, 218);

    private static final Color[] BRICK_COLORS = {
        new Color(196, 138, 132),   // arcilla
        new Color(206, 168, 122),   // arena
        new Color(176, 178, 130),   // oliva
        new Color(132, 164, 156),   // salvia
        new Color(130, 150, 178),   // azul acero
        new Color(160, 138, 168)    // malva
    };

    // ================= Fuentes =================
    private static final Font F_TITLE = tracked(new Font(Font.SANS_SERIF, Font.BOLD, 30), 0.20f);
    private static final Font F_BIG   = tracked(new Font(Font.SANS_SERIF, Font.BOLD, 52), 0.18f);
    private static final Font F_SUB   = tracked(new Font(Font.SANS_SERIF, Font.BOLD, 19), 0.55f);
    private static final Font F_HUD   = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    private static final Font F_UI    = new Font(Font.SANS_SERIF, Font.PLAIN, 15);

    private static Font tracked(Font base, float t) {
        Map<TextAttribute, Object> at = new HashMap<>();
        at.put(TextAttribute.TRACKING, t);
        return base.deriveFont(at);
    }

    // ================= Estado =================
    private enum State { READY, PLAYING, PAUSED, GAME_OVER, WIN }

    private static final int START_LIVES = 3;
    private static final int MAX_LEVEL = 5;
    private static final double BALL_R = 7;
    private static final int PADDLE_W = 112, PADDLE_H = 14;
    private static final int PADDLE_Y = H - 46;
    private static final double PADDLE_SPEED = 9;
    private static final double MAX_BOUNCE = Math.toRadians(62);

    private State state = State.READY;
    private int score = 0, best = 0, lives = START_LIVES, level = 1;
    private boolean everStarted = false;

    private double bx = W / 2.0, by = PADDLE_Y - BALL_R;
    private double bvx = 0, bvy = 0;
    private double px = (W - PADDLE_W) / 2.0;
    private boolean movingLeft = false, movingRight = false;

    private static class Brick {
        double x, y, w, h;
        Color color;
        int points;
        boolean alive = true;
    }

    private static class Particle {
        double x, y, vx, vy;
        int size, life, maxLife;
        Color color;
    }

    private final ArrayList<Brick> bricks = new ArrayList<>();
    private final ArrayList<Particle> particles = new ArrayList<>();
    private final Timer timer;

    // ================= Constructor =================
    public ArkanoidSanAlberto() {
        setPreferredSize(new Dimension(W, H));
        setBackground(BG);
        setFocusable(true);
        buildLevel();

        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT, KeyEvent.VK_A  -> movingLeft = true;
                    case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> movingRight = true;
                    case KeyEvent.VK_SPACE -> action();
                    case KeyEvent.VK_P     -> togglePause();
                }
            }
            @Override public void keyReleased(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT, KeyEvent.VK_A  -> movingLeft = false;
                    case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> movingRight = false;
                }
            }
        });

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e)   { movePaddleToMouse(e); }
            @Override public void mouseDragged(MouseEvent e) { movePaddleToMouse(e); }
            @Override public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                action();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);

        timer = new Timer(16, this);  // ~60 fps
        timer.start();
    }

    private void movePaddleToMouse(MouseEvent e) {
        if (state == State.PAUSED) return;
        px = clampPaddle(e.getX() - PADDLE_W / 2.0);
    }

    private double clampPaddle(double v) {
        return Math.max(12, Math.min(W - 12 - PADDLE_W, v));
    }

    // ================= Bucle =================
    @Override public void actionPerformed(ActionEvent e) {
        update();
        repaint();
    }

    private void update() {
        updateParticles();
        if (score > best) best = score;
        if (state == State.PAUSED || state == State.GAME_OVER || state == State.WIN) return;

        // Pala con teclado
        if (movingLeft)  px -= PADDLE_SPEED;
        if (movingRight) px += PADDLE_SPEED;
        px = clampPaddle(px);

        // Bola pegada a la pala antes de lanzar
        if (state == State.READY) {
            bx = px + PADDLE_W / 2.0;
            by = PADDLE_Y - BALL_R - 1;
            return;
        }

        // Movimiento de la bola
        bx += bvx;
        by += bvy;

        // Rebotes con paredes
        if (bx - BALL_R < 0)        { bx = BALL_R;     bvx = Math.abs(bvx); }
        else if (bx + BALL_R > W)   { bx = W - BALL_R; bvx = -Math.abs(bvx); }
        if (by - BALL_R < HEADER)   { by = HEADER + BALL_R; bvy = Math.abs(bvy); }

        // Bola perdida
        if (by - BALL_R > H) { loseLife(); return; }

        // Rebote con la pala (ángulo según el punto de impacto)
        if (bvy > 0
                && by + BALL_R >= PADDLE_Y && by - BALL_R <= PADDLE_Y + PADDLE_H
                && bx >= px - BALL_R && bx <= px + PADDLE_W + BALL_R) {
            double off = Math.max(-1, Math.min(1, (bx - (px + PADDLE_W / 2.0)) / (PADDLE_W / 2.0)));
            double ang = off * MAX_BOUNCE;
            double s = speedForLevel();
            bvx = s * Math.sin(ang);
            bvy = -s * Math.cos(ang);
            by = PADDLE_Y - BALL_R;
        }

        // Rebotes con bloques
        for (Brick k : bricks) {
            if (!k.alive) continue;
            double nx = Math.max(k.x, Math.min(bx, k.x + k.w));
            double ny = Math.max(k.y, Math.min(by, k.y + k.h));
            double dx = bx - nx, dy = by - ny;
            if (dx * dx + dy * dy > BALL_R * BALL_R) continue;

            k.alive = false;
            score += k.points;
            spawnParticles(k);

            if (Math.abs(dx) > Math.abs(dy)) bvx = dx > 0 ? Math.abs(bvx) : -Math.abs(bvx);
            else                             bvy = dy > 0 ? Math.abs(bvy) : -Math.abs(bvy);
            break;
        }

        // ¿Nivel completado?
        boolean remaining = false;
        for (Brick k : bricks) if (k.alive) { remaining = true; break; }
        if (!remaining) {
            if (level >= MAX_LEVEL) {
                state = State.WIN;
            } else {
                level++;
                buildLevel();
                state = State.READY;
            }
        }
    }

    private void loseLife() {
        lives--;
        state = (lives <= 0) ? State.GAME_OVER : State.READY;
    }

    private void action() {
        switch (state) {
            case READY -> launchBall();
            case PAUSED -> state = State.PLAYING;
            case GAME_OVER, WIN -> resetGame();
            default -> {}
        }
    }

    private void togglePause() {
        if (state == State.PLAYING) state = State.PAUSED;
        else if (state == State.PAUSED) state = State.PLAYING;
    }

    private void launchBall() {
        double s = speedForLevel();
        double ang = Math.toRadians(-18 + Math.random() * 36);
        bvx = s * Math.sin(ang);
        bvy = -s * Math.cos(ang);
        state = State.PLAYING;
        everStarted = true;
    }

    private double speedForLevel() {
        return Math.min(4.2 + (level - 1) * 0.5, 7.2);
    }

    private void resetGame() {
        score = 0;
        lives = START_LIVES;
        level = 1;
        particles.clear();
        buildLevel();
        state = State.READY;
        everStarted = false;
    }

    private void buildLevel() {
        bricks.clear();
        int rows = Math.min(3 + level, BRICK_COLORS.length);
        int cols = 10;
        double gap = 6, margin = 26;
        double bw = (W - 2 * margin - (cols - 1) * gap) / cols;
        double bh = 22;
        double top = HEADER + 26;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Brick k = new Brick();
                k.x = margin + c * (bw + gap);
                k.y = top + r * (bh + gap);
                k.w = bw;
                k.h = bh;
                k.color = BRICK_COLORS[r % BRICK_COLORS.length];
                k.points = (rows - r) * 10;
                bricks.add(k);
            }
        }
    }

    private void spawnParticles(Brick k) {
        for (int i = 0; i < 14; i++) {
            Particle p = new Particle();
            p.x = k.x + Math.random() * k.w;
            p.y = k.y + Math.random() * k.h;
            double ang = Math.random() * Math.PI * 2;
            double sp = 0.6 + Math.random() * 2.4;
            p.vx = Math.cos(ang) * sp;
            p.vy = Math.sin(ang) * sp - 1.2;
            p.size = 3 + (int) (Math.random() * 3);
            p.maxLife = 22 + (int) (Math.random() * 14);
            p.life = p.maxLife;
            p.color = k.color;
            particles.add(p);
        }
    }

    private void updateParticles() {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.x += p.vx;
            p.y += p.vy;
            p.vy += 0.12;
            if (--p.life <= 0) particles.remove(i);
        }
    }

    // ================= Pintado =================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2.setColor(BG);
        g2.fillRect(0, 0, W, H);

        // Bloques (mate, esquinas suaves)
        for (Brick k : bricks) {
            if (!k.alive) continue;
            g2.setColor(k.color);
            g2.fillRoundRect((int) k.x, (int) k.y, (int) k.w, (int) k.h, 7, 7);
        }

        // Pala
        g2.setColor(PADDLE_C);
        g2.fillRoundRect((int) px, PADDLE_Y, PADDLE_W, PADDLE_H, PADDLE_H, PADDLE_H);

        // Bola
        g2.setColor(BALL_C);
        g2.fillOval((int) (bx - BALL_R), (int) (by - BALL_R), (int) (BALL_R * 2), (int) (BALL_R * 2));

        // Partículas
        for (Particle p : particles) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    Math.max(0f, p.life / (float) p.maxLife)));
            g2.setColor(p.color);
            g2.fillRect((int) p.x, (int) p.y, p.size, p.size);
        }
        g2.setComposite(AlphaComposite.SrcOver);

        drawHeader(g2);
        drawOverlays(g2);
    }

    private void drawHeader(Graphics2D g2) {
        g2.setColor(HEADER_BG);
        g2.fillRect(0, 0, W, HEADER);
        g2.setColor(ACCENT);
        g2.fillRect(0, HEADER - 2, W, 2);

        // Título
        g2.setColor(TEXT);
        g2.setFont(F_TITLE);
        drawCentered(g2, "SAN ALBERTO", W / 2, 40);

        // Contadores
        g2.setFont(F_HUD);
        FontMetrics fm = g2.getFontMetrics();
        int y = 66;

        int x = 26;
        g2.setColor(TEXT_DIM);  g2.drawString("PUNTOS", x, y);
        x += fm.stringWidth("PUNTOS") + 8;
        g2.setColor(TEXT);
        String sc = String.format("%06d", score);
        g2.drawString(sc, x, y);
        x += fm.stringWidth(sc) + 24;
        g2.setColor(TEXT_DIM);  g2.drawString("RÉCORD", x, y);
        x += fm.stringWidth("RÉCORD") + 8;
        g2.setColor(TEXT);      g2.drawString(String.format("%06d", best), x, y);

        g2.setColor(TEXT_DIM);
        drawCentered(g2, "NIVEL " + level, W / 2, y);

        // Vidas (puntitos a la derecha)
        String vt = "VIDAS";
        int dot = 9, gapDots = 6;
        int total = fm.stringWidth(vt) + 10 + lives * dot + Math.max(0, lives - 1) * gapDots;
        int vx = W - 26 - total;
        g2.drawString(vt, vx, y);
        g2.setColor(PADDLE_C);
        int dx = vx + fm.stringWidth(vt) + 10;
        for (int i = 0; i < lives; i++) {
            g2.fillOval(dx, y - dot + 1, dot, dot);
            dx += dot + gapDots;
        }
    }

    private void drawOverlays(Graphics2D g2) {
        boolean blink = (System.currentTimeMillis() / 450) % 2 == 0;

        if (state == State.READY && !everStarted) {
            dimPlayfield(g2);
            g2.setColor(TEXT);   g2.setFont(F_BIG);
            drawCentered(g2, "SAN ALBERTO", W / 2, HEADER + 190);
            g2.setColor(ACCENT); g2.setFont(F_SUB);
            drawCentered(g2, "ARKANOID", W / 2, HEADER + 232);
            g2.setColor(TEXT_DIM); g2.setFont(F_UI);
            drawCentered(g2, "Mover: ratón o flechas ← →", W / 2, HEADER + 300);
            drawCentered(g2, "Lanzar: ESPACIO o clic · Pausa: P", W / 2, HEADER + 326);
            if (blink) {
                g2.setColor(TEXT);
                drawCentered(g2, "Pulsa ESPACIO para empezar", W / 2, HEADER + 386);
            }
        }

        if (state == State.READY && everStarted) {
            g2.setColor(TEXT_DIM); g2.setFont(F_UI);
            drawCentered(g2, "ESPACIO o CLIC para lanzar", W / 2, H - 14);
        }

        if (state == State.PAUSED) {
            dimPlayfield(g2);
            g2.setColor(TEXT); g2.setFont(F_BIG.deriveFont(38f));
            drawCentered(g2, "PAUSA", W / 2, HEADER + 220);
            g2.setColor(TEXT_DIM); g2.setFont(F_UI);
            drawCentered(g2, "Pulsa P para continuar", W / 2, HEADER + 260);
        }

        if (state == State.GAME_OVER) {
            dimPlayfield(g2);
            g2.setColor(ACCENT); g2.setFont(F_BIG.deriveFont(40f));
            drawCentered(g2, "FIN DE LA PARTIDA", W / 2, HEADER + 200);
            g2.setColor(TEXT); g2.setFont(F_UI);
            drawCentered(g2, "Puntuación: " + score, W / 2, HEADER + 248);
            if (blink) {
                g2.setColor(TEXT_DIM);
                drawCentered(g2, "ESPACIO para reiniciar", W / 2, HEADER + 300);
            }
        }

        if (state == State.WIN) {
            dimPlayfield(g2);
            g2.setColor(ACCENT); g2.setFont(F_BIG.deriveFont(40f));
            drawCentered(g2, "¡HAS GANADO!", W / 2, HEADER + 200);
            g2.setColor(TEXT); g2.setFont(F_UI);
            drawCentered(g2, "Puntuación final: " + score, W / 2, HEADER + 248);
            if (blink) {
                g2.setColor(TEXT_DIM);
                drawCentered(g2, "ESPACIO para jugar otra vez", W / 2, HEADER + 300);
            }
        }
    }

    private void dimPlayfield(Graphics2D g2) {
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
        g2.setColor(new Color(24, 23, 26));
        g2.fillRect(0, HEADER, W, H - HEADER);
        g2.setComposite(AlphaComposite.SrcOver);
    }

    private void drawCentered(Graphics2D g2, String s, int cx, int baselineY) {
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(s, cx - fm.stringWidth(s) / 2, baselineY);
    }

    // ================= Entrada =================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("San Alberto · Arkanoid");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            ArkanoidSanAlberto game = new ArkanoidSanAlberto();
            frame.add(game);
            frame.pack();
            frame.setResizable(false);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            game.requestFocusInWindow();
        });
    }
}