import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class DatabaseManager {
    private final Properties databaseProperties = new Properties();

    public DatabaseManager() throws IOException {
        loadProperties();
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("MySQL JDBC driver not found. Build the project with Maven so dependencies are downloaded.", e);
        }
    }

    private void loadProperties() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("database.properties")) {
            if (inputStream == null) {
                throw new IOException("database.properties was not found in src/main/resources.");
            }
            databaseProperties.load(inputStream);
        }
    }

    public Properties getDatabaseProperties() {
        Properties clone = new Properties();
        clone.putAll(databaseProperties);
        return clone;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                databaseProperties.getProperty("db.url"),
                databaseProperties.getProperty("db.user"),
                databaseProperties.getProperty("db.password")
        );
    }

    public boolean testConnection() {
        try (Connection connection = getConnection()) {
            return connection != null && connection.isValid(3);
        } catch (SQLException e) {
            return false;
        }
    }

    public int getOrCreateUser(String playerName) throws SQLException {
        String selectSql = "SELECT user_id FROM users WHERE user_name = ?";
        String insertSql = "INSERT INTO users (user_name, created_at) VALUES (?, ?)";

        try (Connection connection = getConnection()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(selectSql)) {
                preparedStatement.setString(1, playerName.trim());
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getInt("user_id");
                    }
                }
            }

            try (PreparedStatement preparedStatement = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                preparedStatement.setString(1, playerName.trim());
                preparedStatement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
                preparedStatement.executeUpdate();
                try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    }
                }
            }
        }
        throw new SQLException("Unable to create a player record for " + playerName);
    }

    public List<Question> fetchRandomQuestions(int count) throws SQLException {
        String sql = """
                SELECT q.question_id,
                       q.question_text,
                       q.category,
                       o.option_id,
                       o.option_label,
                       o.option_text,
                       o.is_correct
                FROM (
                    SELECT question_id, question_text, category
                    FROM questions
                    ORDER BY RAND()
                    LIMIT ?
                ) q
                JOIN options o ON q.question_id = o.question_id
                ORDER BY q.question_id, o.option_label
                """;

        Map<Integer, Question> questionMap = new LinkedHashMap<>();
        try (Connection connection = getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, count);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    int questionId = resultSet.getInt("question_id");
                    Question question = questionMap.computeIfAbsent(questionId,
                            id -> new Question(id,
                                    safeGetString(resultSet, "question_text"),
                                    safeGetString(resultSet, "category")));

                    question.addOption(new Option(
                            resultSet.getInt("option_id"),
                            questionId,
                            safeGetString(resultSet, "option_label"),
                            safeGetString(resultSet, "option_text"),
                            resultSet.getBoolean("is_correct")
                    ));
                }
            }
        }
        return new ArrayList<>(questionMap.values());
    }

    public void saveInteraction(UserInteraction interaction) throws SQLException {
        String sql = """
                INSERT INTO user_interactions (
                    user_id, session_id, question_id, selected_option_id, score,
                    lifelines_used, is_correct, question_number, time_taken_seconds, answered_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            preparedStatement.setInt(1, interaction.getUserId());
            preparedStatement.setString(2, interaction.getSessionId());
            preparedStatement.setInt(3, interaction.getQuestionId());
            if (interaction.getSelectedOptionId() == null) {
                preparedStatement.setNull(4, java.sql.Types.INTEGER);
            } else {
                preparedStatement.setInt(4, interaction.getSelectedOptionId());
            }
            preparedStatement.setInt(5, interaction.getScore());
            preparedStatement.setString(6, interaction.getLifelinesUsedAsCsv());
            preparedStatement.setBoolean(7, interaction.isCorrect());
            preparedStatement.setInt(8, interaction.getQuestionNumber());
            preparedStatement.setInt(9, interaction.getTimeTakenSeconds());
            preparedStatement.setTimestamp(10, Timestamp.valueOf(interaction.getAnsweredAt()));
            preparedStatement.executeUpdate();

            try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    interaction.setInteractionId(generatedKeys.getInt(1));
                }
            }
        }
    }

    public int countQuestions() throws SQLException {
        String sql = "SELECT COUNT(*) AS total FROM questions";
        try (Connection connection = getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getInt("total");
            }
        }
        return 0;
    }

    private String safeGetString(ResultSet resultSet, String columnLabel) {
        try {
            String value = resultSet.getString(columnLabel);
            return value == null ? "" : value;
        } catch (SQLException e) {
            return "";
        }
    }
}
