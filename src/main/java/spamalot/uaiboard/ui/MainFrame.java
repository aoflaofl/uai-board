package spamalot.uaiboard.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import spamalot.uaiboard.game.Game;
import spamalot.uaiboard.game.Move;
import spamalot.uaiboard.game.Outcome;
import spamalot.uaiboard.game.Position;
import spamalot.uaiboard.game.Side;

/**
 * The main window: the board, the move list and a status line. For now two people share the board (hot seat); playing against
 * a UAI engine is the next step.
 */
public final class MainFrame extends JFrame {
    private Game game = new Game();
    private final BoardPanel board = new BoardPanel(game.position());
    private final JTextArea moveList = new JTextArea(20, 16);
    private final JLabel status = new JLabel(" ");

    public MainFrame() {
        super("uai-board");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setJMenuBar(buildMenu());

        moveList.setEditable(false);
        moveList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        JScrollPane moves = new JScrollPane(moveList);
        moves.setPreferredSize(new Dimension(190, 400));
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        add(board, BorderLayout.CENTER);
        add(moves, BorderLayout.EAST);
        add(status, BorderLayout.SOUTH);

        board.setMoveListener(this::humanMove);
        refresh();
        pack();
        setLocationByPlatform(true);
    }

    private JMenuBar buildMenu() {
        JMenuBar bar = new JMenuBar();
        JMenu gameMenu = new JMenu("Game");
        gameMenu.setMnemonic(KeyEvent.VK_G);
        JMenuItem newGame = new JMenuItem("New Game");
        newGame.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        newGame.addActionListener(e -> newGame());
        JMenuItem undo = new JMenuItem("Undo");
        undo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        undo.addActionListener(e -> undo());
        JMenuItem quit = new JMenuItem("Quit");
        quit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        quit.addActionListener(e -> dispose());
        gameMenu.add(newGame);
        gameMenu.add(undo);
        gameMenu.addSeparator();
        gameMenu.add(quit);

        JMenu helpMenu = new JMenu("Help");
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "uai-board\nA Swing front end for Ataxx engines that speak UAI.\nMIT licence.", "About uai-board",
                JOptionPane.INFORMATION_MESSAGE));
        helpMenu.add(about);

        bar.add(gameMenu);
        bar.add(helpMenu);
        return bar;
    }

    private void humanMove(Move move) {
        game.play(move);
        passWhileStuck();
        refresh();
    }

    /** A side with no move passes automatically while the game goes on. */
    private void passWhileStuck() {
        while (!game.position().isGameOver() && game.position().legalMoves().equals(java.util.List.of(Move.pass()))) {
            game.play(Move.pass());
        }
    }

    private void newGame() {
        game = new Game();
        refresh();
    }

    private void undo() {
        // Take back passes together with the move before them.
        if (!game.undo()) {
            return;
        }
        while (game.lastMove() != null && game.lastMove().isPass() && game.undo()) {
            // keep going
        }
        refresh();
    }

    private void refresh() {
        Position p = game.position();
        board.setPosition(p, game.lastMove());
        board.setInputEnabled(!p.isGameOver());
        String text = MoveListFormat.format(game.start().sideToMove(), game.start().fullmoveNumber(), game.moves());
        moveList.setText(text);
        moveList.setCaretPosition(moveList.getDocument().getLength());
        status.setText(statusText(p));
    }

    private static String statusText(Position p) {
        String counts = "x " + p.count(Side.X) + " - o " + p.count(Side.O);
        Outcome outcome = p.outcome();
        return switch (outcome) {
            case ONGOING -> (p.sideToMove() == Side.X ? "x" : "o") + " to move    " + counts;
            case X_WINS -> "Game over: x wins    " + counts;
            case O_WINS -> "Game over: o wins    " + counts;
            case DRAW -> "Game over: draw    " + counts;
        };
    }
}
