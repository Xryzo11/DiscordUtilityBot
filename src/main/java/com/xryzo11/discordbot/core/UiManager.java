package com.xryzo11.discordbot.core;

import com.xryzo11.discordbot.DiscordBot;
import com.xryzo11.discordbot.musicBot.MusicBot;
import com.xryzo11.discordbot.utils.enums.PresenceActivity;
import com.xryzo11.discordbot.utils.enums.PresenceStatus;

import javax.swing.*;
import java.awt.*;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static com.xryzo11.discordbot.musicBot.MusicBot.formatTime;

public class UiManager {
    private static UiManager instance = null;
    private UiWindow uiWindow;
    private static final PrintStream originalOut = System.out;
    private static final PrintStream originalErr = System.err;

    private UiManager() {
        uiWindow = new UiWindow();
        enableConsoleRedirect();
    }

    private void enableConsoleRedirect() {
        TextAreaOutputStream textAreaStream = new TextAreaOutputStream(uiWindow.logTextPane);
        TeeOutputStream teeOut = new TeeOutputStream(originalOut, textAreaStream);
        TeeOutputStream teeErr = new TeeOutputStream(originalErr, textAreaStream);

        PrintStream printStreamOut = new PrintStream(teeOut, true, StandardCharsets.UTF_8);
        PrintStream printStreamErr = new PrintStream(teeErr, true, StandardCharsets.UTF_8);

        System.setOut(printStreamOut);
        System.setErr(printStreamErr);
    }

    public void startInfoUpdater() {
        uiWindow.updateInfo();
    }

    public static UiManager getInstance() {
        if (instance == null) {
            instance = new UiManager();
        }
        return instance;
    }
}

class UiWindow extends JFrame {

    private static JFrame frame = new JFrame("Discord Bot v" + DiscordBot.version);

    private static JPanel logPanel = new JPanel();
    static JTextPane logTextPane = new JTextPane();

    private static JPanel infoPanel = new JPanel();
    private static JLabel uptimeLabel = new JLabel("N/A");
    private static JLabel currentTrackLabel = new JLabel("N/A");
    private static JLabel trackTimestampLabel = new JLabel("N/A");
    private static JLabel playlistInfoLabel = new JLabel("<html>Length: N/A<br>Tracks: N/A</html>");

    public UiWindow() {
        frame.setSize(1200, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.setLocationRelativeTo(null);

        generateLogPanel();
        generateInfoPanel();

        frame.add(logPanel, BorderLayout.CENTER);
        frame.add(infoPanel, BorderLayout.EAST);
        frame.setVisible(true);
    }

    private void generateLogPanel() {
        logPanel.setBackground(Color.DARK_GRAY);
        logPanel.setLayout(new BorderLayout());

        JScrollPane scrollPane = new JScrollPane(logTextPane);
        logPanel.add(scrollPane, BorderLayout.CENTER);

        logTextPane.setEditable(false);
        logTextPane.setBackground(Color.BLACK);
        logTextPane.setForeground(Color.WHITE);
        logTextPane.setFont(new Font("Monospaced", Font.PLAIN, 12));
    }

    private void generateInfoPanel() {
        infoPanel.setBackground(new Color(45, 45, 48));
        infoPanel.setPreferredSize(new Dimension(250, 600));
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel titleLabel = new JLabel("Bot Information");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(new Color(100, 200, 255));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(titleLabel);
        infoPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        infoPanel.add(createSeparator());
        infoPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        infoPanel.add(createSectionLabel("\u25BA Uptime"));
        infoPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        uptimeLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        uptimeLabel.setForeground(new Color(100, 255, 100));
        uptimeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(uptimeLabel);
        infoPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        infoPanel.add(createSeparator());
        infoPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        infoPanel.add(createSectionLabel("\u25BA Current Track"));
        infoPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        currentTrackLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        currentTrackLabel.setForeground(new Color(100, 200, 255));
        currentTrackLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(currentTrackLabel);
        infoPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        infoPanel.add(createSeparator());
        infoPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        infoPanel.add(createSectionLabel("\u25BA Track Position"));
        infoPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        trackTimestampLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        trackTimestampLabel.setForeground(new Color(255, 200, 100));
        trackTimestampLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(trackTimestampLabel);
        infoPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        infoPanel.add(createSeparator());
        infoPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        infoPanel.add(createSectionLabel("\u25BA Playlist Info"));
        infoPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        playlistInfoLabel.setText("<html>Length: N/A<br>Tracks: N/A</html>");
        playlistInfoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        playlistInfoLabel.setForeground(new Color(200, 200, 200));
        playlistInfoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(playlistInfoLabel);

        infoPanel.add(Box.createVerticalGlue());
    }

    public void updateInfo() {
        Thread updateThread = new Thread(() -> {
            Timer timer = new Timer(750, e -> {
                uptimeLabel.setText(DiscordBot.getUptime());
                if (DiscordBot.musicBot != null && DiscordBot.musicBot.currentTrack != null) {
                    String formattedInfo = MusicBot.formatTrackInfo(DiscordBot.musicBot.currentTrack);
                    String[] parts = formattedInfo.split("\\]\\(<");
                    String title = parts[0].substring(1);
                    currentTrackLabel.setText(title);
                    String timestamp = formatTime(DiscordBot.musicBot.currentTrack.getPosition() / 1000)
                            + " / "
                            + formatTime(DiscordBot.musicBot.currentTrack.getDuration() / 1000);
                    trackTimestampLabel.setText(timestamp);
                } else {
                    currentTrackLabel.setText("No track playing");
                    trackTimestampLabel.setText("00:00 / 00:00");
                }
                if (!MusicBot.trackQueue.isEmpty()) {
                    String playlistInfo = "<html>Length: "
                            + (DiscordBot.musicBot != null ? DiscordBot.musicBot.totalQueueDuration() : "N/A")
                            + "<br>Tracks: "
                            + MusicBot.trackQueue.size()
                            + "</html>";
                    playlistInfoLabel.setText(playlistInfo);
                } else {
                    playlistInfoLabel.setText("<html>Length: 0<br>Tracks: 0</html>");
                }
            });
            timer.start();
        });
        updateThread.setDaemon(true);
        updateThread.start();
    }

    private JLabel createSectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 13));
        label.setForeground(new Color(150, 150, 150));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JSeparator createSeparator() {
        JSeparator separator = new JSeparator();
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        separator.setForeground(new Color(80, 80, 85));
        separator.setBackground(new Color(80, 80, 85));
        return separator;
    }
}

