import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
public class SnakeGame extends JPanel implements ActionListener {
    // États du jeu
    private enum GameState {
        MENU,
        PLAYING,
        PAUSED,
        GAME_OVER
    }

    // Niveaux de difficulté
    public enum Difficulty {
        EASY(2.0, 1.0, "Facile"),
        MEDIUM(3.0, 1.2, "Normal"),
        HARD(4.0, 1.5, "Difficile"),
        EXPERT(5.0, 2.0, "Expert");

        final double speed;
        final double scoreMultiplier;
        final String label;

        Difficulty(double speed, double scoreMultiplier, String label) {
            this.speed = speed;
            this.scoreMultiplier = scoreMultiplier;
            this.label = label;
        }
    }

    // Types de pommes
    private enum AppleType {
        BASIC(Color.RED, 1, "Normal (+1 point)"),
        GOLDEN(Color.YELLOW, 3, "Or (+3 points)"),
        SPEED(Color.GREEN, 1, "Vitesse (+1 point, vitesse x2)"),
        SLOW(Color.BLUE, 1, "Ralenti (+1 point, vitesse /2)"),
        RAINBOW(Color.MAGENTA, 2, "Arc-en-ciel (+2 points, score x2)");

        final Color color;
        final int points;
        final String description;

        AppleType(Color color, int points, String description) {
            this.color = color;
            this.points = points;
            this.description = description;
        }
    }

    private class Apple {
        int x, y;
        AppleType type;
        long spawnTime;
        boolean isActive;

        Apple(int x, int y, AppleType type) {
            this.x = x;
            this.y = y;
            this.type = type;
            this.spawnTime = System.currentTimeMillis();
            this.isActive = true;
        }
    }

    private class SoundManager {
        private Map<String, Clip> clips = new HashMap<>();
        float volume = 1.0f;

        public void loadSounds() {
            try {
                loadSound("move", "/sounds/move.wav");
                loadSound("eat", "/sounds/eat.wav");
                loadSound("crash", "/sounds/crash.wav");
                loadSound("start", "/sounds/start.wav");
            } catch (Exception e) {
                System.err.println("Erreur lors du chargement des sons: " + e.getMessage());
            }
        }

        private void loadSound(String name, String path) {
            try {
                AudioInputStream audioIn = AudioSystem.getAudioInputStream(
                        getClass().getResource(path)
                );
                Clip clip = AudioSystem.getClip();
                clip.open(audioIn);
                clips.put(name, clip);
                setVolume(clip, volume);
            } catch (Exception e) {
                System.err.println("Erreur lors du chargement du son " + name + ": " + e.getMessage());
            }
        }

        public void playSound(String name) {
            if (!soundEnabled) return;

            Clip clip = clips.get(name);
            if (clip != null) {
                if (clip.isRunning()) {
                    clip.stop();
                }
                clip.setFramePosition(0);
                clip.start();
            }
        }

        public void stopAll() {
            for (Clip clip : clips.values()) {
                if (clip.isRunning()) {
                    clip.stop();
                }
            }
        }

        public void setVolume(float newVolume) {
            volume = Math.max(0.0f, Math.min(1.0f, newVolume));
            for (Clip clip : clips.values()) {
                setVolume(clip, volume);
            }
        }

        private void setVolume(Clip clip, float volume) {
            try {
                FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float dB = (float) (Math.log10(volume) * 20.0f);
                gainControl.setValue(Math.max(gainControl.getMinimum(),
                        Math.min(gainControl.getMaximum(), dB)));
            } catch (Exception e) {
                System.err.println("Erreur lors du réglage du volume: " + e.getMessage());
            }
        }

        public void toggleSound() {
            soundEnabled = !soundEnabled;
            if (!soundEnabled) {
                stopAll();
            }
        }
    }

    // Variables du jeu
    private final int WIDTH = 600;
    private final int HEIGHT = 600;
    private final int DOT_SIZE = 10;
    private final int DELAY = 16;
    private final int ALL_DOTS = 900;

