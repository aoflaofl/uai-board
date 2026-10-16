package spamalot.uaiboard.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import spamalot.uaiboard.game.Move;
import spamalot.uaiboard.game.Position;
import spamalot.uaiboard.game.Squares;

/**
 * Draws a position and turns mouse clicks into moves through {@link MoveInput}. It only reports the move the player
 * completes; whoever owns the game decides whether and how to play it.
 */
public final class BoardPanel extends JPanel {
    private static final int MARGIN = 24;
    private static final Color LIGHT = new Color(0xE8D9B5);
    private static final Color DARK = new Color(0xB59A6B);
    private static final Color X_COLOR = new Color(0x2B2B2B);
    private static final Color O_COLOR = new Color(0xD9453D);
    private static final Color SELECTED = new Color(0x2E7DD7);
    private static final Color TARGET = new Color(0x2E9E4F);
    private static final Color LAST = new Color(0xF2C230);

    private final MoveInput input;
    private Position position;
    private Move lastMove;
    private boolean inputEnabled = true;
    private Consumer<Move> moveListener = m -> { };

    public BoardPanel(Position position) {
        this.position = position;
        this.input = new MoveInput(position);
        setPreferredSize(new Dimension(560, 560));
        setMinimumSize(new Dimension(280, 280));
        setBackground(new Color(0xF4F1EA));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!inputEnabled) {
                    return;
                }
                int square = geometry().squareAt(e.getX(), e.getY());
                input.click(square).ifPresent(move -> moveListener.accept(move));
                updateCursor(square);
                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                updateCursor(inputEnabled ? geometry().squareAt(e.getX(), e.getY()) : -1);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clear");
        getActionMap().put("clear", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                input.clear();
                repaint();
            }
        });
    }

    /** Called with the move when the player completes one by clicking. */
    public void setMoveListener(Consumer<Move> listener) {
        this.moveListener = listener;
    }

    /** Shows {@code position}; {@code lastMove} (may be null) is outlined. */
    public void setPosition(Position position, Move lastMove) {
        this.position = position;
        this.lastMove = lastMove;
        input.setPosition(position);
        repaint();
    }

    /** Whether clicks are accepted (off while an engine is thinking or the game is over). */
    public void setInputEnabled(boolean enabled) {
        this.inputEnabled = enabled;
        if (!enabled) {
            input.clear();
            setCursor(Cursor.getDefaultCursor());
        }
        repaint();
    }

    private BoardGeometry geometry() {
        return new BoardGeometry(getWidth(), getHeight(), MARGIN);
    }

    private void updateCursor(int square) {
        boolean clickable = square >= 0
                && (!position.movesFrom(square).isEmpty() || input.targets().stream().anyMatch(m -> m.to() == square));
        setCursor(clickable ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            BoardGeometry geo = geometry();
            drawSquares(g, geo);
            drawLastMove(g, geo);
            drawSelection(g, geo);
            drawPieces(g, geo);
            drawTargets(g, geo);
            drawLabels(g, geo);
        } finally {
            g.dispose();
        }
    }

    private void drawSquares(Graphics2D g, BoardGeometry geo) {
        for (int sq = 0; sq < Squares.COUNT; sq++) {
            Rectangle r = geo.bounds(sq);
            g.setColor((Squares.file(sq) + Squares.rank(sq)) % 2 == 0 ? DARK : LIGHT);
            g.fillRect(r.x, r.y, r.width, r.height);
            if (position.pieceAt(sq) == Position.Piece.WALL) {
                g.setColor(Color.DARK_GRAY);
                g.fillRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4);
            }
        }
    }

    private void drawLastMove(Graphics2D g, BoardGeometry geo) {
        if (lastMove == null || lastMove.isPass()) {
            return;
        }
        g.setColor(LAST);
        g.setStroke(new BasicStroke(3f));
        for (int sq : new int[] {lastMove.from(), lastMove.to()}) {
            Rectangle r = geo.bounds(sq);
            g.drawRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4);
        }
    }

    private void drawSelection(Graphics2D g, BoardGeometry geo) {
        if (input.selected() < 0) {
            return;
        }
        Rectangle r = geo.bounds(input.selected());
        g.setColor(SELECTED);
        g.setStroke(new BasicStroke(4f));
        g.drawRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4);
    }

    private void drawPieces(Graphics2D g, BoardGeometry geo) {
        for (int sq = 0; sq < Squares.COUNT; sq++) {
            Position.Piece p = position.pieceAt(sq);
            if (p != Position.Piece.X && p != Position.Piece.O) {
                continue;
            }
            Rectangle r = geo.bounds(sq);
            int d = r.width * 3 / 4;
            int x = r.x + (r.width - d) / 2;
            int y = r.y + (r.height - d) / 2;
            g.setColor(p == Position.Piece.X ? X_COLOR : O_COLOR);
            g.fillOval(x, y, d, d);
            g.setColor(new Color(255, 255, 255, 70));
            g.fillOval(x + d / 6, y + d / 8, d / 3, d / 4);
        }
    }

    private void drawTargets(Graphics2D g, BoardGeometry geo) {
        g.setColor(TARGET);
        g.setStroke(new BasicStroke(3f));
        for (Move m : input.targets()) {
            Rectangle r = geo.bounds(m.to());
            int d = r.width / 3;
            int x = r.x + (r.width - d) / 2;
            int y = r.y + (r.height - d) / 2;
            if (m.isClone()) {
                g.fillOval(x, y, d, d);
            } else {
                g.drawOval(x, y, d, d);
            }
        }
    }

    private void drawLabels(Graphics2D g, BoardGeometry geo) {
        g.setColor(Color.DARK_GRAY);
        g.setFont(getFont().deriveFont(Font.BOLD, Math.max(10f, geo.cellSize() / 5f)));
        FontMetrics fm = g.getFontMetrics();
        for (int file = 0; file < Squares.SIZE; file++) {
            Rectangle r = geo.bounds(Squares.of(file, 0));
            String s = String.valueOf((char) ('a' + file));
            g.drawString(s, r.x + (r.width - fm.stringWidth(s)) / 2, r.y + r.height + fm.getAscent() + 2);
        }
        for (int rank = 0; rank < Squares.SIZE; rank++) {
            Rectangle r = geo.bounds(Squares.of(0, rank));
            String s = String.valueOf(rank + 1);
            g.drawString(s, r.x - fm.stringWidth(s) - 6, r.y + (r.height + fm.getAscent()) / 2 - 2);
        }
    }
}
