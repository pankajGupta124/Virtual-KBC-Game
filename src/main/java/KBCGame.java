import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class KBCGame {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                Properties gameProperties = loadGameProperties();
                DatabaseManager databaseManager = new DatabaseManager();
                GameGUI gameGUI = new GameGUI(databaseManager, gameProperties);
                gameGUI.showGame();
            } catch (Exception exception) {
                exception.printStackTrace();
                JOptionPane.showMessageDialog(
                        null,
                        "The KBC game could not start.\n\nReason: " + exception.getMessage(),
                        "Startup Failure",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }

    private static Properties loadGameProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream inputStream = KBCGame.class.getClassLoader().getResourceAsStream("game.properties")) {
            if (inputStream == null) {
                throw new IOException("game.properties not found in src/main/resources.");
            }
            properties.load(inputStream);
        }
        return properties;
    }
}
