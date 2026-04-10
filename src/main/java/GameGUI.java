import com.sun.speech.freetts.Voice;
import com.sun.speech.freetts.VoiceManager;

import javax.sound.midi.Instrument;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Synthesizer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class GameGUI extends JFrame {
    private final DatabaseManager databaseManager;
    private final Properties gameProperties;
    private final Random random = new Random();

    private final AnimatedBackgroundPanel rootPanel = new AnimatedBackgroundPanel();
    private final JLabel playerLabel = buildStatusLabel("Player: -");
    private final JLabel scoreLabel = buildStatusLabel("Score: ₹0");
    private final JLabel questionNumberLabel = buildStatusLabel("Question: 0/0");
    private final JLabel categoryLabel = buildStatusLabel("Category: -");
    private final JLabel timerLabel = buildStatusLabel("Time: 30s");
    private final JLabel statusMessageLabel = buildStatusLabel("Welcome to the KBC arena!");
    private final JProgressBar timerProgressBar = new JProgressBar();
    private final JTextArea questionArea = new JTextArea();
    private final Map<String, JButton> optionButtons = new LinkedHashMap<>();
    private final Map<String, JButton> lifelineButtons = new LinkedHashMap<>();
    private final DefaultListModel<String> prizeListModel = new DefaultListModel<>();
    private final JList<String> prizeList = new JList<>(prizeListModel);

    private final AudioEngine audioEngine = new AudioEngine();
    private final TextToSpeechEngine ttsEngine = new TextToSpeechEngine();

    private List<Question> sessionQuestions = new ArrayList<>();
    private int[] prizeLadder;
    private int totalQuestionsPerSession;
    private int timePerQuestionSeconds;

    private int currentQuestionIndex = 0;
    private int currentScore = 0;
    private int playerId;
    private String playerName;
    private String sessionId;
    private int secondsLeft;
    private long questionStartTimestamp;
    private Timer countdownTimer;

    private boolean gameActive = false;
    private boolean fiftyFiftyUsed = false;
    private boolean askAudienceUsed = false;
    private boolean phoneFriendUsed = false;

    private final Set<String> lifelinesUsedForCurrentQuestion = new HashSet<>();

    public GameGUI(DatabaseManager databaseManager, Properties gameProperties) {
        this.databaseManager = databaseManager;
        this.gameProperties = gameProperties;
        loadGameConfiguration();
        configureLookAndFeel();
        buildWindow();
        bindWindowLifecycle();
    }

    private void loadGameConfiguration() {
        totalQuestionsPerSession = Integer.parseInt(gameProperties.getProperty("game.totalQuestionsPerSession", "15"));
        timePerQuestionSeconds = Integer.parseInt(gameProperties.getProperty("game.timePerQuestionSeconds", "30"));
        String[] ladderParts = gameProperties.getProperty(
                "game.prizeLadder",
                "1000,2000,3000,5000,10000,20000,40000,80000,160000,320000,640000,1250000,2500000,5000000,10000000"
        ).split(",");
        prizeLadder = Arrays.stream(ladderParts)
                .map(String::trim)
                .mapToInt(Integer::parseInt)
                .toArray();
    }

    private void configureLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Default Swing look and feel is acceptable if system look and feel is unavailable.
        }
    }

    private void buildWindow() {
        setTitle("Virtual KBC Game - Java + MySQL Edition");
        setSize(1400, 860);
        setMinimumSize(new Dimension(1200, 760));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        rootPanel.setLayout(new BorderLayout(18, 18));
        rootPanel.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        setContentPane(rootPanel);

        rootPanel.add(buildHeaderPanel(), BorderLayout.NORTH);
        rootPanel.add(buildCenterPanel(), BorderLayout.CENTER);
        rootPanel.add(buildFooterPanel(), BorderLayout.SOUTH);
    }

    private JPanel buildHeaderPanel() {
        JPanel panel = createCardPanel(new GridLayout(2, 3, 12, 12));
        panel.add(playerLabel);
        panel.add(scoreLabel);
        panel.add(questionNumberLabel);
        panel.add(categoryLabel);
        panel.add(timerLabel);
        panel.add(statusMessageLabel);
        return panel;
    }

    private JPanel buildCenterPanel() {
        JPanel container = new JPanel(new BorderLayout(18, 18));
        container.setOpaque(false);
        container.add(buildQuestionSection(), BorderLayout.CENTER);
        container.add(buildRightRail(), BorderLayout.EAST);
        return container;
    }

    private JPanel buildQuestionSection() {
        JPanel questionSection = createCardPanel(new BorderLayout(16, 16));
        questionSection.setPreferredSize(new Dimension(920, 560));

        questionArea.setEditable(false);
        questionArea.setOpaque(false);
        questionArea.setLineWrap(true);
        questionArea.setWrapStyleWord(true);
        questionArea.setForeground(Color.WHITE);
        questionArea.setFont(new Font("Segoe UI", Font.BOLD, 28));
        questionArea.setMargin(new java.awt.Insets(12, 12, 12, 12));
        questionArea.setText("Press Start Game to load questions from MySQL and begin your KBC session.");

        JScrollPane questionScroll = new JScrollPane(questionArea);
        questionScroll.setBorder(BorderFactory.createEmptyBorder());
        questionScroll.getViewport().setOpaque(false);
        questionScroll.setOpaque(false);

        timerProgressBar.setMinimum(0);
        timerProgressBar.setMaximum(timePerQuestionSeconds);
        timerProgressBar.setValue(timePerQuestionSeconds);
        timerProgressBar.setStringPainted(true);
        timerProgressBar.setForeground(new Color(255, 205, 64));
        timerProgressBar.setBackground(new Color(30, 28, 60));
        timerProgressBar.setFont(new Font("Segoe UI", Font.BOLD, 14));

        JPanel optionsPanel = new JPanel(new GridLayout(2, 2, 14, 14));
        optionsPanel.setOpaque(false);
        for (String label : List.of("A", "B", "C", "D")) {
            JButton button = createOptionButton(label);
            optionButtons.put(label, button);
            optionsPanel.add(button);
        }

        questionSection.add(questionScroll, BorderLayout.CENTER);
        questionSection.add(timerProgressBar, BorderLayout.NORTH);
        questionSection.add(optionsPanel, BorderLayout.SOUTH);
        return questionSection;
    }

    private JPanel buildRightRail() {
        JPanel rail = new JPanel(new BorderLayout(16, 16));
        rail.setOpaque(false);
        rail.setPreferredSize(new Dimension(360, 560));

        JPanel lifelinesPanel = createCardPanel(new GridLayout(4, 1, 10, 10));
        lifelinesPanel.add(buildSectionTitle("Lifelines"));
        JButton fiftyButton = createLifelineButton("50/50", "Remove two wrong answers.");
        fiftyButton.addActionListener(event -> useFiftyFifty());
        lifelinesPanel.add(fiftyButton);
        lifelineButtons.put("50/50", fiftyButton);

        JButton phoneButton = createLifelineButton("Phone a Friend", "Get an expert style hint.");
        phoneButton.addActionListener(event -> usePhoneAFriend());
        lifelinesPanel.add(phoneButton);
        lifelineButtons.put("Phone a Friend", phoneButton);

        JButton audienceButton = createLifelineButton("Ask the Audience", "See simulated audience votes.");
        audienceButton.addActionListener(event -> useAskTheAudience());
        lifelinesPanel.add(audienceButton);
        lifelineButtons.put("Ask the Audience", audienceButton);

        JPanel ladderPanel = createCardPanel(new BorderLayout(10, 10));
        ladderPanel.add(buildSectionTitle("Prize Ladder"), BorderLayout.NORTH);
        prizeList.setBackground(new Color(8, 18, 54, 210));
        prizeList.setForeground(Color.WHITE);
        prizeList.setFont(new Font("Consolas", Font.BOLD, 16));
        prizeList.setFixedCellHeight(28);
        ladderPanel.add(new JScrollPane(prizeList), BorderLayout.CENTER);
        populatePrizeLadder();

        rail.add(lifelinesPanel, BorderLayout.NORTH);
        rail.add(ladderPanel, BorderLayout.CENTER);
        return rail;
    }

    private JPanel buildFooterPanel() {
        JPanel footer = createCardPanel(new BorderLayout(12, 12));
        JButton startButton = createPrimaryButton("Start / Restart Game");
        startButton.addActionListener(event -> showStartDialogAndLaunchGame());

        JLabel helpLabel = new JLabel("Answer 15 randomly selected questions. Lifelines can only be used once per game.");
        helpLabel.setForeground(new Color(214, 224, 255));
        helpLabel.setFont(new Font("Segoe UI", Font.PLAIN, 15));

        footer.add(startButton, BorderLayout.WEST);
        footer.add(helpLabel, BorderLayout.CENTER);
        return footer;
    }

    private JLabel buildSectionTitle(String title) {
        JLabel label = new JLabel(title, SwingConstants.CENTER);
        label.setForeground(new Color(255, 224, 130));
        label.setFont(new Font("Segoe UI", Font.BOLD, 20));
        return label;
    }

    private JLabel buildStatusLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Color.WHITE);
        label.setFont(new Font("Segoe UI", Font.BOLD, 18));
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 255, 255, 50), 1, true),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)
        ));
        return label;
    }

    private JPanel createCardPanel(java.awt.LayoutManager layoutManager) {
        JPanel panel = new JPanel(layoutManager) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, new Color(12, 27, 78, 235), getWidth(), getHeight(), new Color(36, 6, 82, 220)));
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 28, 28));
                g2.setColor(new Color(255, 255, 255, 40));
                g2.setStroke(new BasicStroke(1.4f));
                g2.draw(new RoundRectangle2D.Double(1, 1, getWidth() - 2, getHeight() - 2, 28, 28));
                g2.dispose();
                super.paintComponent(graphics);
            }
        };
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        return panel;
    }

    private JButton createPrimaryButton(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(79, 70, 229));
        button.setFont(new Font("Segoe UI", Font.BOLD, 18));
        button.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
        return button;
    }

    private JButton createOptionButton(String label) {
        JButton button = new JButton(label + ". ");
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setFocusPainted(false);
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(14, 30, 90));
        button.setFont(new Font("Segoe UI", Font.BOLD, 18));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(120, 170, 255, 120), 2, true),
                BorderFactory.createEmptyBorder(16, 18, 16, 18)
        ));
        button.addActionListener(event -> handleAnswerSelection(label));
        return button;
    }

    private JButton createLifelineButton(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setFocusPainted(false);
        button.setFont(new Font("Segoe UI", Font.BOLD, 16));
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(211, 47, 47));
        button.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        return button;
    }

    private void bindWindowLifecycle() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                stopTimer();
                audioEngine.shutdown();
                ttsEngine.shutdown();
            }
        });
    }

    public void showGame() {
        setVisible(true);
        SwingUtilities.invokeLater(this::showWelcomeDialog);
    }

    private void showWelcomeDialog() {
        StringBuilder message = new StringBuilder();
        message.append("Welcome to the Java Virtual KBC Game!\n\n")
                .append("Features included:\n")
                .append("• 15 random questions from MySQL\n")
                .append("• 30 second timer per question\n")
                .append("• 50/50, Phone a Friend, Ask the Audience\n")
                .append("• Text-to-Speech question narration\n")
                .append("• Dynamic MIDI background music\n")
                .append("• Stored user interactions and scores\n\n")
                .append("Click Start / Restart Game to begin.");
        JOptionPane.showMessageDialog(this, message.toString(), "KBC Game Ready", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showStartDialogAndLaunchGame() {
        if (!databaseManager.testConnection()) {
            JOptionPane.showMessageDialog(
                    this,
                    "The game could not connect to MySQL.\n\nPlease update src/main/resources/database.properties, import sql/kbc_game.sql, and try again.",
                    "Database Connection Failed",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        String name = JOptionPane.showInputDialog(this, "Enter player name:", playerName == null ? "Player1" : playerName);
        if (name == null || name.trim().isEmpty()) {
            return;
        }

        try {
            if (databaseManager.countQuestions() < totalQuestionsPerSession) {
                JOptionPane.showMessageDialog(this,
                        "The database does not contain enough questions. Please import the full SQL file.",
                        "Insufficient Data",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            playerName = name.trim();
            playerId = databaseManager.getOrCreateUser(playerName);
            sessionId = UUID.randomUUID().toString();
            sessionQuestions = databaseManager.fetchRandomQuestions(totalQuestionsPerSession);
            resetGameState();
            gameActive = true;
            audioEngine.playLoop(Theme.NEUTRAL);
            displayCurrentQuestion();
            statusMessageLabel.setText("New session loaded. Good luck, " + playerName + "!");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Unable to start the game because of a database error:\n" + ex.getMessage(),
                    "Startup Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void resetGameState() {
        currentQuestionIndex = 0;
        currentScore = 0;
        secondsLeft = timePerQuestionSeconds;
        fiftyFiftyUsed = false;
        askAudienceUsed = false;
        phoneFriendUsed = false;
        lifelinesUsedForCurrentQuestion.clear();
        enableAllOptions();
        restoreLifelineButtons();
        updateHeader();
        populatePrizeLadder();
    }

    private void restoreLifelineButtons() {
        lifelineButtons.values().forEach(button -> {
            button.setEnabled(true);
            button.setBackground(new Color(211, 47, 47));
        });
    }

    private void populatePrizeLadder() {
        prizeListModel.clear();
        for (int i = prizeLadder.length - 1; i >= 0; i--) {
            prizeListModel.addElement(String.format("Q%-2d  ₹%,d", i + 1, prizeLadder[i]));
        }
        highlightPrizeLevel();
    }

    private void highlightPrizeLevel() {
        int indexToHighlight = Math.max(0, prizeLadder.length - 1 - currentQuestionIndex);
        if (!prizeListModel.isEmpty() && indexToHighlight >= 0 && indexToHighlight < prizeListModel.size()) {
            prizeList.setSelectedIndex(indexToHighlight);
            prizeList.ensureIndexIsVisible(indexToHighlight);
        }
    }

    private void displayCurrentQuestion() {
        if (currentQuestionIndex >= sessionQuestions.size()) {
            finishGame(true, "Outstanding! You completed the full KBC ladder.");
            return;
        }

        Question question = sessionQuestions.get(currentQuestionIndex);
        questionArea.setText(question.getQuestionText());
        categoryLabel.setText("Category: " + question.getCategory());
        questionNumberLabel.setText("Question: " + (currentQuestionIndex + 1) + "/" + sessionQuestions.size());
        scoreLabel.setText("Score: ₹" + String.format("%,d", currentScore));
        timerLabel.setText("Time: " + timePerQuestionSeconds + "s");
        secondsLeft = timePerQuestionSeconds;
        timerProgressBar.setMaximum(timePerQuestionSeconds);
        timerProgressBar.setValue(timePerQuestionSeconds);
        timerProgressBar.setString(timePerQuestionSeconds + " seconds left");
        lifelinesUsedForCurrentQuestion.clear();
        enableAllOptions();
        resetOptionColors();
        renderOptions(question);
        updateHeader();
        highlightPrizeLevel();
        questionStartTimestamp = System.currentTimeMillis();
        startTimer();
        narrateQuestion(question);
    }

    private void renderOptions(Question question) {
        for (Option option : question.getOptions()) {
            JButton button = optionButtons.get(option.getOptionLabel());
            if (button != null) {
                button.setText(option.getOptionLabel() + ". " + option.getOptionText());
                button.setVisible(true);
                button.setEnabled(true);
            }
        }
    }

    private void updateHeader() {
        playerLabel.setText("Player: " + (playerName == null ? "-" : playerName));
        scoreLabel.setText("Score: ₹" + String.format("%,d", currentScore));
        questionNumberLabel.setText("Question: " + (gameActive ? currentQuestionIndex + 1 : 0) + "/" + totalQuestionsPerSession);
    }

    private void startTimer() {
        stopTimer();
        countdownTimer = new Timer(1000, event -> {
            secondsLeft--;
            timerLabel.setText("Time: " + secondsLeft + "s");
            timerProgressBar.setValue(Math.max(secondsLeft, 0));
            timerProgressBar.setString(Math.max(secondsLeft, 0) + " seconds left");
            if (secondsLeft <= 10) {
                timerProgressBar.setForeground(new Color(255, 82, 82));
            } else {
                timerProgressBar.setForeground(new Color(255, 205, 64));
            }
            if (secondsLeft <= 0) {
                stopTimer();
                statusMessageLabel.setText("Time up! The arena falls silent...");
                handleTimeout();
            }
        });
        countdownTimer.start();
    }

    private void stopTimer() {
        if (countdownTimer != null) {
            countdownTimer.stop();
            countdownTimer = null;
        }
    }

    private void narrateQuestion(Question question) {
        StringBuilder text = new StringBuilder();
        text.append("Question ").append(currentQuestionIndex + 1).append(". ")
                .append(question.getQuestionText()).append(". ");
        for (Option option : question.getOptions()) {
            text.append("Option ").append(option.getOptionLabel()).append(". ")
                    .append(option.getOptionText()).append(". ");
        }
        ttsEngine.speak(text.toString(), () -> statusMessageLabel.setText("Question narrated using built-in Java TTS."),
                () -> statusMessageLabel.setText("TTS unavailable on this machine. The game will continue normally."));
    }

    private void handleAnswerSelection(String label) {
        if (!gameActive || currentQuestionIndex >= sessionQuestions.size()) {
            return;
        }
        JButton chosenButton = optionButtons.get(label);
        if (chosenButton == null || !chosenButton.isEnabled()) {
            return;
        }

        stopTimer();
        disableAllOptions();
        Question question = sessionQuestions.get(currentQuestionIndex);
        Option selectedOption = question.getOptionByLabel(label);
        Option correctOption = question.getCorrectOption();
        boolean isCorrect = selectedOption != null && selectedOption.isCorrect();
        int timeTaken = Math.max(0, timePerQuestionSeconds - secondsLeft);

        if (isCorrect) {
            currentScore = prizeLadder[Math.min(currentQuestionIndex, prizeLadder.length - 1)];
            chosenButton.setBackground(new Color(46, 204, 113));
            statusMessageLabel.setText("Correct! Moving to the next level.");
            audioEngine.stopLoop();
            audioEngine.playStinger(Theme.CORRECT);
            saveInteraction(question, selectedOption, true, timeTaken);
            currentQuestionIndex++;
            highlightPrizeLevel();
            scheduleNextQuestionAfterDelay(true);
        } else {
            if (chosenButton != null) {
                chosenButton.setBackground(new Color(231, 76, 60));
            }
            highlightCorrectAnswer(correctOption);
            statusMessageLabel.setText("Wrong answer. Game over.");
            audioEngine.stopLoop();
            audioEngine.playStinger(Theme.WRONG);
            saveInteraction(question, selectedOption, false, timeTaken);
            scheduleFinish(false, "You answered incorrectly. The correct answer was " +
                    (correctOption == null ? "not found" : correctOption.getOptionLabel() + ": " + correctOption.getOptionText()) + ".");
        }
    }

    private void handleTimeout() {
        disableAllOptions();
        Question question = sessionQuestions.get(currentQuestionIndex);
        Option correctOption = question.getCorrectOption();
        highlightCorrectAnswer(correctOption);
        audioEngine.stopLoop();
        audioEngine.playStinger(Theme.WRONG);
        saveInteraction(question, null, false, timePerQuestionSeconds);
        scheduleFinish(false, "Time is up. The correct answer was " +
                (correctOption == null ? "not available" : correctOption.getOptionLabel() + ": " + correctOption.getOptionText()) + ".");
    }

    private void scheduleNextQuestionAfterDelay(boolean resumeNeutralMusic) {
        Timer delayTimer = new Timer(2400, event -> {
            ((Timer) event.getSource()).stop();
            if (resumeNeutralMusic) {
                audioEngine.playLoop(Theme.NEUTRAL);
            }
            displayCurrentQuestion();
        });
        delayTimer.setRepeats(false);
        delayTimer.start();
    }

    private void scheduleFinish(boolean won, String message) {
        Timer delayTimer = new Timer(2800, event -> {
            ((Timer) event.getSource()).stop();
            finishGame(won, message);
        });
        delayTimer.setRepeats(false);
        delayTimer.start();
    }

    private void finishGame(boolean won, String message) {
        gameActive = false;
        stopTimer();
        disableAllOptions();
        String finalMessage = message + "\n\nFinal score: ₹" + String.format("%,d", currentScore);
        statusMessageLabel.setText(won ? "Champion! Session completed." : "Session ended.");
        JOptionPane.showMessageDialog(this, finalMessage, won ? "Congratulations" : "Game Over",
                won ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
    }

    private void saveInteraction(Question question, Option selectedOption, boolean correct, int timeTaken) {
        if (playerId <= 0 || question == null) {
            return;
        }
        UserInteraction interaction = new UserInteraction(
                playerId,
                sessionId,
                question.getQuestionId(),
                selectedOption == null ? null : selectedOption.getOptionId(),
                currentScore,
                new HashSet<>(lifelinesUsedForCurrentQuestion),
                correct,
                currentQuestionIndex + 1,
                timeTaken,
                LocalDateTime.now()
        );

        try {
            databaseManager.saveInteraction(interaction);
        } catch (SQLException e) {
            statusMessageLabel.setText("Warning: interaction could not be saved to MySQL.");
        }
    }

    private void highlightCorrectAnswer(Option correctOption) {
        if (correctOption == null) {
            return;
        }
        JButton correctButton = optionButtons.get(correctOption.getOptionLabel());
        if (correctButton != null) {
            correctButton.setBackground(new Color(46, 204, 113));
        }
    }

    private void enableAllOptions() {
        optionButtons.values().forEach(button -> {
            button.setEnabled(true);
            button.setVisible(true);
        });
    }

    private void disableAllOptions() {
        optionButtons.values().forEach(button -> button.setEnabled(false));
    }

    private void resetOptionColors() {
        optionButtons.values().forEach(button -> button.setBackground(new Color(14, 30, 90)));
    }

    private void useFiftyFifty() {
        if (!gameActive || fiftyFiftyUsed) {
            return;
        }
        Question question = sessionQuestions.get(currentQuestionIndex);
        List<Option> wrongOptions = question.getOptions().stream().filter(option -> !option.isCorrect()).toList();
        List<Option> removable = new ArrayList<>(wrongOptions);
        java.util.Collections.shuffle(removable, random);
        for (int i = 0; i < Math.min(2, removable.size()); i++) {
            JButton button = optionButtons.get(removable.get(i).getOptionLabel());
            if (button != null) {
                button.setEnabled(false);
                button.setVisible(false);
            }
        }
        fiftyFiftyUsed = true;
        lifelinesUsedForCurrentQuestion.add("50/50");
        markLifelineUsed("50/50");
        statusMessageLabel.setText("50/50 used. Two wrong answers have been removed.");
    }

    private void usePhoneAFriend() {
        if (!gameActive || phoneFriendUsed) {
            return;
        }
        Question question = sessionQuestions.get(currentQuestionIndex);
        Option suggestion = chooseHintOption(question, 80);
        phoneFriendUsed = true;
        lifelinesUsedForCurrentQuestion.add("Phone a Friend");
        markLifelineUsed("Phone a Friend");

        JOptionPane.showMessageDialog(this,
                "Your friend says: \"I am leaning towards option " + suggestion.getOptionLabel() +
                        ". That seems the most likely answer to me.\"",
                "Phone a Friend",
                JOptionPane.INFORMATION_MESSAGE);
        statusMessageLabel.setText("Phone a Friend used.");
    }

    private void useAskTheAudience() {
        if (!gameActive || askAudienceUsed) {
            return;
        }
        Question question = sessionQuestions.get(currentQuestionIndex);
        Map<String, Integer> audienceVotes = buildAudienceVotes(question);
        askAudienceUsed = true;
        lifelinesUsedForCurrentQuestion.add("Ask the Audience");
        markLifelineUsed("Ask the Audience");

        StringBuilder message = new StringBuilder("Audience poll results:\n\n");
        audienceVotes.forEach((label, votes) -> message.append(label).append(": ").append(votes).append("%\n"));
        JOptionPane.showMessageDialog(this, message.toString(), "Ask the Audience", JOptionPane.INFORMATION_MESSAGE);
        statusMessageLabel.setText("Ask the Audience used.");
    }

    private Option chooseHintOption(Question question, int probabilityForCorrectAnswer) {
        int roll = random.nextInt(100);
        if (roll < probabilityForCorrectAnswer && question.getCorrectOption() != null) {
            return question.getCorrectOption();
        }
        List<Option> options = question.getOptions();
        return options.get(random.nextInt(options.size()));
    }

    private Map<String, Integer> buildAudienceVotes(Question question) {
        Map<String, Integer> votes = new LinkedHashMap<>();
        Option correctOption = question.getCorrectOption();
        int correctShare = 45 + random.nextInt(26);
        int remaining = 100 - correctShare;

        List<Option> wrongOptions = question.getOptions().stream().filter(option -> !option.isCorrect()).toList();
        int first = random.nextInt(remaining + 1);
        int second = random.nextInt(remaining - first + 1);
        int third = remaining - first - second;
        int[] split = {first, second, third};

        for (Option option : question.getOptions()) {
            if (correctOption != null && option.getOptionId() == correctOption.getOptionId()) {
                votes.put(option.getOptionLabel(), correctShare);
            }
        }
        for (int i = 0; i < wrongOptions.size(); i++) {
            votes.put(wrongOptions.get(i).getOptionLabel(), split[i]);
        }
        return votes;
    }

    private void markLifelineUsed(String key) {
        JButton button = lifelineButtons.get(key);
        if (button != null) {
            button.setEnabled(false);
            button.setBackground(new Color(85, 85, 85));
        }
    }

    private static class TextToSpeechEngine {
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private Voice voice;
        private volatile boolean available = true;

        public void speak(String text, Runnable successCallback, Runnable failureCallback) {
            executor.submit(() -> {
                try {
                    if (!available) {
                        if (failureCallback != null) {
                            SwingUtilities.invokeLater(failureCallback);
                        }
                        return;
                    }
                    ensureVoice();
                    if (voice != null) {
                        voice.speak(text);
                        if (successCallback != null) {
                            SwingUtilities.invokeLater(successCallback);
                        }
                    } else {
                        available = false;
                        if (failureCallback != null) {
                            SwingUtilities.invokeLater(failureCallback);
                        }
                    }
                } catch (Exception exception) {
                    available = false;
                    if (failureCallback != null) {
                        SwingUtilities.invokeLater(failureCallback);
                    }
                }
            });
        }

        private void ensureVoice() {
            if (voice != null) {
                return;
            }
            VoiceManager voiceManager = VoiceManager.getInstance();
            voice = voiceManager.getVoice("kevin16");
            if (voice != null) {
                voice.allocate();
            }
        }

        public void shutdown() {
            executor.shutdownNow();
            if (voice != null) {
                try {
                    voice.deallocate();
                } catch (Exception ignored) {
                    // Best effort cleanup.
                }
            }
        }
    }

    private enum Theme {
        NEUTRAL,
        CORRECT,
        WRONG
    }

    private static class AudioEngine {
        private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        private ScheduledFuture<?> loopFuture;
        private Synthesizer synthesizer;
        private MidiChannel channel;
        private volatile boolean available = true;

        public void playLoop(Theme theme) {
            stopLoop();
            if (!available || theme != Theme.NEUTRAL) {
                return;
            }
            loopFuture = executor.scheduleAtFixedRate(() -> playPattern(theme), 0, 4, TimeUnit.SECONDS);
        }

        public void playStinger(Theme theme) {
            executor.submit(() -> playPattern(theme));
        }

        public void stopLoop() {
            if (loopFuture != null) {
                loopFuture.cancel(true);
                loopFuture = null;
            }
        }

        private void ensureSynth() {
            if (synthesizer != null && synthesizer.isOpen() && channel != null) {
                return;
            }
            try {
                synthesizer = MidiSystem.getSynthesizer();
                synthesizer.open();
                Instrument[] instruments = synthesizer.getDefaultSoundbank() == null
                        ? new Instrument[0]
                        : synthesizer.getDefaultSoundbank().getInstruments();
                if (instruments.length > 0) {
                    synthesizer.loadInstrument(instruments[0]);
                }
                channel = synthesizer.getChannels()[0];
                channel.programChange(88);
            } catch (Exception e) {
                available = false;
            }
        }

        private void playPattern(Theme theme) {
            try {
                ensureSynth();
                if (!available || channel == null) {
                    return;
                }
                switch (theme) {
                    case NEUTRAL -> {
                        channel.programChange(88);
                        playNote(60, 500, 65);
                        playNote(64, 450, 60);
                        playNote(67, 600, 70);
                        playNote(72, 900, 55);
                    }
                    case CORRECT -> {
                        channel.programChange(52);
                        playNote(72, 220, 95);
                        playNote(76, 220, 100);
                        playNote(79, 350, 110);
                        playNote(84, 500, 120);
                    }
                    case WRONG -> {
                        channel.programChange(48);
                        playNote(62, 320, 85);
                        playNote(57, 400, 95);
                        playNote(53, 650, 105);
                    }
                }
            } catch (Exception ignored) {
                available = false;
            }
        }

        private void playNote(int note, int durationMs, int velocity) throws InterruptedException {
            channel.noteOn(note, velocity);
            Thread.sleep(durationMs);
            channel.noteOff(note);
        }

        public void shutdown() {
            stopLoop();
            executor.shutdownNow();
            if (synthesizer != null && synthesizer.isOpen()) {
                synthesizer.close();
            }
        }
    }

    private static class AnimatedBackgroundPanel extends JPanel {
        private final List<float[]> stars = new ArrayList<>();
        private float pulse = 0f;
        private boolean expanding = true;

        public AnimatedBackgroundPanel() {
            setOpaque(true);
            setBackground(new Color(4, 7, 24));
            Random random = new Random();
            for (int i = 0; i < 50; i++) {
                stars.add(new float[]{random.nextFloat(), random.nextFloat(), 2 + random.nextFloat() * 6});
            }
            Timer animationTimer = new Timer(60, event -> {
                pulse = expanding ? pulse + 0.02f : pulse - 0.02f;
                if (pulse >= 1f) {
                    expanding = false;
                }
                if (pulse <= 0f) {
                    expanding = true;
                }
                repaint();
            });
            animationTimer.start();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            GradientPaint background = new GradientPaint(0, 0, new Color(2, 8, 28), getWidth(), getHeight(), new Color(36, 10, 82));
            g2.setPaint(background);
            g2.fillRect(0, 0, getWidth(), getHeight());

            for (float[] star : stars) {
                int x = (int) (star[0] * getWidth());
                int y = (int) (star[1] * getHeight());
                int size = (int) star[2];
                int alpha = (int) (80 + pulse * 120);
                g2.setColor(new Color(255, 255, 255, alpha));
                g2.fillOval(x, y, size, size);
            }
            g2.dispose();
        }
    }
}