    private final double[][] positions = new double[ALL_DOTS][2];
    private final double[][] velocities = new double[ALL_DOTS][2];
    private int dots;

    private Apple currentApple;
    private double baseSpeed = 3.0;
    private double currentSpeed;
    private long speedEffectEndTime;
    private long rainbowEffectEndTime;
    private boolean isRainbowEffect = false;

    private Queue<Integer> directionQueue = new LinkedList<>();
    private int currentDirection = KeyEvent.VK_RIGHT;

    private boolean inGame = true;
    private Timer timer;
    private Random random = new Random();

    private int currentScore = 0;
    private int bestScore = 0;
    private final String SCORE_FILE = "snake_best_score.txt";

    private GameState gameState = GameState.MENU;
    private Difficulty currentDifficulty = Difficulty.MEDIUM;
    private int selectedMenuItem = 0;
    private final String[] menuItems = {"Nouvelle Partie", "Difficulté", "Quitter"};
    private boolean showDifficultyMenu = false;

    private SoundManager soundManager;
    private boolean soundEnabled = true;

    public SnakeGame() {
        addKeyListener(new TAdapter());
        setBackground(Color.BLACK);
        setFocusable(true);
        setPreferredSize(new Dimension(WIDTH, HEIGHT));

        soundManager = new SoundManager();
        soundManager.loadSounds();

        loadBestScore();
    }

