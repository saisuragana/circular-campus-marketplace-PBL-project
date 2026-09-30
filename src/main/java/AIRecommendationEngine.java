import java.util.*;
import java.util.regex.Pattern;

/**
 * AI Recommendation Engine
 *
 * Provides:
 * 1. Similar product detection
 * 2. Category matching
 * 3. Keyword similarity
 * 4. Description similarity
 * 5. Price similarity
 * 6. Condition similarity
 * 7. Related-product recommendations
 *
 * This engine works locally and does not require an external AI API.
 */
public class AIRecommendationEngine {

    private AIRecommendationEngine() {
    }

    /* -------------------------------------------------------------
     * STOP WORDS
     * ------------------------------------------------------------- */

    private static final Set<String> STOP_WORDS =
            new HashSet<String>(Arrays.asList(
                    "a", "an", "the", "and", "or", "of",
                    "for", "to", "in", "on", "with",
                    "is", "are", "this", "that",
                    "item", "product", "good", "new",
                    "used", "very", "best"
            ));

    /* -------------------------------------------------------------
     * SYNONYMS
     * ------------------------------------------------------------- */

    private static final Map<String, Set<String>> SYNONYMS =
            new HashMap<String, Set<String>>();

    static {

        addSynonyms(
                "laptop",
                "computer",
                "notebook",
                "pc"
        );

        addSynonyms(
                "phone",
                "mobile",
                "smartphone"
        );

        addSynonyms(
                "cycle",
                "bicycle",
                "bike"
        );

        addSynonyms(
                "headphones",
                "headset",
                "earphones",
                "earbuds",
                "audio"
        );

        addSynonyms(
                "book",
                "textbook",
                "study",
                "academic"
        );

        addSynonyms(
                "calculator",
                "scientific",
                "maths",
                "mathematics"
        );

        addSynonyms(
                "bag",
                "backpack",
                "college",
                "school"
        );

        addSynonyms(
                "coding",
                "programming",
                "development",
                "developer"
        );

        addSynonyms(
                "gaming",
                "game",
                "games"
        );

        addSynonyms(
                "electronics",
                "electronic",
                "device",
                "gadgets"
        );

        addSynonyms(
                "sports",
                "fitness",
                "exercise"
        );
    }

    private static void addSynonyms(
            String mainWord,
            String... relatedWords) {

        Set<String> group =
                new HashSet<String>();

        group.add(mainWord);

        for (String word : relatedWords) {
            group.add(normalize(word));
        }

        for (String word : group) {
            SYNONYMS.put(word, group);
        }
    }

    /* -------------------------------------------------------------
     * RECOMMENDATION DATA
     * ------------------------------------------------------------- */

    public static class Product {

        private final int id;
        private final String title;
        private final String category;
        private final String keywords;
        private final String description;
        private final String condition;
        private final double price;

        public Product(
                int id,
                String title,
                String category,
                String keywords,
                String description,
                String condition,
                double price) {

            this.id = id;
            this.title = title;
            this.category = category;
            this.keywords = keywords;
            this.description = description;
            this.condition = condition;
            this.price = price;
        }

        public int getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getCategory() {
            return category;
        }

        public String getKeywords() {
            return keywords;
        }

        public String getDescription() {
            return description;
        }

        public String getCondition() {
            return condition;
        }

        public double getPrice() {
            return price;
        }
    }

    /* -------------------------------------------------------------
     * SCORE RESULT
     * ------------------------------------------------------------- */

    public static class Recommendation {

        private final Product product;
        private final double score;
        private final String reason;

        public Recommendation(
                Product product,
                double score,
                String reason) {

            this.product = product;
            this.score = score;
            this.reason = reason;
        }

        public Product getProduct() {
            return product;
        }

        public double getScore() {
            return score;
        }

        public String getReason() {
            return reason;
        }
    }

    /* -------------------------------------------------------------
     * CALCULATE SIMILARITY
     * ------------------------------------------------------------- */

