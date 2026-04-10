public class Option {
    private final int optionId;
    private final int questionId;
    private final String optionLabel;
    private final String optionText;
    private final boolean correct;

    public Option(int optionId, int questionId, String optionLabel, String optionText, boolean correct) {
        this.optionId = optionId;
        this.questionId = questionId;
        this.optionLabel = optionLabel;
        this.optionText = optionText;
        this.correct = correct;
    }

    public int getOptionId() {
        return optionId;
    }

    public int getQuestionId() {
        return questionId;
    }

    public String getOptionLabel() {
        return optionLabel;
    }

    public String getOptionText() {
        return optionText;
    }

    public boolean isCorrect() {
        return correct;
    }

    @Override
    public String toString() {
        return optionLabel + ": " + optionText;
    }
}
