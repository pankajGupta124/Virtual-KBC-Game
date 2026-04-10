import java.time.LocalDateTime;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class UserInteraction {
    private int interactionId;
    private int userId;
    private String sessionId;
    private int questionId;
    private Integer selectedOptionId;
    private int score;
    private final Set<String> lifelinesUsed;
    private boolean correct;
    private int questionNumber;
    private int timeTakenSeconds;
    private LocalDateTime answeredAt;

    public UserInteraction(int userId, String sessionId, int questionId, Integer selectedOptionId, int score,
                           Set<String> lifelinesUsed, boolean correct, int questionNumber,
                           int timeTakenSeconds, LocalDateTime answeredAt) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.questionId = questionId;
        this.selectedOptionId = selectedOptionId;
        this.score = score;
        this.lifelinesUsed = new TreeSet<>(lifelinesUsed);
        this.correct = correct;
        this.questionNumber = questionNumber;
        this.timeTakenSeconds = timeTakenSeconds;
        this.answeredAt = answeredAt;
    }

    public int getInteractionId() {
        return interactionId;
    }

    public void setInteractionId(int interactionId) {
        this.interactionId = interactionId;
    }

    public int getUserId() {
        return userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public int getQuestionId() {
        return questionId;
    }

    public Integer getSelectedOptionId() {
        return selectedOptionId;
    }

    public int getScore() {
        return score;
    }

    public Set<String> getLifelinesUsed() {
        return new TreeSet<>(lifelinesUsed);
    }

    public String getLifelinesUsedAsCsv() {
        if (lifelinesUsed.isEmpty()) {
            return "none";
        }
        return lifelinesUsed.stream().sorted().collect(Collectors.joining(", "));
    }

    public boolean isCorrect() {
        return correct;
    }

    public int getQuestionNumber() {
        return questionNumber;
    }

    public int getTimeTakenSeconds() {
        return timeTakenSeconds;
    }

    public LocalDateTime getAnsweredAt() {
        return answeredAt;
    }
}
