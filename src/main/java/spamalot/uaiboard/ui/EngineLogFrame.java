package spamalot.uaiboard.ui;

import java.awt.BorderLayout;
import java.awt.Font;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import spamalot.uaiboard.uai.UaiClient;

/** A window showing every line sent to and read from the engine, for finding out why an engine misbehaves. */
public final class EngineLogFrame extends JFrame {
    static final int MAX_LINES = 3000;
    private final JTextArea text = new JTextArea(24, 90);

    public EngineLogFrame() {
        super("uai-board: engine console");
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        text.setEditable(false);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JButton clear = new JButton("Clear");
        clear.addActionListener(e -> text.setText(""));
        JPanel bottom = new JPanel();
        bottom.add(clear);
        add(new JScrollPane(text), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        pack();
        setLocationByPlatform(true);
    }

    /** Appends text that already ends each line with a newline; call on the event dispatch thread. */
    public void appendText(String lines) {
        text.append(lines);
        int extra = text.getLineCount() - MAX_LINES;
        if (extra > 0) {
            try {
                text.replaceRange("", 0, text.getLineStartOffset(extra));
            } catch (BadLocationException e) {
                text.setText("");
            }
        }
        text.setCaretPosition(text.getDocument().getLength());
    }

    /** The console line for something sent to, read from, or written on the error output of an engine. */
    public static String format(UaiClient.Direction direction, String line) {
        String prefix = switch (direction) {
            case SENT -> ">> ";
            case RECEIVED -> "<< ";
            case ERROR -> "!! ";
        };
        return prefix + line + "\n";
    }
}