class TeeOutputStream extends OutputStream {
    private final OutputStream[] streams;

    public TeeOutputStream(OutputStream... streams) {
        this.streams = streams;
    }

    @Override
    public void write(int b) throws java.io.IOException {
        for (OutputStream stream : streams) {
            stream.write(b);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws java.io.IOException {
        for (OutputStream stream : streams) {
            stream.write(b, off, len);
        }
    }

    @Override
    public void flush() throws java.io.IOException {
        for (OutputStream stream : streams) {
            stream.flush();
        }
    }

    @Override
    public void close() throws java.io.IOException {
        for (OutputStream stream : streams) {
            stream.close();
        }
    }
}

class TextAreaOutputStream extends OutputStream {
    private final JTextPane textPane;
    private final java.io.ByteArrayOutputStream byteBuffer = new java.io.ByteArrayOutputStream();

    public TextAreaOutputStream(JTextPane textPane) {
        this.textPane = textPane;
    }

    @Override
    public void write(int b) {
        byteBuffer.write(b);

        if (b == '\n') {
            String line = byteBuffer.toString(StandardCharsets.UTF_8);
            appendColoredText(line);
            byteBuffer.reset();
        }
    }

    @Override
    public void write(byte[] b, int off, int len) {
        try {
            byteBuffer.write(b, off, len);

            String text = byteBuffer.toString(StandardCharsets.UTF_8);
            int lastNewline = text.lastIndexOf('\n');

            if (lastNewline != -1) {
                String[] lines = text.substring(0, lastNewline + 1).split("(?<=\n)");
                for (String line : lines) {
                    appendColoredText(line);
                }

                byteBuffer.reset();
                String remaining = text.substring(lastNewline + 1);
                if (!remaining.isEmpty()) {
                    byteBuffer.write(remaining.getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void flush() {
        if (byteBuffer.size() > 0) {
            String remaining = byteBuffer.toString(StandardCharsets.UTF_8);
            if (!remaining.isEmpty()) {
                appendColoredText(remaining);
            }
            byteBuffer.reset();
        }
    }

    private void appendColoredText(String text) {
        SwingUtilities.invokeLater(() -> {
            javax.swing.text.StyledDocument doc = textPane.getStyledDocument();
            javax.swing.text.SimpleAttributeSet attrs = new javax.swing.text.SimpleAttributeSet();

            Color color = Color.WHITE;
            String lowerText = text.toLowerCase();
            String trimmedText = text.trim();

            if (lowerText.contains("error") || lowerText.contains("exception") ||
                lowerText.contains("fatal") || lowerText.contains("severe") ||
                trimmedText.startsWith("at ") || trimmedText.startsWith("Caused by:")) {
                color = Color.RED;
            } else if (lowerText.contains("warn") || lowerText.contains("warning")) {
                color = Color.YELLOW;
            }

            javax.swing.text.StyleConstants.setForeground(attrs, color);

            try {
                doc.insertString(doc.getLength(), text, attrs);
                textPane.setCaretPosition(doc.getLength());
            } catch (javax.swing.text.BadLocationException e) {
                e.printStackTrace();
            }
        });
    }
}
