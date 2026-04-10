import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Question {
    private final int questionId;
    private final String questionText;
    private final String category;
    private final List<Option> options = new ArrayList<>();

    public Question(int questionId, String questionText, String category) {
        this.questionId = questionId;
        this.questionText = questionText;
        this.category = category;
    }

    public int getQuestionId() {
        return questionId;
    }

    public String getQuestionText() {
        return questionText;
    }

    public String getCategory() {
        return category;
    }

    public void addOption(Option option) {
        options.add(option);
        options.sort(Comparator.comparing(Option::getOptionLabel));
    }

    public List<Option> getOptions() {
        return Collections.unmodifiableList(options);
    }

    public Option getCorrectOption() {
        return options.stream().filter(Option::isCorrect).findFirst().orElse(null);
    }

    public Option getOptionByLabel(String label) {
        return options.stream()
                .filter(option -> option.getOptionLabel().equalsIgnoreCase(label))
                .findFirst()
                .orElse(null);
    }
}
