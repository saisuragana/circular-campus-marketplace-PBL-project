
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Local AI-assisted listing quality and spam-risk checker.
 *
 * It does not call an external model. It evaluates listing completeness,
 * suspicious repetition, excessive promotional language and unreasonable
 * field values.
 */
public class AIQualityEngine {

    public static class QualityResult {
        private final int score;
        private final String status;
        private final List<String> reasons;

        public QualityResult(int score, String status, List<String> reasons) {
            this.score = score;
            this.status = status;
            this.reasons = reasons;
        }

        public int getScore() { return score; }
        public String getStatus() { return status; }
        public List<String> getReasons() { return reasons; }
    }

    private AIQualityEngine() {}

    public static QualityResult analyze(
            String title,
            String description,
            String keywords,
            String category,
            String condition,
            double price,
            int quantity) {

        int score = 100;
        List<String> reasons = new ArrayList<String>();

        String t = safe(title).toLowerCase(Locale.ENGLISH);
        String d = safe(description).toLowerCase(Locale.ENGLISH);
        String k = safe(keywords).toLowerCase(Locale.ENGLISH);

        if (t.length() < 8) {
            score -= 10;
            reasons.add("Title is very short");
        }

        if (d.length() < 30) {
            score -= 10;
            reasons.add("Description contains limited detail");
        }

        if (k.trim().isEmpty()) {
            score -= 8;
            reasons.add("No keywords provided");
        }

        if (category == null || category.trim().isEmpty()) {
            score -= 15;
            reasons.add("Category is missing");
        }

        if (condition == null || condition.trim().isEmpty()) {
            score -= 10;
            reasons.add("Condition is missing");
        }

        if (price <= 0) {
            score -= 25;
            reasons.add("Invalid price");
        }

        if (quantity <= 0 || quantity > 99) {
            score -= 10;
            reasons.add("Unusual quantity");
        }

        if (containsRepeatedCharacters(t)) {
            score -= 15;
            reasons.add("Title contains excessive repeated characters");
        }

        if (containsSpamWords(t + " " + d)) {
            score -= 18;
            reasons.add("Promotional or spam-like wording detected");
        }

        if (uppercaseRatio(title) > 0.65) {
            score -= 10;
            reasons.add("Title uses excessive capital letters");
        }

        if (d.equals(t) || (!d.isEmpty() && d.contains(t) && t.length() > 5)) {
            score -= 8;
            reasons.add("Description is too similar to the title");
        }

        if (score < 0) score = 0;

        String status;
        if (score >= 75) {
            status = "Validated";
        } else if (score >= 50) {
            status = "Review";
        } else {
            status = "Review";
        }

        if (reasons.isEmpty()) {
            reasons.add("Listing looks complete and consistent");
        }

        return new QualityResult(score, status, reasons);
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean containsRepeatedCharacters(String text) {
        if (text == null) return false;
        return text.matches(".*(.)\\1\\1\\1+.*");
    }

    private static boolean containsSpamWords(String text) {
        String[] spam = {
            "click here", "buy now!!!", "best price ever",
            "100% genuine", "limited offer", "act now",
            "free free", "urgent", "guaranteed"
        };

        for (String word : spam) {
            if (text.contains(word)) return true;
        }

        return false;
    }

    private static double uppercaseRatio(String text) {
        if (text == null || text.isEmpty()) return 0;

        int letters = 0;
        int upper = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) upper++;
            }
        }

        return letters == 0 ? 0 : (double) upper / letters;
    }
}