    public static Recommendation calculateRecommendation(
            Product source,
            Product candidate) {

        if (source == null || candidate == null) {
            return new Recommendation(
                    candidate,
                    0,
                    "No recommendation data"
            );
        }

        double score = 0;

        List<String> reasons =
                new ArrayList<String>();

        /* ---------------------------------------------------------
         * CATEGORY MATCH
         * --------------------------------------------------------- */

        if (same(
                source.getCategory(),
                candidate.getCategory())) {

            score += 35;

            reasons.add(
                    "same category"
            );
        }

        /* ---------------------------------------------------------
         * TITLE SIMILARITY
         * --------------------------------------------------------- */

        double titleScore =
                textSimilarity(
                        source.getTitle(),
                        candidate.getTitle()
                );

        score += titleScore * 20;

        if (titleScore >= 0.35) {
            reasons.add(
                    "similar product name"
            );
        }

        /* ---------------------------------------------------------
         * KEYWORD SIMILARITY
         * --------------------------------------------------------- */

        double keywordScore =
                textSimilarity(
                        source.getKeywords(),
                        candidate.getKeywords()
                );

        score += keywordScore * 25;

        if (keywordScore >= 0.25) {
            reasons.add(
                    "matching keywords"
            );
        }

        /* ---------------------------------------------------------
         * DESCRIPTION SIMILARITY
         * --------------------------------------------------------- */

        double descriptionScore =
                textSimilarity(
                        source.getDescription(),
                        candidate.getDescription()
                );

        score += descriptionScore * 10;

        if (descriptionScore >= 0.20) {
            reasons.add(
                    "similar description"
            );
        }

        /* ---------------------------------------------------------
         * PRICE SIMILARITY
         * --------------------------------------------------------- */

        double priceScore =
                calculatePriceSimilarity(
                        source.getPrice(),
                        candidate.getPrice()
                );

        score += priceScore * 15;

        if (priceScore >= 0.70) {
            reasons.add(
                    "similar price"
            );
        }

        /* ---------------------------------------------------------
         * CONDITION MATCH
         * --------------------------------------------------------- */

        if (same(
                source.getCondition(),
                candidate.getCondition())) {

            score += 5;

            reasons.add(
                    "same condition"
            );
        }

        /* ---------------------------------------------------------
         * CAP SCORE
         * --------------------------------------------------------- */

        if (score > 100) {
            score = 100;
        }

        String reason;

        if (reasons.isEmpty()) {

            reason =
                    "Related marketplace item";

        } else {

            reason =
                    "Recommended because of " +
                    joinReasons(reasons);
        }

        return new Recommendation(
                candidate,
                score,
                reason
        );
    }

    /* -------------------------------------------------------------
     * GET TOP RECOMMENDATIONS
     * ------------------------------------------------------------- */

    public static List<Recommendation> recommend(
            Product source,
            List<Product> candidates,
            int limit) {

        List<Recommendation> results =
                new ArrayList<Recommendation>();

        if (source == null ||
                candidates == null ||
                candidates.isEmpty()) {

            return results;
        }

        for (Product candidate : candidates) {

            if (candidate == null) {
                continue;
            }

            // Never recommend the product itself.
            if (candidate.getId() ==
                    source.getId()) {

                continue;
            }

            Recommendation recommendation =
                    calculateRecommendation(
                            source,
                            candidate
                    );

            if (recommendation.getScore() > 10) {

                results.add(
                        recommendation
                );
            }
        }

        /* ---------------------------------------------------------
         * HIGHEST SCORE FIRST
         * --------------------------------------------------------- */

        Collections.sort(
                results,
                new Comparator<Recommendation>() {

                    @Override
                    public int compare(
                            Recommendation a,
                            Recommendation b) {

                        return Double.compare(
                                b.getScore(),
                                a.getScore()
                        );
                    }
                }
        );

        /* ---------------------------------------------------------
         * LIMIT RESULTS
         * --------------------------------------------------------- */

        if (limit > 0 &&
                results.size() > limit) {

            return new ArrayList<Recommendation>(
                    results.subList(0, limit)
            );
        }

        return results;
    }

