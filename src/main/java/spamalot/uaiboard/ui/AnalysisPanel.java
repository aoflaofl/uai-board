package spamalot.uaiboard.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import spamalot.uaiboard.game.Move;
import spamalot.uaiboard.game.Side;
import spamalot.uaiboard.uai.UaiInfo;

/** Shows what the engine reports while it searches: depth, score, nodes, speed, time and the principal variation. */
public final class AnalysisPanel extends JPanel {
    private final JLabel engine = new JLabel(" ");
    private final JLabel depth = new JLabel("-");
    private final JLabel score = new JLabel("-");
    private final JLabel scoreName = new JLabel("Score");
    private final JLabel nodes = new JLabel("-");
    private final JLabel speed = new JLabel("-");
    private final JLabel time = new JLabel("-");
    private final JTextArea pv = new JTextArea(4, 18);

    /** A panel titled "Engine", showing scores as the engine reports them (from the side to move's point of view). */
    public AnalysisPanel() {
        this("Engine", false);
    }

    /**
     * @param title the border title
     * @param fromX true to show every score from x's point of view (positive is good for x), whoever is to move
     */
    public AnalysisPanel(String title, boolean fromX) {
        super(new BorderLayout(0, 6));
        setBorder(BorderFactory.createTitledBorder(title));
        scoreName.setText(fromX ? "Score (x)" : "Score");
        engine.setFont(engine.getFont().deriveFont(Font.BOLD));
        JPanel grid = new JPanel(new GridBagLayout());
        addRow(grid, 0, "Depth", depth);
        addRow(grid, 1, scoreName, score);
        addRow(grid, 2, "Nodes", nodes);
        addRow(grid, 3, "Speed", speed);
        addRow(grid, 4, "Time", time);
        pv.setEditable(false);
        pv.setLineWrap(true);
        pv.setWrapStyleWord(true);
        pv.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane pvScroll = new JScrollPane(pv);
        pvScroll.setBorder(BorderFactory.createTitledBorder("Best line"));
        add(engine, BorderLayout.NORTH);
        add(grid, BorderLayout.CENTER);
        add(pvScroll, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(230, 280));
    }

    private static void addRow(JPanel grid, int row, String name, JLabel value) {
        addRow(grid, row, new JLabel(name), value);
    }

    private static void addRow(JPanel grid, int row, JLabel name, JLabel value) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(1, 2, 1, 8);
        c.anchor = GridBagConstraints.WEST;
        grid.add(name, c);
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.add(value, c);
    }

    /** The engine's name and a short status such as "is thinking…". */
    public void setEngine(String text) {
        engine.setText(text.isBlank() ? " " : text);
    }

    public void clear() {
        depth.setText("-");
        score.setText("-");
        nodes.setText("-");
        speed.setText("-");
        time.setText("-");
        pv.setText("");
    }

    /**
     * Shows an info line from the point of view of x: the engine's score is for the side to move, so it is negated when o is to
     * move.
     */
    public void setInfo(UaiInfo info, Side sideToMove) {
        setInfo(info);
        if (info.score() != null) {
            score.setText(formatScore(xView(info.score(), sideToMove)));
        }
    }

    /** The score from x's point of view, given the side to move it was reported for. */
    static UaiInfo.Score xView(UaiInfo.Score s, Side sideToMove) {
        return sideToMove == Side.X ? s : new UaiInfo.Score(s.mate(), -s.value());
    }

    /** Shows the fields {@code info} carries and leaves the others as they were. */
    public void setInfo(UaiInfo info) {
        if (info.depth() != null) {
            depth.setText(info.selDepth() != null ? info.depth() + " / " + info.selDepth() : String.valueOf(info.depth()));
        }
        if (info.score() != null) {
            score.setText(formatScore(info.score()));
        }
        if (info.nodes() != null) {
            nodes.setText(formatCount(info.nodes()));
        }
        if (info.nps() != null) {
            speed.setText(formatCount(info.nps()) + "/s");
        }
        if (info.timeMs() != null) {
            time.setText(String.format(Locale.ROOT, "%.2f s", info.timeMs() / 1000.0));
        }
        if (!info.pv().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Move m : info.pv()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(m.toUai());
            }
            pv.setText(sb.toString());
            pv.setCaretPosition(0);
        }
    }

    /** {@code +63}, {@code -12}, {@code mate 3}. Scores are in the engine's own units. */
    static String formatScore(UaiInfo.Score s) {
        return s.mate() ? "mate " + s.value() : String.format(Locale.ROOT, "%+d", s.value());
    }

    /** {@code 950}, {@code 12.3k}, {@code 3.4M}. */
    static String formatCount(long n) {
        if (n >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.2fG", n / 1e9);
        }
        if (n >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.2fM", n / 1e6);
        }
        if (n >= 10_000L) {
            return String.format(Locale.ROOT, "%.1fk", n / 1e3);
        }
        return String.valueOf(n);
    }
}