    private void loadBestScore() {
        try {
            File file = new File(SCORE_FILE);
            if (file.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line = reader.readLine();
                if (line != null) {
                    bestScore = Integer.parseInt(line.trim());
                }
                reader.close();
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Erreur lors du chargement du meilleur score: " + e.getMessage());
            bestScore = 0;
        }
    }

    private void saveBestScore() {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(SCORE_FILE));
            writer.write(String.valueOf(bestScore));
            writer.close();
        } catch (IOException e) {
            System.err.println("Erreur lors de la sauvegarde du meilleur score: " + e.getMessage());
        }
    }

    private void initGame() {
        if (timer != null) {
            timer.stop();
        }

        dots = 3;
        currentScore = 0;
        currentSpeed = baseSpeed;
        inGame = true;
        isRainbowEffect = false;

        for (int i = 0; i < dots; i++) {
            positions[i][0] = WIDTH/2 - i * DOT_SIZE;
            positions[i][1] = HEIGHT/2;
            velocities[i][0] = currentSpeed;
            velocities[i][1] = 0;
        }

        currentDirection = KeyEvent.VK_RIGHT;
        directionQueue.clear();

        locateNewApple();
        soundManager.playSound("start");

        timer = new Timer(DELAY, this);
        timer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        switch (gameState) {
            case MENU:
                drawMenu(g2d);
                break;
            case PLAYING:
                drawGame(g2d);
                break;
            case PAUSED:
                drawGame(g2d);
                drawPauseScreen(g2d);
                break;
            case GAME_OVER:
                drawGame(g2d);
                drawGameOver(g2d);
                break;
        }
    }

    private void drawMenu(Graphics2D g) {
        g.setColor(Color.GREEN);
        g.setFont(new Font("Arial", Font.BOLD, 48));
        String title = "SNAKE";
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(title, (WIDTH - metrics.stringWidth(title))/2, HEIGHT/4);

        g.setFont(new Font("Arial", Font.PLAIN, 24));
        metrics = g.getFontMetrics();
        int y = HEIGHT/2;

        if (!showDifficultyMenu) {
            for (int i = 0; i < menuItems.length; i++) {
                if (i == selectedMenuItem) {
                    g.setColor(Color.GREEN);
                    g.drawString("> " + menuItems[i], (WIDTH - metrics.stringWidth(menuItems[i]))/2 - 20, y);
                } else {
                    g.setColor(Color.WHITE);
                    g.drawString(menuItems[i], (WIDTH - metrics.stringWidth(menuItems[i]))/2, y);
                }
                y += 40;
            }
        } else {
            for (int i = 0; i < Difficulty.values().length; i++) {
                String diffText = Difficulty.values()[i].label;
                if (i == selectedMenuItem) {
                    g.setColor(Color.GREEN);
                    g.drawString("> " + diffText, (WIDTH - metrics.stringWidth(diffText))/2 - 20, y);
                } else {
                    g.setColor(Color.WHITE);
                    g.drawString(diffText, (WIDTH - metrics.stringWidth(diffText))/2, y);
                }
                y += 40;
            }
        }
    }

    private void drawGame(Graphics2D g) {
        if (inGame) {
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 14));
            g.drawString("Score: " + currentScore, 10, 20);
            g.drawString("Meilleur: " + bestScore, WIDTH - 100, 20);
            g.drawString("Difficulté: " + currentDifficulty.label, WIDTH/2 - 50, 20);

            if (currentApple != null && currentApple.isActive) {
                if (currentApple.type == AppleType.RAINBOW) {
                    float hue = (System.currentTimeMillis() % 1000) / 1000f;
                    g.setColor(Color.getHSBColor(hue, 1, 1));
                } else {
                    g.setColor(currentApple.type.color);
                }
                g.fillOval(currentApple.x, currentApple.y, DOT_SIZE, DOT_SIZE);
            }

            for (int i = dots - 1; i >= 0; i--) {
                if (i == 0) {
                    g.setColor(Color.GREEN);
                } else {
                    if (isRainbowEffect) {
                        float hue = ((System.currentTimeMillis() + i * 100) % 1000) / 1000f;
                        g.setColor(Color.getHSBColor(hue, 1, 1));
                    } else {
                        g.setColor(Color.YELLOW);
                    }
                }
                g.fillOval((int)positions[i][0], (int)positions[i][1], DOT_SIZE, DOT_SIZE);
            }
        }
    }

    private void drawPauseScreen(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 36));
        String pauseText = "PAUSE";
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(pauseText, (WIDTH - metrics.stringWidth(pauseText))/2, HEIGHT/2);

        g.setFont(new Font("Arial", Font.PLAIN, 18));
        String resumeText = "Appuyez sur ESPACE pour continuer";
        metrics = g.getFontMetrics();
        g.drawString(resumeText, (WIDTH - metrics.stringWidth(resumeText))/2, HEIGHT/2 + 40);
    }

    private void drawGameOver(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 36));
        String msg = "Game Over - Score: " + currentScore;
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(msg, (WIDTH - metrics.stringWidth(msg))/2, HEIGHT/2 - 40);

        String bestMsg = "Meilleur Score: " + bestScore;
        g.drawString(bestMsg, (WIDTH - metrics.stringWidth(bestMsg))/2, HEIGHT/2);

        g.setFont(new Font("Arial", Font.PLAIN, 18));
        String restartText = "Appuyez sur ESPACE pour retourner au menu";
        metrics = g.getFontMetrics();
        g.drawString(restartText, (WIDTH - metrics.stringWidth(restartText))/2, HEIGHT/2 + 40);
    }

    private void move() {
        if (!directionQueue.isEmpty() && canChangeDirection(directionQueue.peek())) {
            currentDirection = directionQueue.poll();
            updateHeadVelocity();
            soundManager.playSound("move");
        }

        for (int i = 0; i < dots; i++) {
            positions[i][0] += velocities[i][0];
            positions[i][1] += velocities[i][1];

            if (i > 0) {
                double dx = positions[i-1][0] - positions[i][0];
                double dy = positions[i-1][1] - positions[i][1];
                double distance = Math.sqrt(dx * dx + dy * dy);

                if (distance > DOT_SIZE) {
                    velocities[i][0] = (dx / distance) * currentSpeed;
                    velocities[i][1] = (dy / distance) * currentSpeed;
                }
            }
        }
    }

    private void updateHeadVelocity() {
        velocities[0][0] = 0;
        velocities[0][1] = 0;

        switch (currentDirection) {
            case KeyEvent.VK_LEFT: velocities[0][0] = -currentSpeed; break;
            case KeyEvent.VK_RIGHT: velocities[0][0] = currentSpeed; break;
            case KeyEvent.VK_UP: velocities[0][1] = -currentSpeed; break;
            case KeyEvent.VK_DOWN: velocities[0][1] = currentSpeed; break;
        }
    }

    private void locateNewApple() {
        int RAND_POS = (WIDTH - DOT_SIZE) / DOT_SIZE;
        int x, y;
        boolean validLocation;

        do {
            validLocation = true;
            x = random.nextInt(RAND_POS) * DOT_SIZE;
            y = random.nextInt(RAND_POS) * DOT_SIZE;

            for (int i = 0; i < dots; i++) {
                if (Math.abs(positions[i][0] - x) < DOT_SIZE &&
                        Math.abs(positions[i][1] - y) < DOT_SIZE) {
                    validLocation = false;
                    break;
                }
            }
        } while (!validLocation);

        int rand = random.nextInt(100);
        AppleType type;
        if (rand < 60) {           // 60% chance
            type = AppleType.BASIC;
        } else if (rand < 75) {    // 15% chance
            type = AppleType.GOLDEN;
        } else if (rand < 85) {    // 10% chance
            type = AppleType.SPEED;
        } else if (rand < 95) {    // 10% chance
            type = AppleType.SLOW;
        } else {                   // 5% chance
            type = AppleType.RAINBOW;
        }

        currentApple = new Apple(x, y, type);
    }

    private void checkApple() {
        if (currentApple != null && currentApple.isActive &&
                Math.abs(positions[0][0] - currentApple.x) < DOT_SIZE &&
                Math.abs(positions[0][1] - currentApple.y) < DOT_SIZE) {

            soundManager.playSound("eat");

            currentScore += currentApple.type.points * currentDifficulty.scoreMultiplier;
            applyAppleEffect(currentApple.type);

            if (currentScore > bestScore) {
                bestScore = currentScore;
                saveBestScore();
            }

            positions[dots][0] = positions[dots-1][0];
            positions[dots][1] = positions[dots-1][1];
            velocities[dots][0] = velocities[dots-1][0];
            velocities[dots][1] = velocities[dots-1][1];
            dots++;

            locateNewApple();
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime >= speedEffectEndTime) {
            currentSpeed = baseSpeed;
        }
        if (currentTime >= rainbowEffectEndTime) {
            isRainbowEffect = false;
        }
    }

    private void applyAppleEffect(AppleType type) {
        long currentTime = System.currentTimeMillis();

        switch (type) {
            case SPEED:
                currentSpeed = baseSpeed * 2;
                speedEffectEndTime = currentTime + 5000; // 5 secondes
                break;
            case SLOW:
                currentSpeed = baseSpeed / 2;
                speedEffectEndTime = currentTime + 3000; // 3 secondes
                break;
            case RAINBOW:
                isRainbowEffect = true;
                rainbowEffectEndTime = currentTime + 10000; // 10 secondes
                break;
        }

        updateHeadVelocity();
    }

    private void checkCollision() {
        if (positions[0][0] >= WIDTH || positions[0][0] < 0 ||
                positions[0][1] >= HEIGHT || positions[0][1] < 0) {
            gameState = GameState.GAME_OVER;
            inGame = false;
            return;
        }

        for (int i = 4; i < dots; i++) {
            if (Math.abs(positions[0][0] - positions[i][0]) < DOT_SIZE/2 &&
                    Math.abs(positions[0][1] - positions[i][1]) < DOT_SIZE/2) {
                gameState = GameState.GAME_OVER;
                inGame = false;
                return;
            }
        }
    }

    private boolean canChangeDirection(int newDirection) {
        switch (newDirection) {
            case KeyEvent.VK_LEFT: return currentDirection != KeyEvent.VK_RIGHT;
            case KeyEvent.VK_RIGHT: return currentDirection != KeyEvent.VK_LEFT;
            case KeyEvent.VK_UP: return currentDirection != KeyEvent.VK_DOWN;
            case KeyEvent.VK_DOWN: return currentDirection != KeyEvent.VK_UP;
            default: return false;
        }
    }

    private boolean isValidDirectionChange(int currentDir, int newDir) {
        return (currentDir == KeyEvent.VK_UP || currentDir == KeyEvent.VK_DOWN) &&
                (newDir == KeyEvent.VK_LEFT || newDir == KeyEvent.VK_RIGHT) ||
                (currentDir == KeyEvent.VK_LEFT || currentDir == KeyEvent.VK_RIGHT) &&
                        (newDir == KeyEvent.VK_UP || newDir == KeyEvent.VK_DOWN);
    }

    private class TAdapter extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            int key = e.getKeyCode();

            // Contrôles globaux
            if (key == KeyEvent.VK_M) {
                soundManager.toggleSound();
                return;
            }

            // Gestion selon l'état du jeu
            switch (gameState) {
                case MENU:
                    handleMenuInput(key);
                    break;
                case PLAYING:
                    if (key == KeyEvent.VK_P || key == KeyEvent.VK_ESCAPE) {
                        gameState = GameState.PAUSED;
                        timer.stop();
                    } else {
                        handleGameInput(key);
                    }
                    break;
                case PAUSED:
                    if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ESCAPE) {
                        gameState = GameState.PLAYING;
                        timer.start();
                    }
                    break;
                case GAME_OVER:
                    if (key == KeyEvent.VK_SPACE) {
                        gameState = GameState.MENU;
                        selectedMenuItem = 0;
                    }
                    break;
            }
            repaint();
        }
    }

    private void handleMenuInput(int key) {
        System.out.println("Menu key pressed: " + key); // Pour déboguer

        if (!showDifficultyMenu) {
            switch (key) {
                case KeyEvent.VK_UP:
                    selectedMenuItem--;
                    if (selectedMenuItem < 0) selectedMenuItem = menuItems.length - 1;
                    break;
                case KeyEvent.VK_DOWN:
                    selectedMenuItem++;
                    if (selectedMenuItem >= menuItems.length) selectedMenuItem = 0;
                    break;
                case KeyEvent.VK_ENTER:
                    handleMenuSelection();
                    break;
            }
        } else {
            switch (key) {
                case KeyEvent.VK_UP:
                    selectedMenuItem--;
                    if (selectedMenuItem < 0) selectedMenuItem = Difficulty.values().length - 1;
                    break;
                case KeyEvent.VK_DOWN:
                    selectedMenuItem++;
                    if (selectedMenuItem >= Difficulty.values().length) selectedMenuItem = 0;
                    break;
                case KeyEvent.VK_ENTER:
                    currentDifficulty = Difficulty.values()[selectedMenuItem];
                    showDifficultyMenu = false;
                    selectedMenuItem = 0;
                    break;
                case KeyEvent.VK_ESCAPE:
                    showDifficultyMenu = false;
                    selectedMenuItem = 0;
                    break;
            }
        }
        repaint();
    }
    private void handleMenuSelection() {
        switch (selectedMenuItem) {
            case 0: // Nouvelle Partie
                startNewGame();
                break;
            case 1: // Difficulté
                showDifficultyMenu = true;
                selectedMenuItem = 0;
                break;
            case 2: // Quitter
                System.exit(0);
                break;
        }
    }

    private void handleGameInput(int key) {
        if (key == KeyEvent.VK_P || key == KeyEvent.VK_ESCAPE) {
            gameState = GameState.PAUSED;
            timer.stop();
            return;
        }

        if ((key == KeyEvent.VK_LEFT || key == KeyEvent.VK_RIGHT ||
                key == KeyEvent.VK_UP || key == KeyEvent.VK_DOWN) &&
                directionQueue.size() < 2) {

            int lastDirection = directionQueue.isEmpty() ? currentDirection : directionQueue.peek();
            if (isValidDirectionChange(lastDirection, key)) {
                directionQueue.offer(key);
            }
        }
    }

    private void startNewGame() {
        gameState = GameState.PLAYING;
        baseSpeed = currentDifficulty.speed;
        currentSpeed = baseSpeed;
        initGame();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (gameState == GameState.PLAYING && inGame) {
            move();
            checkCollision();
            checkApple();
        }
        repaint();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Snake");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(new SnakeGame());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