    /* -------------------------------------------------------------
     * TEXT SIMILARITY
     * ------------------------------------------------------------- */

    private static double textSimilarity(
            String text1,
            String text2) {

        Set<String> words1 =
                tokenize(text1);

        Set<String> words2 =
                tokenize(text2);

        if (words1.isEmpty() ||
                words2.isEmpty()) {

            return 0;
        }

        Set<String> intersection =
                new HashSet<String>(words1);

        intersection.retainAll(words2);

        if (intersection.isEmpty()) {

            /*
             * Try synonym matching.
             */
            int synonymMatches = 0;

            for (String word1 : words1) {

                Set<String> related =
                        SYNONYMS.get(word1);

                if (related == null) {
                    continue;
                }

                for (String word2 : words2) {

                    if (related.contains(word2)) {

                        synonymMatches++;
                        break;
                    }
                }
            }

            if (synonymMatches == 0) {
                return 0;
            }

            return Math.min(
                    1.0,
                    (double) synonymMatches /
                            Math.max(
                                    words1.size(),
                                    words2.size()
                            )
            );
        }

        /*
         * Jaccard-style similarity.
         */
        Set<String> union =
                new HashSet<String>(words1);

        union.addAll(words2);

        return (double) intersection.size()
                / union.size();
    }

    /* -------------------------------------------------------------
     * PRICE SIMILARITY
     * ------------------------------------------------------------- */

    private static double calculatePriceSimilarity(
            double price1,
            double price2) {

        if (price1 <= 0 ||
                price2 <= 0) {

            return 0;
        }

        double difference =
                Math.abs(price1 - price2);

        double average =
                (price1 + price2) / 2.0;

        if (average == 0) {
            return 0;
        }

        double ratio =
                difference / average;

        /*
         * Same price = 1.0
         *
         * Very different prices approach 0.
         */
        return Math.max(
                0,
                1.0 - ratio
        );
    }

    /* -------------------------------------------------------------
     * TOKENIZATION
     * ------------------------------------------------------------- */

    private static Set<String> tokenize(
            String text) {

        Set<String> result =
                new HashSet<String>();

        if (text == null ||
                text.trim().isEmpty()) {

            return result;
        }

        String normalized =
                normalize(text);

        String[] words =
                normalized.split("\\s+");

        for (String word : words) {

            if (word.isEmpty()) {
                continue;
            }

            if (STOP_WORDS.contains(word)) {
                continue;
            }

            result.add(word);

            /*
             * Add synonyms as related concepts.
             */
            Set<String> related =
                    SYNONYMS.get(word);

            if (related != null) {
                result.addAll(related);
            }
        }

        return result;
    }

    /* -------------------------------------------------------------
     * NORMALIZATION
     * ------------------------------------------------------------- */

    private static String normalize(
            String text) {

        if (text == null) {
            return "";
        }

        return text
                .toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9\\s-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /* -------------------------------------------------------------
     * SAME TEXT
     * ------------------------------------------------------------- */

    private static boolean same(
            String a,
            String b) {

        if (a == null ||
                b == null) {

            return false;
        }

        return a.trim()
                .equalsIgnoreCase(
                        b.trim()
                );
    }

    /* -------------------------------------------------------------
     * REASON BUILDER
     * ------------------------------------------------------------- */

    private static String joinReasons(
            List<String> reasons) {

        if (reasons == null ||
                reasons.isEmpty()) {

            return "related products";
        }

        if (reasons.size() == 1) {
            return reasons.get(0);
        }

        if (reasons.size() == 2) {
            return reasons.get(0) +
                    " and " +
                    reasons.get(1);
        }

        StringBuilder sb =
                new StringBuilder();

        for (int i = 0;
             i < reasons.size();
             i++) {

            if (i > 0) {

                if (i == reasons.size() - 1) {
                    sb.append(" and ");
                } else {
                    sb.append(", ");
                }
            }

            sb.append(
                    reasons.get(i)
            );
        }

        return sb.toString();
    }
}