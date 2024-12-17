import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.LinkedList;
import java.util.Queue;

public class SnakeGame extends JPanel implements ActionListener {
    private final int WIDTH = 300;
    private final int HEIGHT = 300;
    private final int DOT_SIZE = 10;
    private final int DELAY = 16;

    private final int ALL_DOTS = 900;
    private final double[][] positions = new double[ALL_DOTS][2];
    private final double[][] velocities = new double[ALL_DOTS][2];
    private int dots;

    private int apple_x;
    private int apple_y;

    private Queue<Integer> directionQueue = new LinkedList<>();
    private int currentDirection = KeyEvent.VK_RIGHT;

    private boolean inGame = true;
    private Timer timer;
    private final double moveSpeed = 3.0;

    // Système de score
    private int currentScore = 0;
    private int bestScore = 0;
    private final String SCORE_FILE = "snake_best_score.txt";

    public SnakeGame() {
        addKeyListener(new TAdapter());
        setBackground(Color.BLACK);
        setFocusable(true);
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        loadBestScore();
        initGame();
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
        inGame = true;

        for (int i = 0; i < dots; i++) {
            positions[i][0] = 150 - i * DOT_SIZE;
            positions[i][1] = 150;
            velocities[i][0] = moveSpeed;
            velocities[i][1] = 0;
        }

        currentDirection = KeyEvent.VK_RIGHT;
        directionQueue.clear();

        locateApple();

        timer = new Timer(DELAY, this);
        timer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        doDrawing((Graphics2D)g);
    }

    private void doDrawing(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (inGame) {
            // Scores
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 14));
            g.drawString("Score: " + currentScore, 10, 20);
            g.drawString("Meilleur: " + bestScore, WIDTH - 100, 20);

            // Pomme
            g.setColor(Color.RED);
            g.fillOval(apple_x, apple_y, DOT_SIZE, DOT_SIZE);

            // Serpent
            for (int i = dots - 1; i >= 0; i--) {
                if (i == 0) {
                    g.setColor(Color.GREEN);
                } else {
                    g.setColor(Color.YELLOW);
                }
                g.fillOval((int)positions[i][0], (int)positions[i][1], DOT_SIZE, DOT_SIZE);
            }
        } else {
            gameOver(g);
        }

        Toolkit.getDefaultToolkit().sync();
    }

    private void gameOver(Graphics g) {
        timer.stop();

        String msg = "Game Over - Score: " + currentScore;
        String bestMsg = "Meilleur Score: " + bestScore;
        String restartMsg = "Appuyez sur ESPACE pour recommencer";

        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 14));
        FontMetrics fm = g.getFontMetrics();

        g.drawString(msg, (WIDTH - fm.stringWidth(msg)) / 2, HEIGHT / 2 - 20);
        g.drawString(bestMsg, (WIDTH - fm.stringWidth(bestMsg)) / 2, HEIGHT / 2);
        g.drawString(restartMsg, (WIDTH - fm.stringWidth(restartMsg)) / 2, HEIGHT / 2 + 20);
    }

    private void move() {
        if (!directionQueue.isEmpty() && canChangeDirection(directionQueue.peek())) {
            currentDirection = directionQueue.poll();
            updateHeadVelocity();
        }

        for (int i = 0; i < dots; i++) {
            positions[i][0] += velocities[i][0];
            positions[i][1] += velocities[i][1];

            if (i > 0) {
                double dx = positions[i-1][0] - positions[i][0];
                double dy = positions[i-1][1] - positions[i][1];
                double distance = Math.sqrt(dx * dx + dy * dy);

                if (distance > DOT_SIZE) {
                    velocities[i][0] = (dx / distance) * moveSpeed;
                    velocities[i][1] = (dy / distance) * moveSpeed;
                }
            }
        }
    }

    private void updateHeadVelocity() {
        velocities[0][0] = 0;
        velocities[0][1] = 0;

        switch (currentDirection) {
            case KeyEvent.VK_LEFT: velocities[0][0] = -moveSpeed; break;
            case KeyEvent.VK_RIGHT: velocities[0][0] = moveSpeed; break;
            case KeyEvent.VK_UP: velocities[0][1] = -moveSpeed; break;
            case KeyEvent.VK_DOWN: velocities[0][1] = moveSpeed; break;
        }
    }

    private void checkApple() {
        if (Math.abs(positions[0][0] - apple_x) < DOT_SIZE &&
                Math.abs(positions[0][1] - apple_y) < DOT_SIZE) {

            // Score augmente de 1 par pomme
            currentScore++;
            if (currentScore > bestScore) {
                bestScore = currentScore;
                saveBestScore();
            }

            positions[dots][0] = positions[dots-1][0];
            positions[dots][1] = positions[dots-1][1];
            velocities[dots][0] = velocities[dots-1][0];
            velocities[dots][1] = velocities[dots-1][1];
            dots++;
            locateApple();
        }
    }

    private void checkCollision() {
        if (positions[0][0] >= WIDTH || positions[0][0] < 0 ||
                positions[0][1] >= HEIGHT || positions[0][1] < 0) {
            inGame = false;
            return;
        }

        for (int i = 4; i < dots; i++) {
            if (Math.abs(positions[0][0] - positions[i][0]) < DOT_SIZE/2 &&
                    Math.abs(positions[0][1] - positions[i][1]) < DOT_SIZE/2) {
                inGame = false;
                return;
            }
        }
    }

    private void locateApple() {
        int RAND_POS = (WIDTH - DOT_SIZE) / DOT_SIZE;
        boolean validLocation;

        do {
            validLocation = true;
            apple_x = ((int) (Math.random() * RAND_POS)) * DOT_SIZE;
            apple_y = ((int) (Math.random() * RAND_POS)) * DOT_SIZE;

            // Vérifie que la pomme n'apparaît pas sur le serpent
            for (int i = 0; i < dots; i++) {
                if (Math.abs(positions[i][0] - apple_x) < DOT_SIZE &&
                        Math.abs(positions[i][1] - apple_y) < DOT_SIZE) {
                    validLocation = false;
                    break;
                }
            }
        } while (!validLocation);
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

    @Override
    public void actionPerformed(ActionEvent e) {
        if (inGame) {
            move();
            checkCollision();
            checkApple();
        }
        repaint();
    }

    private class TAdapter extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            int key = e.getKeyCode();

            if (key == KeyEvent.VK_SPACE && !inGame) {
                initGame();
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
    }

    private boolean isValidDirectionChange(int currentDir, int newDir) {
        return (currentDir == KeyEvent.VK_UP || currentDir == KeyEvent.VK_DOWN) &&
                (newDir == KeyEvent.VK_LEFT || newDir == KeyEvent.VK_RIGHT) ||
                (currentDir == KeyEvent.VK_LEFT || currentDir == KeyEvent.VK_RIGHT) &&
                        (newDir == KeyEvent.VK_UP || newDir == KeyEvent.VK_DOWN);
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